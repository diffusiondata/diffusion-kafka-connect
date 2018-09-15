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
package com.pushtechnology.connect.diffusion.data;

import static org.apache.kafka.connect.data.Values.inferSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.DataException;

import com.pushtechnology.diffusion.datatype.json.JSON;
import com.pushtechnology.repackaged.jackson.core.JsonToken;
import com.pushtechnology.repackaged.jackson.dataformat.cbor.CBORFactory;
import com.pushtechnology.repackaged.jackson.dataformat.cbor.CBORParser;

/**
 * Converter for translating between CBOR-Encoded {@link JSON} objects and Kafka-compatible value/schema pairs. 
 */
public class JSONToSchemaValue {
	private static final CBORFactory factory = new CBORFactory();

	/**
	 * Parse a JSON instance and return a schema/value pair.
	 * <P>
	 * The returned schema may be null if an appropriate schema cannot be inferred. This is
	 * most likely in Arrays or Maps with mixed value types. Primitive values will be 
	 * associated with the closest matching Schema; for Integers, this will either be
	 * {@link Schema.INT32_SCHEMA} or {@link Schema.INT64_SCHEMA}, while floating
	 * point values will be returned as {@link Schema.FLOAT64_SCHEMA}.
	 * <P>
	 * This method assumes that the provided JSON encapsulates a well-formed CBOR 
	 * backing array. Since this is used to translate data directly from Diffusion,
	 * which can perform its own input validation, it is encumbent on the user to
	 * ensure that values are able to be parsed correctly. Any parsing errors will
	 * result in a {@link DataException} being thrown. 
	 * 
	 * @param json The JSON instance to parse into a native value / schema pair
	 * @throws DataException if parsing fails
	 * @return The (potentially null) schema and native value
	 */
	public static SchemaAndValue valueFromJson(JSON json) {
		final Object value;
		
		try {
			CBORParser parser = factory.createParser(json.toByteArray());
			parser.nextToken();
			
			value = parse(parser);
		} catch (Exception e) {
			throw new DataException("Unable to parse JSON value", e);
		}
		
		final Schema schema = inferSchema(value);
		
		return new SchemaAndValue(schema, value);
	}
	
	private static Object parse(CBORParser parser) throws Exception {
		switch (parser.currentToken()) {
		case VALUE_NULL:
			return null;
		case VALUE_TRUE:
			return true;
		case VALUE_FALSE:
			return false;
		case VALUE_STRING:
			return parser.getValueAsString();
		case FIELD_NAME:
			return parser.getCurrentName();
		case VALUE_NUMBER_INT:
		case VALUE_NUMBER_FLOAT:
			switch (parser.getNumberType()) {
			case INT:
				return parser.getValueAsInt();
			case LONG:
				return parser.getValueAsLong();
			case BIG_INTEGER:
				return parser.getBigIntegerValue();
			case BIG_DECIMAL:
				return parser.getDecimalValue();
			case FLOAT:
				return parser.getFloatValue();
			case DOUBLE:
				return parser.getValueAsDouble();
			}
		case START_ARRAY:
			List<Object> arr = new ArrayList<>();
			
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				arr.add(parse(parser));
			}
			
			return arr;
		case START_OBJECT:
			Map<Object, Object> map = new HashMap<>();
			
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				Object field = parse(parser);
				parser.nextToken();
				Object value = parse(parser);
				
				map.put(field, value);
			}
			
			return map;
		default:
			throw new DataException("Unknown CBOR Token: " + parser.currentToken());
		}
	}
}
