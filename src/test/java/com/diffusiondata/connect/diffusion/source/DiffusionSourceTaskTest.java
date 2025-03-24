package com.diffusiondata.connect.diffusion.source;

import static com.pushtechnology.diffusion.client.session.Session.State.CLOSED_BY_SERVER;
import static com.pushtechnology.diffusion.client.session.Session.State.CONNECTED_ACTIVE;
import static com.pushtechnology.diffusion.client.session.Session.State.CONNECTING;
import static com.pushtechnology.diffusion.client.session.Session.State.RECOVERING_RECONNECT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.apache.kafka.connect.data.Schema.STRING_SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diffusiondata.connect.diffusion.client.DiffusionClient;
import com.diffusiondata.connect.diffusion.client.DiffusionClient.SubscriptionStream;
import com.diffusiondata.connect.diffusion.client.DiffusionClientFactory;
import com.diffusiondata.connect.diffusion.config.DiffusionConfig;
import com.diffusiondata.connect.diffusion.config.DiffusionConfig.SourceConfig;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.session.Session.Listener;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;
import com.pushtechnology.diffusion.datatype.json.JSON;
import com.pushtechnology.diffusion.datatype.json.JSONDataType;

@ExtendWith(MockitoExtension.class)
public class DiffusionSourceTaskTest {
    private final JSONDataType jsonType = Diffusion.dataTypes().json();

    @Mock
    private DiffusionClientFactory factory;

    @Mock
    private DiffusionClient client;

    @Mock
    private TopicSpecification topicSpec;

    @Mock
    private CompletableFuture<?> goodFuture;

    @Captor
    private ArgumentCaptor<SourceConfig> configCaptor;

    @Captor
    private ArgumentCaptor<Listener> listenerCaptor;

    @Captor
    private ArgumentCaptor<SubscriptionStream> streamCaptor;

    private Map<String, String> props = new HashMap<>();
    private DiffusionSourceTask task;

    private Map<String, Object> defaultMap;
    private JSON defaultJson;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        when(factory.connect(isA(SourceConfig.class), isA(Listener.class))).thenReturn(client);

        when(goodFuture.get(isA(Long.class), isA(TimeUnit.class)))
            .thenReturn(null);

        props.put(DiffusionConfig.HOST, "localhost");
        props.put(DiffusionConfig.PORT, "8080");
        props.put(DiffusionConfig.USERNAME, "admin");
        props.put(DiffusionConfig.PASSWORD, "password");
        props.put(DiffusionConfig.DIFFUSION_SELECTOR, "topic");
        props.put(DiffusionConfig.KAFKA_TOPIC, "kafka");
        props.put(DiffusionConfig.POLL_INTERVAL, "0");

        task = new DiffusionSourceTask(factory);

        defaultJson =
            jsonType
                .fromJsonString(
                    "{\"foo\":\"hello world\",\"bar\":123,\"baz\":[4,5,6]}");

        defaultMap = new HashMap<>();
        defaultMap.put("foo", "hello world");
        defaultMap.put("bar", 123);
        defaultMap.put("baz", asList(4, 5, 6));
    }

    @AfterEach
    public void after() {
        verifyNoMoreInteractions(client, factory, goodFuture);
    }

    private void start() throws Exception {
        doReturn(goodFuture).when(client).subscribe(isA(String.class),
            Mockito.any());

        task.start(props);

        verify(client).subscribe(eq("topic"), streamCaptor.capture());
        verify(factory).connect(configCaptor.capture(),
            listenerCaptor.capture());
        verify(goodFuture).get(10, TimeUnit.SECONDS);

        listenerCaptor
            .getValue()
            .onSessionStateChanged(null, CONNECTING, CONNECTED_ACTIVE);
    }

    @Test
    public void testStart() throws Exception {
        start();

        SourceConfig config = configCaptor.getValue();

        assertEquals(config, new SourceConfig(props));

        verify(client).subscribe(eq("topic"), streamCaptor.capture());
    }

    @Test
    public void testStop() throws Exception {
        testStart();

        task.stop();

        verify(client).close();
    }

    @Test
    public void testThrowsConnectExceptionWhenClosed() throws Exception {
        start();

        listenerCaptor
            .getValue()
            .onSessionStateChanged(null, CONNECTED_ACTIVE, CLOSED_BY_SERVER);

        when(client.getSessionState()).thenReturn(CLOSED_BY_SERVER);

        assertThrows(ConnectException.class, () -> task.poll());

        verify(client).getSessionState();
    }

    @Test
    public void testThrowsRetriableExceptionWhenDisconnected() throws Exception {
        start();

        listenerCaptor
            .getValue()
            .onSessionStateChanged(null, CONNECTED_ACTIVE,
                RECOVERING_RECONNECT);

        when(client.getSessionState()).thenReturn(RECOVERING_RECONNECT);

        assertThrows(RetriableException.class, () -> task.poll());
        verify(client).getSessionState();
    }

    @Test
    public void testPollNoMessages() throws Exception {
        start();

        assertEquals(0, task.poll().size());
    }

    @Test
    public void testPollSingleMessage() throws Exception {
        start();

        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);

        List<SourceRecord> records = task.poll();

        assertEquals(1, records.size());

        assertRecord(
            records.get(0),
            "kafka",
            singletonMap("topic", "topic"),
            STRING_SCHEMA,
            "topic",
            null,
            defaultMap);
    }

    @Test
    public void testPollSingleMessageWithTopicToken() throws Exception {
        props.put(DiffusionConfig.KAFKA_TOPIC, "kafka/${topic}");

        start();

        streamCaptor.getValue().onMessage("foo", topicSpec, defaultJson);

        List<SourceRecord> records = task.poll();

        assertEquals(1, records.size());

        assertRecord(
            records.get(0),
            "kafka_foo",
            singletonMap("topic", "foo"),
            STRING_SCHEMA,
            "foo",
            null,
            defaultMap);
    }

    @Test
    public void testPollSingleMessageWithUnknownToken() throws Exception {
        props.put(DiffusionConfig.KAFKA_TOPIC, "kafka/${unknown}");

        start();

        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);

        List<SourceRecord> records = task.poll();

        assertEquals(1, records.size());

        assertRecord(
            records.get(0),
            "kafka_${unknown}",
            singletonMap("topic", "topic"),
            STRING_SCHEMA,
            "topic",
            null,
            defaultMap);
    }

    @Test
    public void testPollMultipleMessages() throws Exception {
        start();

        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);
        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);
        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);
        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);

        List<SourceRecord> records = task.poll();

        assertEquals(4, records.size());

        for (SourceRecord record : records) {
            assertRecord(
                record,
                "kafka",
                singletonMap("topic", "topic"),
                STRING_SCHEMA,
                "topic",
                null,
                defaultMap);
        }
    }

    @Test
    public void testDrainQueueWhileDisconnected() throws Exception {
        start();

        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);

        listenerCaptor
            .getValue()
            .onSessionStateChanged(null, CONNECTED_ACTIVE, CLOSED_BY_SERVER);

        when(client.getSessionState()).thenReturn(CLOSED_BY_SERVER);

        List<SourceRecord> records = task.poll();

        assertEquals(1, records.size());

        assertRecord(
            records.get(0),
            "kafka",
            singletonMap("topic", "topic"),
            STRING_SCHEMA,
            "topic",
            null,
            defaultMap);

        assertThrows(ConnectException.class, () -> task.poll());

        verify(client).getSessionState();
    }

    @Test
    public void testPollConstrainsBatchSizeMessages() throws Exception {
        props.put(DiffusionConfig.POLL_BATCH_SIZE, "1");

        start();

        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);
        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);
        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);
        streamCaptor.getValue().onMessage("topic", topicSpec, defaultJson);

        for (int i = 0; i < 4; ++i) {
            List<SourceRecord> records = task.poll();

            assertEquals(1, records.size());

            assertRecord(
                records.get(0),
                "kafka",
                singletonMap("topic", "topic"),
                STRING_SCHEMA,
                "topic",
                null,
                defaultMap);
        }

        assertEquals(0, task.poll().size());
    }

    private void assertRecord(
        SourceRecord record,
        String topic,
        Object partition,
        Schema keySchema,
        Object key,
        Schema valueSchema,
        Object value) {

        assertEquals(topic, record.topic());
        assertEquals(keySchema, record.keySchema());
        assertEquals(key, record.key());
        assertEquals(valueSchema, record.valueSchema());
        assertEquals(value, record.value());
    }
}
