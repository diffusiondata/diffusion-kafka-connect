package com.diffusiondata.connect.diffusion.data;

import static com.diffusiondata.connect.diffusion.data.JSONToSchemaValue.valueFromJson;
import static java.util.Arrays.asList;
import static org.apache.kafka.connect.data.Schema.Type.ARRAY;
import static org.apache.kafka.connect.data.SchemaBuilder.array;
import static org.apache.kafka.connect.data.SchemaBuilder.type;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.junit.jupiter.api.Test;

import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.datatype.json.JSONDataType;

public class JSONToSchemaValueTest {
    private static JSONDataType JSON_TYPE =
        Diffusion.dataTypes().json();

    @Test
    public void testPrimitives() throws Exception {
        assertSchemaAndValue(null, null, "null");
        assertSchemaAndValue(Schema.BOOLEAN_SCHEMA, true, "true");
        assertSchemaAndValue(Schema.BOOLEAN_SCHEMA, false, "false");
        assertSchemaAndValue(Schema.INT32_SCHEMA, 123, "123");
        assertSchemaAndValue(Schema.FLOAT64_SCHEMA, 123.456, "123.456");
        assertSchemaAndValue(
            Schema.STRING_SCHEMA, "hello world", "\"hello world\"");
    }

    @Test
    public void testArray() throws Exception {
        final Schema schema = SchemaBuilder.array(Schema.INT32_SCHEMA).build();
        assertSchemaAndValue(schema, asList(4, 5, 6), "[4,5,6]");
    }

    @Test
    public void testMixedArray() throws Exception {
        assertSchemaAndValue(
            null,
            asList(4, "hello world", true),
            "[4,\"hello world\",true]");
    }

    @Test
    public void testNestedArray() throws Exception {
        final Schema schema = array(type(ARRAY).build()).build();
        assertSchemaAndValue(
            schema, asList(asList(1, 2, 3),
                asList(4, 5, 6)),
            "[[1,2,3],[4,5,6]]");
    }

    @Test
    public void testObject() throws Exception {
        final Map<String, Object> map = new HashMap<>();
        map.put("foo", "hello world");
        map.put("bar", 123);
        map.put("baz", asList(4, 5, 6));

        assertSchemaAndValue(
            null,
            map,
            "{\"foo\":\"hello world\",\"bar\":123,\"baz\":[4,5,6]}");
    }

    @Test
    public void testNestedObject() throws Exception {
        final Map<String, Object> map = new HashMap<>();
        map.put("foo", "hello world");
        map.put("bar", 123);
        map.put("baz", asList(4, 5, 6));

        final Map<String, Object> map2 = new HashMap<>();
        map2.put("foo", map);

        assertSchemaAndValue(
            null,
            map2,
            "{\"foo\":{\"foo\":\"hello world\",\"bar\":123,\"baz\":[4,5,6]}}");
    }

    private void assertSchemaAndValue(Schema type, Object value, String json) {
        SchemaAndValue result = valueFromJson(JSON_TYPE.fromJsonString(json));

        assertEquals(type, result.schema());
        assertEquals(value, result.value());
    }
}
