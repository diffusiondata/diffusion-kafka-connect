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

import static java.util.Arrays.asList;

import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

import com.pushtechnology.connect.diffusion.config.DiffusionConfig;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig.SourceConfig;

public class DiffusionSourceConnector extends SourceConnector {
	private SourceConfig sourceConfig;
	private Map<String, String> props;
	
	@Override
	public ConfigDef config() {
		return DiffusionConfig.SOURCE_CONFIG;
	}

	@Override
	public void start(Map<String, String> props) {
		this.props = props;
		sourceConfig = new SourceConfig(props);
	}

	@Override
	public void stop() {
		// No-op
	}

	@Override
	public Class<? extends Task> taskClass() {
		return DiffusionSourceTask.class;
	}

	@Override
	public List<Map<String, String>> taskConfigs(int maxTasks) {
		// Diffusion does not currently support sufficient parallelism primitives to safely
		// run multiple concurrent tasks in the way that the Connect framework expects. 
		// Since Diffusion operates under last-write-wins, there is no way for tasks
		// receiving messages across multiple partitions to guarantee correct ordering
		return asList(props);
	}

	@Override
	public String version() {
		return DiffusionConfig.VERSION;
	}
}
