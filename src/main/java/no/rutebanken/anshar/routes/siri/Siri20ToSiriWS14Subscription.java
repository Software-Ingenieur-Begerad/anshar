package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http4.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Siri20ToSiriWS14Subscription extends SiriSubscriptionRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private SiriHandler handler;

    public Siri20ToSiriWS14Subscription(SiriHandler handler, SubscriptionSetup subscriptionSetup) {
        this.handler = handler;
        this.subscriptionSetup = subscriptionSetup;

        this.customNamespacePrefixMapper = new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String arg0, String arg1, boolean arg2) {
                return "siri";
            }
        };
    }


    @Override
    public void configure() throws Exception {

        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();

        RouteHelper helper = new RouteHelper(subscriptionSetup, customNamespacePrefixMapper);

        //Start subscription
        from("direct:delayedStart" + subscriptionSetup.getSubscriptionId())
                .bean(helper, "marshalSiriSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant(RequestType.SUBSCRIBE))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("http4://" + urlMap.get(RequestType.SUBSCRIBE)+getTimeout())
                .choice().when(simple("${in.body} != null"))
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                    .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String body =p.getIn().getBody(String.class);
                    handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);

                })
        ;

        //Cancel subscription
        from("direct:delayedCancel" + subscriptionSetup.getSubscriptionId())
                .bean(helper, "marshalSiriTerminateSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .setHeader("SOAPAction", constant("DeleteSubscription")) // set SOAPAction Header (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to("http4://" + urlMap.get(RequestType.DELETE_SUBSCRIPTION)+getTimeout())
                .choice().when(simple("${in.body} != null"))
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                    .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String body = p.getIn().getBody(String.class);
                    logger.info("Response body [{}]", body);
                    if (body != null && !body.isEmpty()) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
        ;

        initTriggerRoutes();
    }

}
