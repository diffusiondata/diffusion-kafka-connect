package com.diffusiondata.connect.diffusion.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.diffusiondata.connect.diffusion.config.DiffusionConfig;

public class DiffusionSourceConnectorTest {
    private final DiffusionSourceConnector connector =
		new DiffusionSourceConnector();
    private final Map<String, String> props = new HashMap<>();

    @BeforeEach
    public void setup() {
        props.put(DiffusionConfig.DIFFUSION_URL, "ws://localhost:8080");
        props.put(DiffusionConfig.USERNAME, "admin");
        props.put(DiffusionConfig.PASSWORD, "password");

        props.put(DiffusionConfig.KAFKA_TOPIC, "topic");
        props.put(DiffusionConfig.DIFFUSION_SELECTOR, "topic");
    }

    @Test
    public void testTaskConfigs() {
        connector.start(props);

        List<Map<String, String>> taskConfigs = connector.taskConfigs(1);

        assertEquals(1, taskConfigs.size());
        assertEquals(taskConfigs.get(0), props);
    }

    @Test
    public void testTaskConfigsWithMoreThanOne() {
        connector.start(props);

        List<Map<String, String>> taskConfigs = connector.taskConfigs(123);

        assertEquals(1, taskConfigs.size());
        assertEquals(taskConfigs.get(0), props);
    }

    @Test
    public void testTaskClass() {
        assertEquals(DiffusionSourceTask.class, connector.taskClass());
    }
}
