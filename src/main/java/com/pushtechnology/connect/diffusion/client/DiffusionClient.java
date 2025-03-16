/*******************************************************************************
 * Copyright (C) 2018,2025 Push Technology Ltd.
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
package com.pushtechnology.connect.diffusion.client;

import java.util.concurrent.CompletableFuture;

import com.pushtechnology.diffusion.client.session.Session.State;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;
import com.pushtechnology.diffusion.client.topics.details.TopicType;
import com.pushtechnology.diffusion.datatype.Bytes;
import com.pushtechnology.diffusion.datatype.json.JSON;

/**
 * Interface for generic client interactions over Diffusion.
 * <P>
 * Abstracts common operations to more naturally fit behavior required for the Connect framework.
 */
public interface DiffusionClient {

	/**
	 * Close any underlying connection to Diffusion
	 */
	void close();

	/**
	 * Return the current state of any connection to Diffusion.
	 * 
	 * @return Current session state
	 */
	State getSessionState();
	
	/**
	 * Subscribe to one or more topics on Diffusion.
	 * 
	 * @param topicSelector The topic selector with which to subscribe
	 * @param stream The stream handler for receiving messages
	 * 
	 * @return A completable future representing the state of the subscribe operation
	 */
	CompletableFuture<?> subscribe(String topicSelector, SubscriptionStream stream);

	/**
	 * Update a topic on Diffusion.
	 * <P>
	 * If the topic does not exist, it will be created and then the update will be retried.
	 * <P> 
	 * The returned future will contain either the sucesfully published value, or an ExecutionException
	 * indicating the point of failure. 
	 * 
	 * @param topicPath - The topic path to update
	 * @param topicType - The type of the Diffusion topic
	 * @param clazz - The class type of the update
	 * @param value - The value to update
	 * @return A completable future representing the state of the operation
	 */
	<T extends Bytes> CompletableFuture<T> update(
		String topicPath, TopicType topicType, Class<T> clazz, T value);
	
	/**
	 * Stream interface for receiving messages from a subscription.
	 */
	@FunctionalInterface
	interface SubscriptionStream {
		/**
		 * Handle a new message for a subscribed topic.
		 * 
		 * @param topicPath The topic path from which this message came
		 * @param specification The specification for the associated topic
		 * @param value The new topic value
		 */
		void onMessage(String topicPath, TopicSpecification specification, JSON value);
	}
}