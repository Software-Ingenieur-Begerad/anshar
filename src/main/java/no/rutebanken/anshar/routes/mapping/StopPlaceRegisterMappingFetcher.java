/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

@Service
public class StopPlaceRegisterMappingFetcher {

    private static final Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMappingFetcher.class);

    @Autowired
    BlobStoreService blobStoreService;

    public Map<String, String> fetchStopPlaceMapping(String name) {

        Map<String, String> stopPlaceMappings = new HashMap<>();
        if (name != null && !name.isEmpty()) {

            long t1 = System.currentTimeMillis();

            Counter duplicates = new CounterImpl(0);

            final InputStream blob = blobStoreService.getBlob(name);

            if (blob != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(blob));

                reader.lines().forEach(line -> {
                    StringTokenizer tokenizer = new StringTokenizer(line, ",");
                    String id = tokenizer.nextToken();
                    String generatedId = tokenizer.nextToken();

                    if (stopPlaceMappings.containsKey(id)) {
                        duplicates.increment();
                    }
                    stopPlaceMappings.put(id, generatedId);
                });

                long t2 = System.currentTimeMillis();

                logger.info("Fetched mapping data - {} mappings, found {} duplicates. [fetched:{}ms]", stopPlaceMappings.size(), duplicates.getValue(), (t2 - t1));
                return stopPlaceMappings;
            }
        }
        logger.error("Filename is null or empty. Not possible to fetch mapping-file from GCS: {}", name);
        return stopPlaceMappings;
    }
}
