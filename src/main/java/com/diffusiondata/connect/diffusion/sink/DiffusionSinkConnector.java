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
package com.diffusiondata.connect.diffusion.sink;

import static com.diffusiondata.connect.diffusion.config.DiffusionConfig.SINK_CONFIG;
import static java.util.Arrays.asList;

import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;

import com.diffusiondata.connect.diffusion.config.DiffusionConfig;
import com.diffusiondata.connect.diffusion.config.DiffusionConfig.SinkConfig;

public class DiffusionSinkConnector extends SinkConnector {
	private SinkConfig sinkConfig;
	private Map<String, String> props;
	
	@Override
	public String version() {
		return DiffusionConfig.VERSION;
	}

	@Override
	public void start(Map<String, String> props) {
		this.props = props;
		sinkConfig = new SinkConfig(props);
	}

	@Override
	public Class<? extends Task> taskClass() {
		return DiffusionSinkTask.class;
	}

	@Override
	public List<Map<String, String>> taskConfigs(int maxTasks) {
		// Diffusion does not currently support sufficient parallelism primitives to safely
		// run multiple concurrent tasks in the way that the Connect framework expects. 
		// We also can't determine ahead of time how many concrete topics a given selector 
		// will resolve against, and even if we could, that number may change at any point 
		// in the future 
		return asList(props);
	}

	@Override
	public void stop() {
		// No-op
	}

	@Override
	public ConfigDef config() {
		return SINK_CONFIG;
	}

}
