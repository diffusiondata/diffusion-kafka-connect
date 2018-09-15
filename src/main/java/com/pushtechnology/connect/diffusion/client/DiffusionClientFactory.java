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
package com.pushtechnology.connect.diffusion.client;

import com.pushtechnology.connect.diffusion.config.DiffusionConfig;
import com.pushtechnology.diffusion.client.session.Session.Listener;

/**
 * Connection factory for {@link DiffusionClient} instances. 
 * <P>
 * This factory interface is primarily used for dependency injection in tests.
 */
public interface DiffusionClientFactory {
	/**
	 * Create a new {@link DiffusionClient} instance.
	 * 
	 * @param config The config instance containing Diffusion server details
	 * @param listener The session state listener
	 * @return A new connected DiffusionClient instance
	 * 
	 * @throws Exception If no connection can be established to the remote Diffusion server 
	 */
	DiffusionClient connect(DiffusionConfig config, Listener listener) throws Exception;
}
