/*******************************************************************************
 * Copyright (C) 2018 Push Technology Ltd.
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
package com.pushtechnology.connect.diffusion.client.impl;

import static com.pushtechnology.diffusion.client.features.control.topics.TopicUpdateControl.MISSING_TOPIC;

import java.util.concurrent.CompletableFuture;

import com.pushtechnology.connect.diffusion.client.DiffusionClient;
import com.pushtechnology.connect.diffusion.client.DiffusionClientFactory;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.callbacks.ErrorReason;
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.features.control.topics.TopicControl;
import com.pushtechnology.diffusion.client.features.control.topics.TopicUpdateControl;
import com.pushtechnology.diffusion.client.features.control.topics.TopicUpdateControl.Updater.UpdateCallback;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.Session.Listener;
import com.pushtechnology.diffusion.client.session.Session.State;
import com.pushtechnology.diffusion.client.session.SessionFactory;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;
import com.pushtechnology.diffusion.client.topics.details.TopicType;
import com.pushtechnology.diffusion.datatype.Bytes;
import com.pushtechnology.diffusion.datatype.json.JSON;

/**
 * Implementation of {@link DiffusionClientFactory}.
 * <P>
 * Constructs {@link DiffusionClient} instances connected to provided server details.
 */
public class DiffusionClientFactoryImpl implements DiffusionClientFactory {
	private static final SessionFactory factory = Diffusion.sessions();

	@Override
	public DiffusionClient connect(DiffusionConfig config, Listener listener) throws Exception {
		final Session session = factory.principal(config.username())
				.password(config.password())
				.listener(listener)
				.noReconnection()
				.serverHost(config.host())
				.serverPort(config.port())
				.open();
		
		return new DiffusionClientImpl(session);
	}
	
	private static class DiffusionClientImpl implements DiffusionClient {
		private final TopicUpdateControl topicUpdateControl;
		private final TopicControl topicControl;
		private final Topics topics;
		
		private final Session session;

		protected DiffusionClientImpl(Session session) {
			topicUpdateControl = session.feature(TopicUpdateControl.class);
			topicControl = session.feature(TopicControl.class);
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
		public CompletableFuture<?> subscribe(String topicSelector, SubscriptionStream stream) {
			topics.addStream(topicSelector, JSON.class, new Topics.ValueStream.Default<JSON>() {
				@Override
				public void onValue(String topicPath, TopicSpecification specification, JSON oldValue, JSON newValue) {
					stream.onMessage(topicPath, specification, newValue);
				}
			});
		
			return topics.subscribe(topicSelector);
		}
		
		@Override
		public <T extends Bytes> CompletableFuture<T> update(String topicPath, T value) {
			final CompletableFuture<T> future = new CompletableFuture<>();
			
			topicUpdateControl
				.updater()
				.update(topicPath, value, new UpdateCallback() {
					@Override
					public void onError(ErrorReason errorReason) {
						if (MISSING_TOPIC.equals(errorReason)) {
							topicControl.addTopic(topicPath, TopicType.JSON).handle((result, err) -> {
								if (err != null) {
									future.completeExceptionally(err);
								} else {
									topicUpdateControl
									.updater()
									.update(topicPath, value, new UpdateCallback() {
										@Override
										public void onError(ErrorReason errorReason) {
											// Terminal, since we already determined that the topic itself exists
											future.completeExceptionally(new ErrorReasonException(errorReason));
										}
	
										@Override
										public void onSuccess() {
											future.complete(value);
										}
									});
								}
								
								return null;
							});
						} else {
							future.completeExceptionally(new ErrorReasonException(errorReason));
						}
					}

					@Override
					public void onSuccess() {
						future.complete(value);
					}
				});
			
			return future;
		}
		
		public static class ErrorReasonException extends Throwable {
			private static final long serialVersionUID = 1905000388329387731L;
			private final ErrorReason errorReason;
			
			public ErrorReasonException(ErrorReason errorReason) {
				this.errorReason = errorReason;
			}
			
			public ErrorReason getErrorReason() {
				return errorReason;
			}
		}
	}
}