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

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;


public class HawkularSinkConnectorConfig extends AbstractConfig {

    public static final String METRIC_ID_CONTAINER_FIELD_CONFIG = "metric.id.container";
    public static final String METRIC_ID_FIELDS_CONFIG = "metric.id.fields";
    public static final String METRIC_ID_PREFIX_CONFIG = "metric.id.prefix";
    public static final String METRIC_ID_DELIMITER_CONFIG = "metric.id.delimiter";
    public static final String METRIC_ID_TAGS_CONFIG = "metric.id.tags";
    public static final String METRIC_TIMESTAMP_FIELD_CONFIG = "metric.timestamp.field";

    public static final String HAWKULAR_ENDPOINT_CONFIG = "hawkular.hostname";
    public static final String HAWKULAR_USERNAME_CONFIG = "hawkular.username";
    public static final String HAWKULAR_PASSWORD_CONFIG = "hawkular.password";
    public static final String HAWKULAR_TENANT_ID_CONFIG = "hawkular.tenant.id";
    public static final String HAWKULAR_RETENTION_PERIOD_CONFIG = "hawkular.retention.period";
    public static final String HAWKULAR_MAX_RETRIES = "hawkular.max.retries";
    public static final String HAWKULAR_RETRY_BACKOFF_MS = "hawkular.retry.backoff.ms";
    public static final String HAWKULAR_ADMIN_TOKEN_CONFIG = "hawkular.admin.token";

    protected static ConfigDef config() {
        final ConfigDef configDef = new ConfigDef();

        {
            final String group = "Connector";
            int order = 0;

            configDef
                    .define(METRIC_ID_CONTAINER_FIELD_CONFIG, ConfigDef.Type.STRING, "", ConfigDef.Importance.LOW,
                            "The container field that holds the metric id (e.g. metric:'path') to be used as the metric id (optional).", group, ++order, ConfigDef.Width.MEDIUM, "Metric Field ID")
                    .define(METRIC_ID_FIELDS_CONFIG, ConfigDef.Type.LIST, ConfigDef.Importance.HIGH,
                            "A list of fields that will be used as the values of the metric id (e.g. mem:GAUGE,requests:COUNTER,osVer:STRING,online:AVAILABILITY)", group, ++order, ConfigDef.Width.MEDIUM, "Metric Field IDs")
                    .define(METRIC_ID_PREFIX_CONFIG, ConfigDef.Type.LIST, ConfigDef.Importance.HIGH,
                            "A list of fields that will be used as this metric prefix.", group, ++order, ConfigDef.Width.MEDIUM, "Metric ID Prefix")
                    .define(METRIC_ID_DELIMITER_CONFIG, ConfigDef.Type.STRING, ".", ConfigDef.Importance.HIGH,
                            "The delimiter to be used when concatenating the metric fields (in case for more than one).", group, ++order, ConfigDef.Width.MEDIUM, "Metric Delimiter")
                    .define(METRIC_ID_TAGS_CONFIG, ConfigDef.Type.LIST, ConfigDef.Importance.HIGH,
                            "A list of fields that will be used as the tags of the metric.", group, ++order, ConfigDef.Width.MEDIUM, "Tag Fields")
                    .define(METRIC_TIMESTAMP_FIELD_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                            "The field to be used as the metric timestamp.", group, ++order, ConfigDef.Width.MEDIUM, "Timestamp Field");
        }

        {
            final String group = "Hawkular";
            int order = 0;

            configDef
                    .define(HAWKULAR_ENDPOINT_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                            "The hostname of the running hawkular instance that this connector will connect to.", group, ++order, ConfigDef.Width.LONG, "Hostname")
                    .define(HAWKULAR_USERNAME_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH,
                            "Username to authenticate to Hawkular.", group, ++order, ConfigDef.Width.LONG, "Username")
                    .define(HAWKULAR_PASSWORD_CONFIG, ConfigDef.Type.PASSWORD, ConfigDef.Importance.HIGH,
                            "Password to authenticate to Hawkular.", group, ++order, ConfigDef.Width.LONG, "Password")
                    .define(HAWKULAR_TENANT_ID_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.LOW,
                            "The Hawkular tenant id.", group, ++order, ConfigDef.Width.LONG, "Tenant-ID")
                    .define(HAWKULAR_RETENTION_PERIOD_CONFIG, ConfigDef.Type.INT, 7, ConfigDef.Importance.HIGH,
                            "The data retention period for each inserted metric.", group, ++order, ConfigDef.Width.MEDIUM, "Metric Data Retention")
                    .define(HAWKULAR_MAX_RETRIES, ConfigDef.Type.INT, 10, ConfigDef.Importance.MEDIUM,
                            "The maximum number of times to retry on errors before failing the task.", group, ++order, ConfigDef.Width.MEDIUM, "Maximum Retries")
                    .define(HAWKULAR_RETRY_BACKOFF_MS, ConfigDef.Type.LONG, 3000, ConfigDef.Importance.MEDIUM,
                            "The time in milliseconds to wait following an error before a retry attempt is made.", group, ++order, ConfigDef.Width.MEDIUM, "Retry Backoff (millis)")
                    .define(HAWKULAR_ADMIN_TOKEN_CONFIG, ConfigDef.Type.STRING, null, ConfigDef.Importance.LOW,
                            "The Hawkular admin token id.", group, ++order, ConfigDef.Width.LONG, "Tenant-ID");
        }

        return configDef;
    }

    public static final ConfigDef CONFIG = config();

    public HawkularSinkConnectorConfig(Map<String, String> originals) {
        super(CONFIG, originals);
    }
}
