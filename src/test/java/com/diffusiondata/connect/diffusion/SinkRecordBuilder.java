package com.diffusiondata.connect.diffusion;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.sink.SinkRecord;

/**
 * Utility class for constructing SinkRecord instances with sane defaults.
 */
public class SinkRecordBuilder {
	private String topic = "topic";
	private int partition = 1;
	private Schema keySchema = SchemaBuilder.string();
	private Object key = "foo";
	private Schema valSchema = SchemaBuilder.string();
	private Object val = "foo";
	private long offset = -1;
	
	public static SinkRecordBuilder sinkRecord() {
		return new SinkRecordBuilder();
	}
	
	public SinkRecordBuilder topic(String topic) {
		this.topic = topic;
		return this;
	}
	
	public SinkRecordBuilder partition(int partition) {
		this.partition = partition;
		return this;
	}
	
	public SinkRecordBuilder key(Schema schema, Object key) {
		this.keySchema = schema;
		this.key = key;
		return this;
	}
	
	public SinkRecordBuilder val(Schema schema, Object val) {
		this.valSchema = schema;
		this.val = val;
		return this;
	}
	
	public SinkRecordBuilder offset(long offset) {
		this.offset = offset;
		return this;
	}
	
	public SinkRecord build() {
		return new SinkRecord(topic, partition, keySchema, key, valSchema, val, offset);
	}
}