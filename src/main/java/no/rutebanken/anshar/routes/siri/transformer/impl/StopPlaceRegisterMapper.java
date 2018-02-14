package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StopPlaceRegisterMapper extends ValueAdapter {

    private Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMapper.class);

    private static HealthManager healthManager;

    private final List<String> prefixes;
    private final String datatype;

    private static final Set<String> unmappedAlreadyAdded = new HashSet<>();

    private final String datasetId;
    private final SubscriptionSetup.SubscriptionType type;

    public StopPlaceRegisterMapper(SubscriptionSetup.SubscriptionType type, String datasetId, Class clazz, List<String> prefixes) {
        this(type, datasetId, clazz, prefixes, "Quay");
    }

    public StopPlaceRegisterMapper(SubscriptionSetup.SubscriptionType type, String datasetId, Class clazz, List<String> prefixes, String datatype) {
        super(clazz);
        this.type = type;
        this.datasetId = datasetId;
        this.prefixes = prefixes;
        this.datatype = datatype;
    }


    public String apply(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        if (healthManager == null) {
            healthManager = ApplicationContextHolder.getContext().getBean(HealthManager.class);
        }

        String mappedValue = null;

        try {
            if (stopPlaceService != null) {
                if (prefixes != null && !prefixes.isEmpty()) {

                    for (String prefix : prefixes) {
                        mappedValue = stopPlaceService.get(createCompleteId(prefix, id, datatype));
                        if (mappedValue != null) {
                            return mappedValue;
                        }
                    }
                } else {
                    mappedValue = stopPlaceService.get(id);
                    if (mappedValue != null) {
                        return mappedValue;
                    }
                }
            }
        } finally {
            if (mappedValue != null && unmappedAlreadyAdded.contains(id)) {
                healthManager.removeUnmappedId(type, datasetId, id);
                unmappedAlreadyAdded.remove(id);
            }
        }

        if (unmappedAlreadyAdded.add(id)) {
            healthManager.addUnmappedId(type, datasetId, id);
        }
        return id;
    }

    private String createCompleteId(String prefix, String id, String datatype) {
        String nsrIdPrefix = new StringBuilder().append(prefix).append(":").append(datatype).append(":").toString();
        if (id.startsWith(nsrIdPrefix)) {
            return id;
        }
        return new StringBuilder().append(nsrIdPrefix).append(id).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StopPlaceRegisterMapper)) return false;

        StopPlaceRegisterMapper that = (StopPlaceRegisterMapper) o;

        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return prefixes.equals(that.prefixes);
    }
}
