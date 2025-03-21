/*******************************************************************************
 * Copyright (C) 2018, 2025 DiffusionData Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.diffusiondata.connect.diffusion.config;

import static org.apache.kafka.common.config.ConfigDef.Importance.HIGH;
import static org.apache.kafka.common.config.ConfigDef.Importance.LOW;
import static org.apache.kafka.common.config.ConfigDef.Type.INT;
import static org.apache.kafka.common.config.ConfigDef.Type.STRING;
import static org.apache.kafka.common.config.ConfigDef.Width.LONG;
import static org.apache.kafka.common.config.ConfigDef.Width.MEDIUM;
import static org.apache.kafka.common.config.ConfigDef.Width.SHORT;

import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Type;

public class DiffusionConfig extends AbstractConfig {
    public static final String VERSION = "0.0.1";

    public static final String DIFFUSION_DESTINATION = "diffusion.destination";
    public static final String DIFFUSION_SELECTOR = "diffusion.selector";
    public static final String KAFKA_TOPIC = "kafka.topic";

    public static final String USERNAME = "diffusion.username";
    public static final String PASSWORD = "diffusion.password";
    public static final String HOST = "diffusion.host";
    public static final String PORT = "diffusion.port";

    public static final int POLL_INTERVAL_DEFAULT = 1000;
    public static final String POLL_INTERVAL = "diffusion.poll.interval";

    public static final int POLL_BATCH_SIZE_DEFAULT = 128;
    public static final String POLL_BATCH_SIZE = "diffusion.poll.size";

    public static final String CONNECTION_GROUP = "Diffusion Connection " +
        "Details";
    public static final String SETTINGS_GROUP = "Diffusion Topic Settings";

    private static ConfigDef base() {
        return new ConfigDef()
            .define(
                HOST,
                STRING,
                HIGH,
                "The hostname of the Diffusion instance to connect to",
                CONNECTION_GROUP,
                0,
                LONG,
                "Diffusion Hostname")
            .define(
                PORT,
                INT,
                HIGH,
                "The port of the Diffusion instance to connect to",
                CONNECTION_GROUP,
                1,
                MEDIUM,
                "Diffusion Port")
            .define(
                USERNAME,
                STRING,
                HIGH,
                "The username to connect to Diffusion with",
                CONNECTION_GROUP,
                2,
                MEDIUM,
                "Username")
            .define(
                PASSWORD,
                Type.PASSWORD,
                HIGH,
                "The password to connect to Diffusion with",
                CONNECTION_GROUP,
                3,
                LONG,
                "Password");
    }

    public static final ConfigDef SOURCE_CONFIG = base()
        .define(
            DIFFUSION_SELECTOR,
            STRING,
            HIGH,
            "Diffusion source topic selector",
            SETTINGS_GROUP,
            0,
            LONG,
            "Diffusion topic selector")
        .define(
            KAFKA_TOPIC, STRING,
            HIGH,
            "The kafka topic pattern to publish data to",
            SETTINGS_GROUP,
            1,
            LONG,
            "Kafka topic pattern")
        .define(
            POLL_INTERVAL,
            INT,
            POLL_INTERVAL_DEFAULT,
            LOW,
            "Time in milliseconds to wait for polling messages",
            SETTINGS_GROUP,
            2,
            SHORT,
            "Poll interval")
        .define(
            POLL_BATCH_SIZE,
            INT,
            POLL_BATCH_SIZE_DEFAULT,
            LOW,
            "Number of messages to batch in each poll",
            SETTINGS_GROUP,
            3,
            SHORT,
            "Poll batch size");

    public static final ConfigDef SINK_CONFIG = base()
        .define(
            DIFFUSION_DESTINATION,
            STRING,
            HIGH,
            "Diffusion destination topic pattern",
            SETTINGS_GROUP,
            0,
            LONG,
            "Destination topic pattern");

    DiffusionConfig(ConfigDef definition, Map<String, String> props) {
        super(definition, props);
    }

    public String username() {
        return getString(USERNAME);
    }

    public String password() {
        return getPassword(PASSWORD).value();
    }

    public String host() {
        return getString(HOST);
    }

    public int port() {
        return getInt(PORT);
    }

    public static class SourceConfig extends DiffusionConfig {
        public SourceConfig(Map<String, String> props) {
            super(SOURCE_CONFIG, props);
        }

        public String diffusionTopic() {
            return getString(DIFFUSION_SELECTOR);
        }

        public String kafkaTopic() {
            return getString(KAFKA_TOPIC);
        }

        public int pollInterval() {
            return getInt(POLL_INTERVAL);
        }

        public int pollBatchSize() {
            return getInt(POLL_BATCH_SIZE);
        }
    }

    public static class SinkConfig extends DiffusionConfig {
        public SinkConfig(Map<String, String> props) {
            super(SINK_CONFIG, props);
        }

        public String diffusionDestination() {
            return getString(DIFFUSION_DESTINATION);
        }
    }
}
