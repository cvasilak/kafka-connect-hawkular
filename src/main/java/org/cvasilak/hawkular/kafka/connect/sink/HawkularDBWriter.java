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

import io.confluent.common.config.ConfigException;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.hawkular.client.core.ClientResponse;
import org.hawkular.client.core.HawkularClient;
import org.hawkular.client.core.jaxrs.Empty;
import org.hawkular.metrics.model.*;
import org.hawkular.metrics.model.param.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.apache.kafka.connect.data.Schema.Type.*;

public class HawkularDBWriter {

    private static Logger log = LoggerFactory.getLogger(HawkularDBWriter.class);

    private final HawkularClient client;
    private final HawkularSinkConnectorConfig config;

    private final int dataRetention;
    private final List<String> ifMetricsExistCache;

    HawkularDBWriter(HawkularSinkConnectorConfig config) {
        try {
            this.config = config;
            this.ifMetricsExistCache = new ArrayList<>();

            this.dataRetention = config.getInt(HawkularSinkConnectorConfig.HAWKULAR_RETENTION_PERIOD_CONFIG);

            String hawkEndpoint = config.getString(HawkularSinkConnectorConfig.HAWKULAR_ENDPOINT_CONFIG);
            String hawkUsername = config.getString(HawkularSinkConnectorConfig.HAWKULAR_USERNAME_CONFIG);
            String hawkPasswd = config.getPassword(HawkularSinkConnectorConfig.HAWKULAR_PASSWORD_CONFIG).value();
            String hawkTenantId = config.getString(HawkularSinkConnectorConfig.HAWKULAR_TENANT_ID_CONFIG);
            String hawkAdminToken = config.getString(HawkularSinkConnectorConfig.HAWKULAR_ADMIN_TOKEN_CONFIG);

            this.client = HawkularClient.builder(hawkTenantId)
                    .uri(new URI(hawkEndpoint))
                    .basicAuthentication(hawkUsername, hawkPasswd)
                    .adminTokenAuthentication(hawkAdminToken)
                    .build();

            // ping server
            if (!client.metrics().status()
                    .status().isSuccess())
                throw new ConnectException(String.format("there was a problem contacting Hawkular ['%s'], is your user/passwd correct?", hawkEndpoint));

        } catch (URISyntaxException | ConfigException e) {
            throw new ConnectException("couldn't start task due to configuration error:", e);
        }
    }

    public void write(Collection<SinkRecord> records) {
        final List<Metric<Double>> gauges = new ArrayList<>();
        final List<Metric<AvailabilityType>> availabilities = new ArrayList<>();
        final List<Metric<Long>> counters = new ArrayList<>();
        final List<Metric<String>> strings = new ArrayList<>();

        // iterate over records and setup metrics
        for (SinkRecord rec : records) {
            final Schema schema = rec.valueSchema();
            final Struct record = (Struct) rec.value();

            // timestamp
            final long timestamp = record.getInt64(config.getString(HawkularSinkConnectorConfig.METRIC_TIMESTAMP_FIELD_CONFIG));

            // tags
            final Map<String, String> tags = new HashMap<>();
            for (String configTag : config.getList(HawkularSinkConnectorConfig.METRIC_ID_TAGS_CONFIG)) {
                tags.put(configTag, record.getString(configTag));
            }

            // prefixes
            final StringBuilder prefixBuilder = new StringBuilder();
            for (String entry : config.getList(HawkularSinkConnectorConfig.METRIC_ID_PREFIX_CONFIG)) {
                prefixBuilder.append(record.get(entry))
                        .append(config.getString(HawkularSinkConnectorConfig.METRIC_ID_DELIMITER_CONFIG));
            }
            final String prefix = prefixBuilder.toString();

            // fields
            for (String mapping : config.getList(HawkularSinkConnectorConfig.METRIC_ID_FIELDS_CONFIG)) {
                final String[] parts = mapping.split(":");
                String name = parts[0];
                MetricType<?> hawkType = MetricType.fromTextCode(parts[1].toLowerCase());

                // does it exist?
                if (record.get(name) == null)
                    continue;

                // if its a container field the metric name should be set from the record field configured
                // that is if 'metric.id.container='path' we should look at the record for the field 'path'
                // and append this to the metric name.
                String containerField = config.getString(HawkularSinkConnectorConfig.METRIC_ID_CONTAINER_FIELD_CONFIG);
                final String id = prefix.concat(
                        containerField.isEmpty() ? name : record.getString(containerField));

                // extract kafka connect type from schema
                final Schema fieldSchema = schema.field(name).schema();

                // setup DataPoint according to hawkular type
                if (hawkType == MetricType.AVAILABILITY) {
                    AvailabilityType type = AvailabilityType.UNKNOWN;

                    if (fieldSchema.type() == BOOLEAN) {
                        type = record.getBoolean(name) ? AvailabilityType.UP : AvailabilityType.DOWN;
                    } else if (fieldSchema.type() == INT8) {
                        type = AvailabilityType.fromByte(record.getInt8(name));
                    }

                    DataPoint<AvailabilityType> point = new DataPoint<>(timestamp, type);
                    Metric<AvailabilityType> metric = new Metric<>(id, tags, dataRetention, MetricType.AVAILABILITY, Arrays.asList(point));

                    checkAndCreate(metric);
                    availabilities.add(metric);

                } else if (hawkType == MetricType.GAUGE) {
                    Double value;

                    if (fieldSchema.type() == FLOAT32)
                        value = record.getFloat32(name).doubleValue();
                    else if (fieldSchema.type() == FLOAT64)
                        value = record.getFloat64(name);
                    else
                        throw new IllegalStateException("field type set to GAUGE but received value is of type " + fieldSchema.type().name());

                    DataPoint<Double> point = new DataPoint<>(timestamp, value);
                    Metric<Double> metric = new Metric<>(id, tags, dataRetention, MetricType.GAUGE, Arrays.asList(point));

                    checkAndCreate(metric);
                    gauges.add(metric);

                } else if (hawkType == MetricType.COUNTER) {
                    Long value;

                    if (fieldSchema.type() == INT32)
                        value = record.getInt32(name).longValue();
                    else if (fieldSchema.type() == INT64)
                        value = record.getInt64(name);
                    else
                        throw new IllegalStateException("field type set to COUNTER but received value is of type " + fieldSchema.type().name());

                    DataPoint<Long> point = new DataPoint<>(timestamp, value);
                    Metric<Long> metric = new Metric<>(id, tags, dataRetention, MetricType.COUNTER, Arrays.asList(point));

                    checkAndCreate(metric);
                    counters.add(metric);

                } else if (hawkType == MetricType.STRING) {
                    String value = record.getString(name);

                    DataPoint<String> point = new DataPoint<>(timestamp, value);
                    Metric<String> metric = new Metric<>(id, tags, dataRetention, MetricType.STRING, Arrays.asList(point));

                    checkAndCreate(metric);
                    strings.add(metric);
                }
            }
        }

        // if all lists are empty means records received
        // do not contain any expected metric ids as
        // configured in the connector. Instead of throwing
        // an exception we notify and silently ignore.
        if (gauges.isEmpty() && availabilities.isEmpty() &&
                counters.isEmpty() && strings.isEmpty()) {
            log.warn("records received do not contain any usable metric id, check your connector configuration, ignoring..");
            return;
        }

        // setup request
        MixedMetricsRequest request = new MixedMetricsRequest(gauges,
                availabilities,
                counters,
                strings);

        // and issue it
        ClientResponse<Empty> response = client.metrics()
                .metric()
                .addMetricsData(request);

        if (!response.isSuccess()) {
            throw new IllegalStateException("inserting metrics to Hawkular failed, no success response received");
        }
    }

    // Metrics must be created otherwise TAGS for a metric
    // are ignored during addMetricsData()..
    // TODO: can this be avoided?
    private void checkAndCreate(Metric<?> metric) {
        String id = metric.getId();

        // if already in the cache, no need to check simply return
        if (ifMetricsExistCache.contains(id))
            return;

        // check if metric exists
        ClientResponse ifExistsResponse = client.metrics()
                .metric()
                .findMetrics(metric.getType(), new Tags(metric.getTags()), id);

        if (!ifExistsResponse.isSuccess()) {
            // create it
            log.info("Initial creation of metric with id '{}'  ", id);

            ClientResponse<Empty> createResponse = client.metrics()
                    .metric()
                    .createMetric(true, metric);

            if (!createResponse.isSuccess())
                throw new IllegalStateException(String.format("unable to create metric with id '{}'", id));
        }

        // add it to the cache
        ifMetricsExistCache.add(id);
    }
}