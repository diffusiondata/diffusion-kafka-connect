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
package com.diffusiondata.connect.diffusion.util;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.sink.SinkRecord;

/**
 * Utility methods for constructing appropriate topic paths / keys from source
 * objects, utilising format patterns.
 * <p>
 * Patterns may be a simple string, or they may include one or more token
 * patterns. These tokens will be substituted with associated components of the
 * object that the pattern is evaluated against.
 * <p>
 * Unknown or unsupported tokens will be left as-is within the returned String.
 */
public class TopicPathMapping {
    /**
     * The version of a given SinkRecord's key schema.
     * <p>
     * Only applicable for Sink tasks.
     */
    public static final String KEY_VERSION = "${key.version}";

    /**
     * The version of a given SinkRecord's value schema.
     * <p>
     * Only applicable for Sink tasks.
     */
    public static final String VALUE_VERSION = "${value.version}";

    /**
     * Stringified form of a key from a given SinkRecord.
     * <p>
     * Only applicable for Sink tasks.
     */
    public static final String KEY_TOKEN = "${key}";

    /**
     * The topic that the associated source message was received on.
     * <p>
     * Applicable for Sink and Source tasks.
     */
    public static final String TOPIC_TOKEN = "${topic}";

    /**
     * Derive a path by applying a {@link SinkRecord} against a given pattern.
     *
     * @param pattern The pattern to compile against the given SinkRecord
     * @param record  The SinkRecord to apply to the given pattern
     * @return A path
     */
    public static String pathFromRecord(String pattern, SinkRecord record) {
        String topic = pattern.replace(TOPIC_TOKEN, record.topic());

        if (record.key() == null) {
            return topic;
        }

        final Schema keySchema = record.keySchema();

        if (keySchema != null) {
            if (keySchema.version() != null) {
                topic =
                    topic.replace(KEY_VERSION, keySchema.version().toString());
            }
        }

        if (record.key() != null) {
            topic = topic.replace(KEY_TOKEN, record.key().toString());
        }

        final Schema valSchema = record.valueSchema();

        if (valSchema != null) {
            if (valSchema.version() != null) {
                topic =
                    topic.replace(VALUE_VERSION,
                        valSchema.version().toString());
            }
        }

        return topic;
    }

    /**
     * Derive a path by applying a topic path against a given pattern.
     *
     * @param pattern   The pattern to compile against the given Topic Path
     * @param topicPath The topic path to apply to the given pattern
     * @return A path
     */
    public static String pathFromTopic(String pattern, String topicPath) {
        return pattern
            .replace(TOPIC_TOKEN, topicPath).replace("/", "_");
    }
}
