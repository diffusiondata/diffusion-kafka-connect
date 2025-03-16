package com.pushtechnology.connect.diffusion.data;

import static com.pushtechnology.connect.diffusion.data.RecordToJSON.jsonFromRecord;
import static com.pushtechnology.test.util.SinkRecordBuilder.sinkRecord;
import static java.util.Arrays.asList;
import static org.apache.kafka.connect.data.Schema.INT32_SCHEMA;
import static org.apache.kafka.connect.data.Schema.STRING_SCHEMA;
import static org.apache.kafka.connect.data.Schema.Type.ARRAY;
import static org.apache.kafka.connect.data.Schema.Type.MAP;
import static org.apache.kafka.connect.data.SchemaBuilder.array;
import static org.apache.kafka.connect.data.SchemaBuilder.map;
import static org.apache.kafka.connect.data.SchemaBuilder.struct;
import static org.apache.kafka.connect.data.SchemaBuilder.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.junit.jupiter.api.Test;

public class RecordToJSONTest {

	@Test
	public void testParsePrimitivesWithSchema() {
		assertWithAndWithoutSchema(Schema.BOOLEAN_SCHEMA, true, "true");
		assertWithAndWithoutSchema(Schema.BOOLEAN_SCHEMA, false, "false");
		assertWithAndWithoutSchema(Schema.INT8_SCHEMA, (byte) 7, "7");
		assertWithAndWithoutSchema(Schema.INT16_SCHEMA, (short) 7, "7");
		assertWithAndWithoutSchema(Schema.INT32_SCHEMA, 7, "7");
		assertWithAndWithoutSchema(Schema.INT64_SCHEMA, 777777777777777l, "777777777777777");
		assertWithAndWithoutSchema(Schema.FLOAT32_SCHEMA, 7.7, "7.7");
		assertWithAndWithoutSchema(Schema.FLOAT64_SCHEMA, (float) 7.7, "7.7");
		assertWithAndWithoutSchema(Schema.FLOAT64_SCHEMA, (double) 7.7, "7.7");
		assertWithAndWithoutSchema(Schema.STRING_SCHEMA, "hello world", "\"hello world\"");
	}

	@Test
	public void testParseArray() {
		assertWithAndWithoutSchema(array(INT32_SCHEMA), asList(1, 2, 3, 4, 5), "[1,2,3,4,5]");
	}

	@Test
	public void testParseNestedArray() {
		assertWithAndWithoutSchema(array(array(INT32_SCHEMA)), asList(asList(1, 2, 3), asList(4, 5, 6)), "[[1,2,3],[4,5,6]]");
	}

	@Test
	public void testParseNestedArrayWithPartialSchema() {
		assertWithAndWithoutSchema(array(type(ARRAY).build()), asList(asList(1, 2, 3), asList(4, 5, 6)), "[[1,2,3],[4,5,6]]");
	}

	@Test
	public void testParseMap() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("foo", 1);
		map.put("bar", 2);
		map.put("baz", 3);

		assertWithAndWithoutSchema(map(STRING_SCHEMA, INT32_SCHEMA), map, "{\"foo\":1,\"bar\":2,\"baz\":3}");
	}

	@Test
	public void testParseMapWithNonPrimitiveKeyThrows() {
		Map<List<Integer>, Integer> map = new LinkedHashMap<>();
		map.put(asList(1), 1);
		map.put(asList(2), 2);
		map.put(asList(3), 3);

		assertThrows(
			DataException.class,
			() -> jsonFromRecord((sinkRecord().val(null, map).build())));
	}

	@Test
	public void testParseNestedMap() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("foo", 1);
		map.put("bar", 2);
		map.put("baz", 3);

		Map<String, Map<String, Integer>> map2 = new LinkedHashMap<>();
		map2.put("foo", map);
		map2.put("bar", map);

		assertWithAndWithoutSchema(map(STRING_SCHEMA, map(STRING_SCHEMA, INT32_SCHEMA)), map2, "{\"foo\":{\"foo\":1,\"bar\":2,\"baz\":3},\"bar\":{\"foo\":1,\"bar\":2,\"baz\":3}}");
	}

	@Test
	public void testParseNestedMapWithPartialSchema() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("foo", 1);
		map.put("bar", 2);
		map.put("baz", 3);

		Map<String, Map<String, Integer>> map2 = new LinkedHashMap<>();
		map2.put("foo", map);
		map2.put("bar", map);

		assertWithAndWithoutSchema(map(STRING_SCHEMA, type(MAP)), map2, "{\"foo\":{\"foo\":1,\"bar\":2,\"baz\":3},\"bar\":{\"foo\":1,\"bar\":2,\"baz\":3}}");
	}

	@Test
	public void testParseStruct() {
		Schema schema = struct()
			.field("foo", STRING_SCHEMA)
			.field("bar", INT32_SCHEMA)
			.field("baz", array(INT32_SCHEMA))
			.build();

		Struct struct = new Struct(schema);
		struct.put("foo", "hello world");
		struct.put("bar", 123);
		struct.put("baz", asList(1, 2, 3));

		assertOnlyWithSchema(schema, struct, "{\"foo\":\"hello world\",\"bar\":123,\"baz\":[1,2,3]}");
	}

	@Test
	public void testParseStructWithoutSchemaThrows() {
		Schema schema = struct()
			.field("foo", STRING_SCHEMA)
			.field("bar", INT32_SCHEMA)
			.field("baz", array(INT32_SCHEMA))
			.build();

		Struct struct = new Struct(schema);
		struct.put("foo", "hello world");
		struct.put("bar", 123);
		struct.put("baz", asList(1, 2, 3));

		assertThrows(
			DataException.class,
			() -> 		assertOnlyWithSchema(null, struct, "{\"foo\":\"hello world\",\"bar\":123,\"baz\":[1,2,3]}"));
	}

	public void assertOnlyWithSchema(Schema schema, Object value, String json) {
		assertEquals(json, jsonFromRecord((sinkRecord().val(schema, value).build())).toJsonString(), "Can parse with schema");
	}

	public void assertWithAndWithoutSchema(Schema schema, Object value, String json) {
		assertEquals(json, jsonFromRecord((sinkRecord().val(schema, value).build())).toJsonString(), "Can parse with schema");
		assertEquals(json, jsonFromRecord((sinkRecord().val(null, value).build())).toJsonString(), "Can parse without schema");
	}
}
