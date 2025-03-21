/*******************************************************************************
 * Copyright (C) 2018,2025 DiffusionData Ltd.
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
package com.diffusiondata.connect.diffusion.data;

import static org.apache.kafka.connect.data.Values.convertToBoolean;
import static org.apache.kafka.connect.data.Values.convertToByte;
import static org.apache.kafka.connect.data.Values.convertToFloat;
import static org.apache.kafka.connect.data.Values.convertToInteger;
import static org.apache.kafka.connect.data.Values.convertToList;
import static org.apache.kafka.connect.data.Values.convertToLong;
import static org.apache.kafka.connect.data.Values.convertToMap;
import static org.apache.kafka.connect.data.Values.convertToShort;
import static org.apache.kafka.connect.data.Values.convertToString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;

import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.datatype.json.JSON;
import com.pushtechnology.diffusion.datatype.json.JSONDataType;
import com.pushtechnology.repackaged.jackson.dataformat.cbor.CBORFactory;
import com.pushtechnology.repackaged.jackson.dataformat.cbor.CBORGenerator;

/**
 * Parses a SinkRecord that contains a value schema into a Diffusion-compatible JSON instance. 
 */
public final class RecordToJSON {
	private static JSONDataType JSON_TYPE =
		Diffusion.dataTypes().json();
	private static final CBORFactory CBOR = new CBORFactory();
	
	/**
	 * Parse a {@link SinkRecord} instance and return a Diffusion-compatible {@link JSON} value.
	 * <P>
	 * If the provided record contains a schema, then the value will be parsed according to the
	 * associated schema. If the schema is null, then the parser will attempt to determine the
	 * appropriate serialisation type based on the provided value's class.
	 * <P>
	 * If the record contains a value of an unknown type, or is unable to be serialised to JSON 
	 * (e.g. containing a Map that has non-primitive keys), it will throw a {@link DataException}.
	 * 
	 * @param record The SinkRecord instance to parse into a JSON object
	 * @throws DataException if parsing fails
	 * @return The serialised JSON object
	 */
	public static JSON jsonFromRecord(SinkRecord record) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			CBORGenerator generator = CBOR.createGenerator(out);
			
			writeValue(record.value(), record.valueSchema(), generator);
			
			generator.close();
			
			return JSON_TYPE.readValue(out.toByteArray());
		} catch (Exception e) {
			throw new DataException("Unable to parse record: " + record, e);
		}
	}
	
	private static void writeValue(Object value, Schema schema, CBORGenerator generator) throws IOException {
		if (schema == null) {
			writeValue(value, generator);
			return;
		}
		
		if (value == null) {
			if (schema.isOptional()) {
				generator.writeNull();
				return;
			} else {
				throw new DataException("Encounted null value for required schema: " + schema);
			}
		}
		
		final Type type = schema.type();
		
		switch (type) {
		case STRING:
			generator.writeString(convertToString(schema, value));
			break;
		case BOOLEAN:
            generator.writeBoolean(convertToBoolean(schema, value));
            break;
        case BYTES:
        	if (value instanceof ByteBuffer) {
        		generator.writeBinary(((ByteBuffer) value).array());
        	} else {
        		generator.writeBinary((byte[]) value);
        	}
        	break;
        case FLOAT32:
            generator.writeNumber(convertToFloat(schema, value));
            break;
        case FLOAT64:
            generator.writeNumber(convertToFloat(schema, value));
            break;
        case INT8:
            generator.writeNumber(convertToByte(schema, value));
            break;
        case INT16:
        	generator.writeNumber(convertToShort(schema, value));
            break;
        case INT32:
        	generator.writeNumber(convertToInteger(schema, value));
            break;
        case INT64:
        	generator.writeNumber(convertToLong(schema, value));
            break;
        case ARRAY:
        	generator.writeStartArray();
        	
        	final List<?> list = convertToList(schema, value);
        	
        	for (Object item : list) {
        		writeValue(item, schema.valueSchema(), generator);
        	}
        	
        	generator.writeEndArray();
        	break;
        case MAP:
        	if (schema.keySchema() != null && schema.keySchema().type() != Type.STRING) {
        		throw new DataException("Unable to serialise non-primitive keyed map values");
        	}
        	
        	generator.writeStartObject();
        	
        	final Map<?, ?> map = convertToMap(schema, value);
        	
        	for (Entry<?, ?> entry : map.entrySet()) {
        		generator.writeFieldName(convertToString(schema.keySchema(), entry.getKey()));
        		writeValue(entry.getValue(), schema.valueSchema(), generator);
        	}
        	
        	generator.writeEndObject();
        	break;
        case STRUCT:
        	generator.writeStartObject();
        	
        	final Struct struct = (Struct) value;
        	
        	for (Field field : schema.fields()) {
        		generator.writeFieldName(field.name());
        		writeValue(struct.get(field), field.schema(), generator);
			}
        	
        	generator.writeEndObject();
        	break;
        default:
            throw new DataException("Encountered unsupported value schema type: " + schema);
		}
	}
	
	private static void writeValue(Object value, CBORGenerator generator) throws IOException {
		writeValue(value, generator, false);
	}
	
	@SuppressWarnings("unchecked")
	private static void writeValue(Object value, CBORGenerator generator, boolean isKey) throws IOException {
		if (value == null) {
			generator.writeNull();
		} else if (value instanceof String) {
			if (isKey) {
				generator.writeFieldName(value.toString());
			} else {
				generator.writeString((String) value);
			}
		} else if (value instanceof Boolean) {
            generator.writeBoolean((Boolean) value);
		} else if (value instanceof ByteBuffer) {
    		generator.writeBinary(((ByteBuffer) value).array());
		} else if (value instanceof byte[]) {
    		generator.writeBinary((byte[]) value);
    	} else if (value instanceof Float) {
            generator.writeNumber((Float) value);
        } else if (value instanceof Double) {
            generator.writeNumber((Double)value);
        } else if (value instanceof Byte) {
            generator.writeNumber((Byte) value);
        } else if (value instanceof Short) {
        	generator.writeNumber((Short) value);
        } else if (value instanceof Integer) {
        	generator.writeNumber((Integer) value);
        } else if (value instanceof Long) {
        	generator.writeNumber((Long) value);
        } else if (value instanceof List) {
        	if (isKey) {
        		throw new DataException("Unable to serialise non-primitive keyed map values");
        	}
        	
        	generator.writeStartArray();
        	
        	final List<Object> arr = (List<Object>) value;
        	
        	for (Object v : arr) {
        		writeValue(v, generator);
        	}
        	
        	generator.writeEndArray();
        } else if (value instanceof Map) {
        	if (isKey) {
        		throw new DataException("Unable to serialise non-primitive keyed map values");
        	}
        	
        	generator.writeStartObject();
        	
        	final Map<Object, Object> map = (Map<Object, Object>) value;
        	
        	for (Entry<Object, Object> entry : map.entrySet()) {
        		writeValue(entry.getKey(), generator, true);
        		writeValue(entry.getValue(), generator);
        	}
        	
        	generator.writeEndObject();
        } else {
            throw new DataException("Encountered unsupported value type: " + value.getClass());
		}
	}
}
