package no.rutebanken.anshar.routes.mqtt;

import javafx.util.Pair;
import no.rutebanken.anshar.routes.Constants;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;
import uk.org.siri.siri20.VehicleActivityStructure.MonitoredVehicleJourney;

import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Configuration
@Component
public class SiriVmMqttRoute extends RouteBuilder implements CamelContextAware {
    private Logger logger = LoggerFactory.getLogger(SiriVmMqttRoute.class);

    private static final String URI = "direct:" + Constants.MQTT_ROUTE_ID;
    private static final String TOPIC_PREFIX = "/hfp/journey/";

    private static final String ZERO = "0";
    private static final String SLASH = "/";
    private static final String JOURNEY_DELIM = " -> ";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String ODAY_FORMAT = "hhmm";

    private static final String RUTEBANKEN = "rutebanken";

    @Value("${anshar.mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${anshar.mqtt.subscribe:false}")
    private boolean mqttSubscribe;

    @Value("${anshar.mqtt.host}")
    private String host;

    @Value("${anshar.mqtt.username}")
    private String username;

    @Value("${anshar.mqtt.password}")
    private String password;

    @Autowired
    private CamelContext camelContext;

    @Override
    public void configure() throws Exception {

        if (mqttEnabled) {
            from(URI)
                .to("log:mqtt:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("mqtt:realtime?host=" + host + "&userName=" + username + "&password=" + password + "&byDefaultRetain=true");
        }

        if (mqttSubscribe) {
            from("mqtt:realtime?host=" + host + "&subscribeTopicName=#")
                .to("log:response:" + getClass().getSimpleName() + "?showAll=true&multiline=true");
        }
    }


    public void pushToMqttRoute(String datasetId, VehicleActivityStructure activity) {
        if (!mqttEnabled) {
            return;
        }

        try {
            Pair<String, String> message = getMessage(datasetId, activity);
            ProducerTemplate template = camelContext.createProducerTemplate();
            template.sendBodyAndHeader(URI, message.getValue(), "CamelMQTTPublishTopic", message.getKey());
        } catch (NullPointerException e) {
            logger.warn("Incomplete Siri data", e);
        } catch (Exception e) {
            logger.warn("Could not parse", e);
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Pair<String, String> getMessage(String datasetId, VehicleActivityStructure activity) {
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney == null) {
            throw new NullPointerException("VehicleActivityStructure.MonitoredVehicleJourney is null");
        }

        String vehicleId = getVehicleId(datasetId, monitoredVehicleJourney);
        if (vehicleId == null) {
            throw new NullPointerException("VehicleActivityStructure.MonitoredVehicleJourney.VehicleRef is null");
        }

        String mode = getMode(monitoredVehicleJourney);
        String route = getRoute(monitoredVehicleJourney);
        String direction = getDirection(monitoredVehicleJourney);
        String headSign = getHeadSign(monitoredVehicleJourney);
        String startTime = getStartTime(monitoredVehicleJourney);
        double lat = getLatitude(monitoredVehicleJourney);
        double lng = getLongitude(monitoredVehicleJourney);
        String timestamp = getTimestamp(activity);
        long tsi = getTsi(activity);

        String topic = getTopic(mode, vehicleId, route, direction, headSign, startTime);
        String message = getMessage(monitoredVehicleJourney, vehicleId, timestamp, tsi, route, direction, headSign, startTime, lat, lng);

        return new Pair<>(topic, message);
    }

    /**
     * Formats topic to string
     * - hfp/journey/<mode>/<vehicleId>/<route>/<direction>/<headsign>/<start_time>;
     */
    private String getTopic(String mode, String vehicleId, String route, String direction, String headSign,
                            String startTime) {
        return new StringBuilder(TOPIC_PREFIX)
                    .append(mode).append(SLASH)
                    .append(vehicleId).append(SLASH)
                    .append(route).append(SLASH)
                    .append(direction).append(SLASH)
                    .append(headSign).append(SLASH)
                    .append(startTime).append(SLASH)
                .toString();
    }

    private String getMessage(MonitoredVehicleJourney monitoredVehicleJourney, String vehicleId, String timeStamp,
                              long tsi, String route, String direction, String headSign, String startTime, double lat,
                              double lng) {
        JSONObject vehiclePosition = new JSONObject();
        vehiclePosition.put(VehiclePosition.DESIGNATION, getDesignation(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.DIRECTION, direction);
        vehiclePosition.put(VehiclePosition.OPERATOR, getOperator(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.VEHICLE_ID, vehicleId);
        vehiclePosition.put(VehiclePosition.TIMESTAMP, timeStamp);
        vehiclePosition.put(VehiclePosition.TSI, tsi);
        //vehiclePosition.put(VehiclePosition.HEADING, 9);
        //vehiclePosition.put(VehiclePosition.SPEED, 28);
        vehiclePosition.put(VehiclePosition.LATITUDE, lat);
        vehiclePosition.put(VehiclePosition.LONGITUDE, lng);
        vehiclePosition.put(VehiclePosition.DELAY, getDelay(monitoredVehicleJourney));
        //vehiclePosition.put(VehiclePosition.ODOMETER: odometer);
        vehiclePosition.put(VehiclePosition.ODAY, getDepartureDay(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.JOURNEY, getJourney(monitoredVehicleJourney, headSign));
        vehiclePosition.put(VehiclePosition.LINE, route);
        vehiclePosition.put(VehiclePosition.STARTTIME, startTime);
        vehiclePosition.put(VehiclePosition.STOP_INDEX, getStopIndex(monitoredVehicleJourney));
        vehiclePosition.put(VehiclePosition.SOURCE, RUTEBANKEN);

        return new JSONObject().put(VehiclePosition.ROOT, vehiclePosition).toString();
    }

    /*
     * MQTT helper methods
     */

    private String getMode(MonitoredVehicleJourney monitoredVehicleJourney) {
        if ("Sporvognsdrift".equals(getOperator(monitoredVehicleJourney))) {
            return "tram";
        }
        return "bus";
    }

    private String getVehicleId(String datasetId, MonitoredVehicleJourney monitoredVehicleJourney) {
        VehicleRef vehicleRef = monitoredVehicleJourney.getVehicleRef();
        if (vehicleRef != null && vehicleRef.getValue() != null) {
            return datasetId + vehicleRef.getValue();
        }
        return null;
    }

    private String getRoute(MonitoredVehicleJourney monitoredVehicleJourney) {
        LineRef lineRef = monitoredVehicleJourney.getLineRef();
        if (lineRef != null && lineRef.getValue() != null) {
            return OutboundIdAdapter.getMappedId(lineRef.getValue());
        }
        return VehiclePosition.UNKNOWN;
    }

    private String getDirection(MonitoredVehicleJourney monitoredVehicleJourney) {
        DirectionRefStructure directionRef = monitoredVehicleJourney.getDirectionRef();
        if (directionRef != null && directionRef.getValue() != null) {
            return directionRef.getValue();
        }
        return ZERO;
    }

    private String getHeadSign(MonitoredVehicleJourney monitoredVehicleJourney) {
        String departureName = VehiclePosition.UNKNOWN;
        List<NaturalLanguageStringStructure> destinationNames = monitoredVehicleJourney.getDestinationNames();
        if (destinationNames.size() > 0) {
            NaturalLanguageStringStructure destinationName = destinationNames.get(0);
            if (destinationName != null && destinationName.getValue() != null) {
                departureName = destinationName.getValue();
            }
        }
        return departureName;
    }

    private String getStartTime(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime originAimedDepartureTime = monitoredVehicleJourney.getOriginAimedDepartureTime();

        String date = VehiclePosition.UNKNOWN;
        if (originAimedDepartureTime != null) {
            try {
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern(ODAY_FORMAT));
            } catch (DateTimeException exception) {
                logger.warn("Could not format " + originAimedDepartureTime + " to " + ODAY_FORMAT, exception);
            }
        }
        return date;
    }

    private String getNextStop(MonitoredVehicleJourney monitoredVehicleJourney) {
        OnwardCallsStructure onwardCalls = monitoredVehicleJourney.getOnwardCalls();
        if (onwardCalls != null && onwardCalls.getOnwardCalls().size() > 0) {
            StopPointRef stopPointRef = onwardCalls.getOnwardCalls().get(0).getStopPointRef();
            if (stopPointRef != null && stopPointRef.getValue() != null) {
                return OutboundIdAdapter.getMappedId(stopPointRef.getValue());
            }
        }
        return VehiclePosition.UNKNOWN;
    }

    private double getLatitude(MonitoredVehicleJourney monitoredVehicleJourney) {
        LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
        if (vehicleLocation != null) {
            return vehicleLocation.getLatitude().doubleValue();
        }
        return 0.0;
    }

    private double getLongitude(MonitoredVehicleJourney monitoredVehicleJourney) {
        LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
        if (vehicleLocation != null) {
            return vehicleLocation.getLongitude().doubleValue();
        }
        return 0.0;
    }

    private String getGeoHash(double latitude, double longitude) {
        StringBuilder geohash = new StringBuilder();
        geohash.append((int)latitude);
        geohash.append(";");
        geohash.append((int)longitude);
        geohash.append(digits(latitude, longitude));
        return geohash.toString();
    }

    /*
     * MQTT Message helper methods
     */

    private String getDesignation(MonitoredVehicleJourney monitoredVehicleJourney) {
        List<NaturalLanguageStringStructure> publishedLineNames = monitoredVehicleJourney.getPublishedLineNames();
        if (publishedLineNames != null && publishedLineNames.size() > 0) {
            NaturalLanguageStringStructure publishedLine = publishedLineNames.get(0);
            if (publishedLine != null && publishedLine.getValue() != null) {
                return publishedLine.getValue();
            }
        }
        return VehiclePosition.UNKNOWN;
    }

    private String getOperator(MonitoredVehicleJourney monitoredVehicleJourney) {
        OperatorRefStructure operatorRef = monitoredVehicleJourney.getOperatorRef();
        if (operatorRef != null && operatorRef.getValue() != null) {
            return operatorRef.getValue();
        }
        return VehiclePosition.UNKNOWN;
    }

    private String getTimestamp(VehicleActivityStructure monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getRecordedAtTime();
        if (locationRecordedAtTime != null) {
            return DateTimeFormatter.ISO_INSTANT.format(locationRecordedAtTime);
        }
        return VehiclePosition.UNKNOWN;
    }

    private long getTsi(VehicleActivityStructure monitoredVehicleJourney) {
        ZonedDateTime locationRecordedAtTime = monitoredVehicleJourney.getRecordedAtTime();
        if (locationRecordedAtTime != null) {
            return locationRecordedAtTime.toEpochSecond();
        }
        return 0;
    }

    private int getDelay(MonitoredVehicleJourney monitoredVehicleJourney) {
        Duration delay = monitoredVehicleJourney.getDelay();
        if (delay != null) {
            return delay.getSeconds();
        }
        return 0;
    }

    private String getDepartureDay(MonitoredVehicleJourney monitoredVehicleJourney) {
        ZonedDateTime originAimedDepartureTime = monitoredVehicleJourney.getOriginAimedDepartureTime();

        String date = VehiclePosition.UNKNOWN;
        if (originAimedDepartureTime != null) {
            try {
                date = originAimedDepartureTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
            } catch (DateTimeException exception) {
                logger.warn("Could not format " + originAimedDepartureTime + " to " + DATE_FORMAT, exception);
            }
        }
        return date;
    }

    private String getJourney(MonitoredVehicleJourney monitoredVehicleJourney, String headSign) {
        String origin = VehiclePosition.UNKNOWN;

        List<NaturalLanguagePlaceNameStructure> originNames = monitoredVehicleJourney.getOriginNames();
        if (originNames != null && originNames.size() > 0) {
            NaturalLanguagePlaceNameStructure originName = originNames.get(0);
            if (originName != null && originName.getValue() != null) {
                origin = originName.getValue();
            }
        }
        return origin.concat(JOURNEY_DELIM).concat(headSign);
    }

    private long getStopIndex(MonitoredVehicleJourney monitoredVehicleJourney) {
        MonitoredCallStructure monitoredCall = monitoredVehicleJourney.getMonitoredCall();
        if (monitoredCall != null) {
            BigInteger visitNumber = monitoredCall.getVisitNumber();
            if (visitNumber != null) {
                return visitNumber.longValue();
            }
        }
        return 0;
    }

    /*
     * GeoHash Helper methods
     */

    private String digit(double x, int i) {
        return "" + (int) (Math.floor(x * Math.pow(10, i)) % 10);
    }

    private String digits(double latitude, double longitude) {
        int j;
        StringBuilder results = new StringBuilder(SLASH);
        for (int i = j = 1; j <= 3; i = ++j) {
            results.append(digit(latitude, i)).append(digit(longitude, i)).append(SLASH);
        }
        return results.toString();
    }
}
