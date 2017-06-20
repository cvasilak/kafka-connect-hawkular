/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cvasilak.hawkular.kafka.connect.sink;

import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.cvasilak.hawkular.kafka.connect.utils.ConnectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class HawkularSinkTask extends SinkTask {

    private static Logger log = LoggerFactory.getLogger(HawkularSinkTask.class);

    private HawkularDBWriter writer;

    private int remainingRetries, remainingRetriesReset;
    private long retryBackoffMs;

    @Override
    public String version() {
        return ConnectorUtils.getVersion();
    }

    @Override
    public void start(Map<String, String> settings) {
        log.info(ConnectorUtils.loadBanner("hawkular-sink-ascii.txt"));

        HawkularSinkConnectorConfig config = new HawkularSinkConnectorConfig(settings);

        remainingRetriesReset = config.getInt(HawkularSinkConnectorConfig.HAWKULAR_MAX_RETRIES);
        remainingRetries = remainingRetriesReset;

        retryBackoffMs = config.getLong(HawkularSinkConnectorConfig.HAWKULAR_RETRY_BACKOFF_MS);

        this.writer = new HawkularDBWriter(config);
    }

    @Override
    public void put(Collection<SinkRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        if (log.isDebugEnabled()) {
            final SinkRecord first = records.iterator().next();
            final int recordsCount = records.size();
            log.debug("[{}] received {} records with first record kafka coordinates:(topic:{},partition:{},offset:{}). Inserting to Hawkular..",
                    Thread.currentThread().getName(), recordsCount, first.topic(), first.kafkaPartition(), first.kafkaOffset());
        }

        try {
            this.writer.write(records);

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new ConnectException(e);
        } catch (Exception e) { // TODO: more fine-grained exception handling for retry mechanism
            log.warn("write of {} records failed, remainingRetries={}", records.size(), remainingRetries, e);
            if (remainingRetries == 0) {
                throw new ConnectException(e);
            } else {
                remainingRetries--;
                context.timeout(retryBackoffMs);

                throw new RetriableException(e);
            }
        }
        remainingRetries = remainingRetriesReset;
    }

    @Override
    public void stop() {
        log.info("Stopping HawkularSinkTask");
    }
}