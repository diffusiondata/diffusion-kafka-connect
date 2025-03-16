package com.pushtechnology.connect.diffusion.data;

import static com.pushtechnology.connect.diffusion.data.JSONToSchemaValue.valueFromJson;
import static com.pushtechnology.connect.diffusion.data.RecordToJSON.jsonFromRecord;
import static com.pushtechnology.test.util.SinkRecordBuilder.sinkRecord;
import static java.util.Arrays.asList;
import static org.apache.kafka.connect.data.Schema.BOOLEAN_SCHEMA;
import static org.apache.kafka.connect.data.Schema.FLOAT32_SCHEMA;
import static org.apache.kafka.connect.data.Schema.INT32_SCHEMA;
import static org.apache.kafka.connect.data.Schema.STRING_SCHEMA;
import static org.apache.kafka.connect.data.Schema.Type.ARRAY;
import static org.apache.kafka.connect.data.Schema.Type.MAP;
import static org.apache.kafka.connect.data.SchemaBuilder.array;
import static org.apache.kafka.connect.data.SchemaBuilder.map;
import static org.apache.kafka.connect.data.SchemaBuilder.struct;
import static org.apache.kafka.connect.data.SchemaBuilder.type;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.Test;

import com.pushtechnology.diffusion.datatype.json.JSON;

public class RoundTripParsingTest {
	@Test
	public void testPrimitives() throws Exception {
		assertRoundTrip(null, null);
		assertRoundTrip(BOOLEAN_SCHEMA, true);
		assertRoundTrip(BOOLEAN_SCHEMA, false);
		assertRoundTrip(INT32_SCHEMA, 123);
		assertRoundTrip(FLOAT32_SCHEMA, (float) 123.456);
		assertRoundTrip(STRING_SCHEMA, "hello world");
	}

	@Test
	public void testArray() throws Exception {
		assertRoundTrip(array(INT32_SCHEMA).build(), asList(1, 2, 3));
	}

	@Test
	public void testNestedArray() throws Exception {
		assertRoundTrip(array(type(ARRAY).build()).build(), array(array(INT32_SCHEMA)).build(), asList(asList(1, 2, 3), asList(4, 5, 6)));
	}

	@Test
	public void testMap() throws Exception {
		Map<String, Integer> map = new HashMap<>();
		map.put("foo", 1);
		map.put("foo", 2);
		map.put("foo", 3);

		assertRoundTrip(map(STRING_SCHEMA, INT32_SCHEMA).build(), map);
	}

	@Test
	public void testNestedMap() throws Exception {
		Map<String, Integer> map = new HashMap<>();
		map.put("foo", 1);
		map.put("bar", 2);
		map.put("baz", 3);

		Map<String, Map<String, Integer>> map2 = new HashMap<>();
		map2.put("foo", map);
		map2.put("bar", map);
		map2.put("baz", map);

		assertRoundTrip(map(STRING_SCHEMA, type(MAP).build()).build(), map(STRING_SCHEMA, map(STRING_SCHEMA, INT32_SCHEMA).build()).build(), map2);
	}

	@Test
	public void testStruct() throws Exception {
		Schema schema = struct()
			.field("foo", STRING_SCHEMA)
			.field("bar", INT32_SCHEMA)
			.field("baz", array(INT32_SCHEMA))
			.build();

		Struct struct = new Struct(schema);
		struct.put("foo", "hello world");
		struct.put("bar", 123);
		struct.put("baz", asList(1, 2, 3));

		// Cannot synthesize struct from JSON, closest we can get is Map<String, Object>
		Map<String, Object> map = new HashMap<>();
		map.put("foo", "hello world");
		map.put("bar", 123);
		map.put("baz", asList(1, 2, 3));

		JSON json = jsonFromRecord(sinkRecord().val(schema, struct).build());
		SchemaAndValue schemaAndValue = valueFromJson(json);

		// Cannot synthesize schema from JSON representation of struct
		assertEquals(null, schemaAndValue.schema());
		assertEquals(map, schemaAndValue.value());

		JSON json2 = jsonFromRecord(sinkRecord().val(schemaAndValue.schema(), schemaAndValue.value()).build());

		assertEquals(schemaAndValue, valueFromJson(json2));
	}

	private void assertRoundTrip(Schema schema, Object value) {
		assertRoundTrip(schema, schema, value);
	}

	private void assertRoundTrip(Schema toSchema, Schema fromSchema, Object value) {
		JSON json = jsonFromRecord(sinkRecord().val(fromSchema, value).build());
		SchemaAndValue schemaAndValue = valueFromJson(json);

		assertEquals(toSchema, schemaAndValue.schema());
		assertEquals(value, schemaAndValue.value());

		JSON json2 = jsonFromRecord(sinkRecord().val(schemaAndValue.schema(), schemaAndValue.value()).build());

		assertEquals(schemaAndValue, valueFromJson(json2));
	}
}
