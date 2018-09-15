package com.pushtechnology.connect.diffusion.sink;

import static com.pushtechnology.diffusion.client.session.Session.State.CLOSED_BY_SERVER;
import static com.pushtechnology.diffusion.client.session.Session.State.CONNECTED_ACTIVE;
import static com.pushtechnology.diffusion.client.session.Session.State.CONNECTING;
import static com.pushtechnology.diffusion.client.session.Session.State.RECOVERING_RECONNECT;
import static com.pushtechnology.test.util.SinkRecordBuilder.sinkRecord;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.kafka.connect.data.SchemaBuilder.array;
import static org.apache.kafka.connect.data.SchemaBuilder.bool;
import static org.apache.kafka.connect.data.SchemaBuilder.float32;
import static org.apache.kafka.connect.data.SchemaBuilder.float64;
import static org.apache.kafka.connect.data.SchemaBuilder.int16;
import static org.apache.kafka.connect.data.SchemaBuilder.int32;
import static org.apache.kafka.connect.data.SchemaBuilder.int64;
import static org.apache.kafka.connect.data.SchemaBuilder.int8;
import static org.apache.kafka.connect.data.SchemaBuilder.string;
import static org.apache.kafka.connect.data.SchemaBuilder.struct;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import com.pushtechnology.connect.diffusion.client.DiffusionClient;
import com.pushtechnology.connect.diffusion.client.DiffusionClientFactory;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig;
import com.pushtechnology.connect.diffusion.config.DiffusionConfig.SinkConfig;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.session.Session.Listener;
import com.pushtechnology.diffusion.datatype.json.JSON;

public class DiffusionSinkTaskTest {
	private static final JSON DUMMY_JSON = Diffusion.dataTypes().json().fromJsonString("{\"foo\":\"bar\"}");
	
	
	@Mock
	private DiffusionClientFactory factory;
	
	@Mock
	private DiffusionClient client;
	
	@Mock
	private CompletableFuture<JSON> goodFuture;
	
	@Mock
	private CompletableFuture<JSON> badFuture;
	
	@Captor
	private ArgumentCaptor<SinkConfig> configCaptor;
	
	@Captor
	private ArgumentCaptor<Listener> listenerCaptor;
	
	@Captor
	private ArgumentCaptor<JSON> jsonCaptor;
	
	private Map<String, String> props = new HashMap<>();
	private DiffusionSinkTask task;
	
	private Schema schema;
	private Struct struct;
	
	@Before
	@SuppressWarnings("unchecked")
	public void setUp() throws Exception {
		initMocks(this);

		when(factory.connect(isA(SinkConfig.class), isA(Listener.class))).thenReturn(client);
		
		when(goodFuture.get(isA(Long.class), isA(TimeUnit.class))).thenReturn(DUMMY_JSON);
		when(badFuture.get(isA(Long.class), isA(TimeUnit.class))).thenThrow(ExecutionException.class);
		
		props.put(DiffusionConfig.HOST, "localhost");
		props.put(DiffusionConfig.PORT, "8080");
		props.put(DiffusionConfig.USERNAME, "admin");
		props.put(DiffusionConfig.PASSWORD, "password");
		props.put(DiffusionConfig.DIFFUSION_DESTINATION, "topic");
		
		schema = struct()
				.field("foo", string())
				.field("bar", int32())
				.field("baz", array(int32()))
				.build();
		
		struct = new Struct(schema);
		
		task = new DiffusionSinkTask(factory);
	}
	
	@After
	public void after() {
		verifyNoMoreInteractions(client, factory, goodFuture, badFuture);
	}
	
	private void start() throws Exception {
		task.start(props);
		
		verify(factory).connect(configCaptor.capture(), listenerCaptor.capture());
		
		listenerCaptor.getValue().onSessionStateChanged(null, CONNECTING, CONNECTED_ACTIVE);
	}
	
	@Test
	public void testStart() throws Exception {
		start();
		
		SinkConfig config = configCaptor.getValue();
		
		assertEquals(config, new SinkConfig(props));
	}
	
	@Test
	public void testStop() throws Exception {
		testStart();
		
		task.stop();
		
		verify(client).close();
	}
	
	@Test
	public void testPutFailsWhenClosed() throws Exception {
		start();
		
		listenerCaptor.getValue().onSessionStateChanged(null, CONNECTED_ACTIVE, CLOSED_BY_SERVER);
		
		when(client.getSessionState()).thenReturn(CLOSED_BY_SERVER);
		
		try {
			task.put(asList(sinkRecord().val(null, true).build()));
		} catch (ConnectException e) {
			verify(client, never()).update(isA(String.class), isA(JSON.class));
		}
		
		verify(client).getSessionState();
	}
	
	@Test
	public void testPutFailsWithRetriableWhenDisconnected() throws Exception {
		start();
		
		listenerCaptor.getValue().onSessionStateChanged(null, CONNECTED_ACTIVE, RECOVERING_RECONNECT);
		
		when(client.getSessionState()).thenReturn(RECOVERING_RECONNECT);
		
		try {
			task.put(asList(sinkRecord().val(null, true).build()));
		} catch (RetriableException e) {
			verify(client, never()).update(isA(String.class), isA(JSON.class));
		}
		
		verify(client).getSessionState();
	}
	
	@Test
	public void testPutPrimitives() throws Exception {
		start();
		
		List<SinkRecord> records = new ArrayList<>();
		
		records.add(sinkRecord().val(bool(), true).build());
		records.add(sinkRecord().val(int8(), 77).build());
		records.add(sinkRecord().val(int16(), 777).build());
		records.add(sinkRecord().val(int32(), 777777).build());
		records.add(sinkRecord().val(int64(), 777777777777777l).build());
		records.add(sinkRecord().val(float32(), 7.7).build());
		records.add(sinkRecord().val(float64(), 7.7).build());
		records.add(sinkRecord().val(string(), "hello world").build());
		
		task.put(records);
		
		verify(client, times(8)).update(eq("topic"), jsonCaptor.capture());
		
		List<String> values = jsonCaptor.getAllValues()
												.stream()
												.map(JSON::toJsonString)
												.collect(toList());
		
		assertEquals("true", values.get(0));
		assertEquals("77", values.get(1));
		assertEquals("777", values.get(2));
		assertEquals("777777", values.get(3));
		assertEquals("777777777777777", values.get(4));
		assertEquals("7.7", values.get(5));
		assertEquals("7.7", values.get(6));
		assertEquals("\"hello world\"", values.get(7));
		
	}
	
	@Test
	public void testPutSchemalessPrimitives() throws Exception {
		start();
		
		List<SinkRecord> records = new ArrayList<>();
		
		records.add(sinkRecord().val(null, true).build());
		records.add(sinkRecord().val(null, 77).build());
		records.add(sinkRecord().val(null, 777).build());
		records.add(sinkRecord().val(null, 777777).build());
		records.add(sinkRecord().val(null, 777777777777777l).build());
		records.add(sinkRecord().val(null, 7.7).build());
		records.add(sinkRecord().val(null, 7.7).build());
		records.add(sinkRecord().val(null, "hello world").build());
		
		task.put(records);
		
		verify(client, times(8)).update(eq("topic"), jsonCaptor.capture());
		
		List<String> values = jsonCaptor.getAllValues()
												.stream()
												.map(JSON::toJsonString)
												.collect(toList());
		
		assertEquals("true", values.get(0));
		assertEquals("77", values.get(1));
		assertEquals("777", values.get(2));
		assertEquals("777777", values.get(3));
		assertEquals("777777777777777", values.get(4));
		assertEquals("7.7", values.get(5));
		assertEquals("7.7", values.get(6));
		assertEquals("\"hello world\"", values.get(7));
		
	}
	
	@Test
	public void testPutStruct() throws Exception {
		start();
		
		struct.put("foo", "hello world");
		struct.put("bar", 123);
		struct.put("baz", asList(4, 5, 6));
		
		List<SinkRecord> records = asList(sinkRecord().val(schema, struct).build());
		
		task.put(records);
		
		verify(client).update(eq("topic"), jsonCaptor.capture());
		
		String value = jsonCaptor.getValue().toJsonString();
		
		assertEquals("{\"foo\":\"hello world\",\"bar\":123,\"baz\":[4,5,6]}", value);
	}
	
	@Test(expected = DataException.class)
	public void testThrowsWithSchemalessStruct() throws Exception {
		start();
		
		struct.put("foo", "hello world");
		struct.put("bar", 123);
		struct.put("baz", asList(4, 5, 6));
		
		List<SinkRecord> records = asList(sinkRecord().val(null, struct).build());
		
		task.put(records);
	}
	
	@Test
	public void testFlushesPendingOffsets() throws Exception {
		start();
		
		Map<TopicPartition, OffsetAndMetadata> partitionOffsets = new HashMap<>();
	    partitionOffsets.put(new TopicPartition("topic", 0), null);
	    
	    List<SinkRecord> records = new ArrayList<>();
		
		records.add(sinkRecord().topic("topic").partition(0).val(null, true).build());
		records.add(sinkRecord().topic("topic").partition(0).val(null, false).build());
		
		when(client.update(isA(String.class), isA(JSON.class))).thenReturn(goodFuture);
		
	    task.put(records);
	    task.flush(partitionOffsets);
	    
	    verify(client, times(2)).update(eq("topic"), jsonCaptor.capture());
	    verify(goodFuture, times(2)).get(5, TimeUnit.SECONDS);
	    
	    // Should be no-op; if pending isn't cleared, will fail teardown verification of future invocation
	    task.flush(partitionOffsets);
	}
	
	@Test
	public void testThrowsOnFailedFlushPendingOffsets() throws Exception {
		start();
		
		Map<TopicPartition, OffsetAndMetadata> partitionOffsets = new HashMap<>();
	    partitionOffsets.put(new TopicPartition("topic", 0), null);
	    
	    List<SinkRecord> records = new ArrayList<>();
		
		records.add(sinkRecord().topic("topic").partition(0).val(null, true).build());
		records.add(sinkRecord().topic("topic").partition(0).val(null, false).build());
		
		when(client.update(isA(String.class), isA(JSON.class))).thenReturn(badFuture);
		
	    task.put(records);
	    
	    verify(client, times(2)).update(eq("topic"), jsonCaptor.capture());
	    
	    try {
	    	task.flush(partitionOffsets);
	    } catch (ConnectException e) {
	    	Assert.assertTrue("Error cause is from future", e.getCause() instanceof ExecutionException);
	    }
	    
	    verify(badFuture, times(1)).get(5, TimeUnit.SECONDS);
	}
}
