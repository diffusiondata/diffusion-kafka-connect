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
package com.pushtechnology.connect.diffusion.source;

import static com.pushtechnology.connect.diffusion.data.JSONToSchemaValue.valueFromJson;
import static com.pushtechnology.connect.diffusion.util.TopicPathMapping.pathFromTopic;
import static java.lang.Thread.sleep;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pushtechnology.connect.diffusion.client.DiffusionClient;
import com.pushtechnology.connect.diffusion.client.DiffusionClientFactory;
import com.pushtechnology.connect.diffusion.client.impl.DiffusionClientFactoryImpl;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig.SourceConfig;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.Session.Listener;
import com.pushtechnology.diffusion.client.session.Session.State;

public class DiffusionSourceTask extends SourceTask implements Listener {
	private static final Logger LOGGER = LoggerFactory.getLogger(DiffusionSourceTask.class);
	
	private static final int POLL_RETRIES = 3;
	
	private final ConcurrentLinkedQueue<SourceRecord> messages = new ConcurrentLinkedQueue<>();
	private volatile boolean running = false;
	
	private DiffusionClientFactory factory = new DiffusionClientFactoryImpl();
	private DiffusionClient client;
	private SourceConfig config;
	
	public DiffusionSourceTask() {
		
	}
	
	/* For testing. */
	protected DiffusionSourceTask(DiffusionClientFactory factory) {
		this.factory = factory;
	}
	
	public String version() {
		return DiffusionConfig.VERSION;
	}

	@Override
	public void start(Map<String, String> props) {
		config = new SourceConfig(props);
		
		try {
			client = factory.connect(config, this);
		} catch (Exception e) {
			throw new ConnectException("Unable to establish connection to Diffusion", e);
		}
		
		final CompletableFuture<?> sub = client.subscribe(config.diffusionTopic(), (path, spec, value) -> {
			final String topic = pathFromTopic(config.kafkaTopic(), path);
			final SchemaAndValue parsed = valueFromJson(value);
			final Map<String, String> p = singletonMap("topic", path);
			
			messages.add(new SourceRecord(
					p,
					null,
					topic, 
					Schema.STRING_SCHEMA, 
					path, 
					parsed.schema(), 
					parsed.value()));

			LOGGER.debug("New message received for: {}", path);
		});
		
		try {
			sub.get(10, SECONDS);
		} catch (Exception e) {
			throw new ConnectException("Unable to subscribe to: " + config.diffusionTopic(), e);
		}
	}
	
	@Override
	public List<SourceRecord> poll() throws InterruptedException {
        final ArrayList<SourceRecord> records = new ArrayList<>(config.pollBatchSize());
        int retries = 0;
        
        while (retries < POLL_RETRIES && records.isEmpty()) {
        	for (int i = 0; i < config.pollBatchSize(); ++i) {
        		final SourceRecord record = messages.poll();
        		
        		if (record == null) {
        			break;
        		}
        		
        		records.add(record);
        	}
        	
        	if (records.isEmpty()) {
        		sleep(config.pollInterval());
        		retries++;
        	}
        }
        
        // If we've exhausted pending messages, and the Diffusion client is disconnected, inform Connect framework
        if (records.isEmpty() && !running) {
        	if (client.getSessionState() == State.RECOVERING_RECONNECT) {
				throw new RetriableException("Diffusion Client is not connected, attempting reconnection");
			} else {
				throw new ConnectException("Diffusion Client is closed");
			}
        }

		return records;
	}

	@Override
	public void stop() {
		client.close();
	}

	@Override
	public void onSessionStateChanged(Session session, State oldState, State newState) {
		if (newState == State.CONNECTED_ACTIVE) {
			running = true;
		} else {
			running = false;
		}
	}
}
