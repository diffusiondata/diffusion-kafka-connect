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
package com.pushtechnology.connect.diffusion.sink;

import static com.pushtechnology.connect.diffusion.util.TopicPathMapping.pathFromRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;

import com.pushtechnology.connect.diffusion.client.DiffusionClient;
import com.pushtechnology.connect.diffusion.client.DiffusionClientFactory;
import com.pushtechnology.connect.diffusion.client.impl.DiffusionClientFactoryImpl;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig.SinkConfig;
import com.pushtechnology.connect.diffusion.data.RecordToJSON;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.Session.Listener;
import com.pushtechnology.diffusion.client.session.Session.State;
import com.pushtechnology.diffusion.datatype.json.JSON;

public class DiffusionSinkTask extends SinkTask implements Listener {
	private final Map<String, Map<Integer, List<CompletableFuture<JSON>>>> pendingOffsets = new HashMap<>();
	private volatile boolean running = false;
	
	private DiffusionClientFactory factory = new DiffusionClientFactoryImpl();
	private DiffusionClient client;
	private SinkConfig config;
	
	public DiffusionSinkTask() {
		
	}
	
	/* For testing. */
	protected DiffusionSinkTask(DiffusionClientFactory factory) {
		this.factory = factory;
	}
	
	@Override
	public String version() {
		return DiffusionConfig.VERSION;
	}

	@Override
	public void start(Map<String, String> props) {
		config = new SinkConfig(props);
		
		try {
			client = factory.connect(config, this);
		} catch (Exception e) {
			throw new ConnectException("Unable to establish connection to Diffusion", e);
		}
	}

	@Override
	public void put(Collection<SinkRecord> records) {
		if (running) {
			for (SinkRecord record : records) {
				
				final String topicPath = pathFromRecord(config.diffusionDestination(), record);
				final JSON value = RecordToJSON.jsonFromRecord(record);

				final CompletableFuture<JSON> result = client.update(topicPath, value);
				
				markPending(record, result);
			}
		} else {
			if (client.getSessionState() == State.RECOVERING_RECONNECT) {
				throw new RetriableException("Diffusion Client is not connected, attempting reconnection");
			} else {
				throw new ConnectException("Diffusion Client is closed");
			}
		}
	}
	
	private void markPending(SinkRecord record, CompletableFuture<JSON> result) {
		Map<Integer, List<CompletableFuture<JSON>>> pendingForTopic = pendingOffsets.get(record.topic());
		
		if (pendingForTopic == null) {
			pendingForTopic = new HashMap<>();
			pendingOffsets.put(record.topic(), pendingForTopic);
		}
		
		List<CompletableFuture<JSON>> pendingForPartition = pendingForTopic.get(record.kafkaPartition());
		
		if (pendingForPartition == null) {
			pendingForPartition = new ArrayList<>();
			pendingForTopic.put(record.kafkaPartition(), pendingForPartition);
		}
		
		pendingForPartition.add(result);
	}
	
	@Override
	public void flush(Map<TopicPartition, OffsetAndMetadata> partitionOffsets) {
		for (Entry<TopicPartition, OffsetAndMetadata> entry : partitionOffsets.entrySet()) {
			Map<Integer, List<CompletableFuture<JSON>>> pendingForTopic = pendingOffsets.get(entry.getKey().topic());
			
			if (pendingForTopic == null) {
				continue;
			}
			
			List<CompletableFuture<JSON>> pendingForPartition = pendingForTopic.get(entry.getKey().partition());
			
			if (pendingForPartition == null) {
				continue;
			}
			
			try {
				for (CompletableFuture<JSON> future : pendingForPartition) {
					future.get(5, TimeUnit.SECONDS);
				}
			} catch (Exception e) {
				throw new ConnectException("Unable to flush pending offsets", e);
			}
			
			pendingForPartition.clear();
		}
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
