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
package com.diffusiondata.connect.diffusion.client.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusiondata.connect.diffusion.client.DiffusionClient;
import com.diffusiondata.connect.diffusion.client.DiffusionClientFactory;
import com.diffusiondata.connect.diffusion.config.DiffusionConfig;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.features.NoSuchTopicException;
import com.pushtechnology.diffusion.client.features.TopicUpdate;
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.Session.Listener;
import com.pushtechnology.diffusion.client.session.Session.State;
import com.pushtechnology.diffusion.client.session.SessionFactory;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;
import com.pushtechnology.diffusion.client.topics.details.TopicType;
import com.pushtechnology.diffusion.datatype.Bytes;
import com.pushtechnology.diffusion.datatype.json.JSON;
import com.pushtechnology.diffusion.topics.details.TopicSpecificationImpl;

/**
 * Implementation of {@link DiffusionClientFactory}.
 * <p>
 * Constructs {@link DiffusionClient} instances connected to provided server
 * details.
 */
public class DiffusionClientFactoryImpl implements DiffusionClientFactory {
    private static final Logger LOG =
        LoggerFactory.getLogger(DiffusionClientFactoryImpl.class);

    private static final SessionFactory factory = Diffusion.sessions();

    @Override
    public DiffusionClient connect(DiffusionConfig config, Listener listener) throws Exception {
        final Session session =
            factory
                .principal(config.username())
                .password(config.password())
                .listener(listener)
                .noReconnection()
                .open(config.diffusionUrl());

        return new DiffusionClientImpl(session);
    }

    private static class DiffusionClientImpl implements DiffusionClient {
        private final TopicUpdate topicUpdate;
        private final Topics topics;

        private final Session session;

        protected DiffusionClientImpl(Session session) {
            topicUpdate = session.feature(TopicUpdate.class);
            topics = session.feature(Topics.class);

            this.session = session;
        }

        @Override
        public void close() {
            session.close();
        }

        @Override
        public State getSessionState() {
            return session.getState();
        }

        @Override
        public CompletableFuture<?> subscribe(
            String topicSelector,
            SubscriptionStream stream) {

            topics.addStream(
                topicSelector,
                JSON.class,
                new Topics.ValueStream.Default<JSON>() {
                    @Override
                    public void onValue(String topicPath,
                        TopicSpecification specification, JSON oldValue,
                        JSON newValue) {
                        stream.onMessage(topicPath, specification, newValue);
                    }
                });

            return topics.subscribe(topicSelector);
        }

        @Override
        public <T extends Bytes> CompletableFuture<T> update(
            String topicPath, TopicType topicType, Class<T> clazz, T value) {

            final CompletableFuture<T> future = new CompletableFuture<>();

            topicUpdate
                .set(topicPath, clazz, value)
                .whenComplete(
                    (result, ex) -> {
                        if (ex == null) {
                            future.complete(value);
                            return;
                        }

                        final Throwable cause =
                            ex instanceof CompletionException ?
                                ex.getCause() : ex;

                        if (cause instanceof NoSuchTopicException) {
                            addAndSet(
                                topicPath,
                                topicType,
                                clazz,
                                value)
                                .whenComplete((topicCreationResult,
                                    throwable) -> {
                                    if (throwable != null) {
                                        future.completeExceptionally(throwable);
                                    }
                                    else {
                                        future.complete(value);
                                    }
                                });
                        }
                        else {
                            LOG.error(
                                "Failed to publish to Diffusion topic " +
                                    "path: {}.",
                                topicPath);
                            future.completeExceptionally(ex);
                        }
                    });
            return future;
        }

        private <T> CompletableFuture<?> addAndSet(
            String topicPath, TopicType topicType, Class<T> clazz, T value) {
            return
                topicUpdate
                    .addAndSet(
                        topicPath,
                        new TopicSpecificationImpl(topicType),
                        clazz,
                        value);
        }
    }
}