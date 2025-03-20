package com.diffusiondata.connect.diffusion.util;

import static com.diffusiondata.connect.diffusion.util.TopicPathMapping.pathFromRecord;
import static com.diffusiondata.connect.diffusion.util.TopicPathMapping.pathFromTopic;
import static com.diffusiondata.connect.diffusion.SinkRecordBuilder.sinkRecord;
import static org.apache.kafka.connect.data.SchemaBuilder.string;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TopicPathMapping}.
 */
public class TopicPathMappingTest {

	@Test
	public void canDeriveFromTopicPathNoOp() {
		String topicPath = "foo/bar/baz";
		String pattern = "foo";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("foo", result);
	}

	@Test
	public void canDeriveFromTopicPathReplaceSlash() {
		String topicPath = "foo/bar/baz";
		String pattern = "foo/bar/baz";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("foo_bar_baz", result);
	}

	@Test
	public void canDeriveFromTopicPathWithTopicReplace() {
		String topicPath = "foo/bar/baz";
		String pattern = "${topic}";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("foo_bar_baz", result);
	}

	@Test
	public void canDeriveFromTopicPathWithTopicAppend() {
		String topicPath = "foo/bar/baz";
		String pattern = "root/${topic}";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("root_foo_bar_baz", result);
	}

	@Test
	public void canDeriveFromTopicPathWithTopicPrepend() {
		String topicPath = "foo/bar/baz";
		String pattern = "${topic}/suffix";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("foo_bar_baz_suffix", result);
	}

	@Test
	public void canDeriveFromTopicPathWithTopicInsert() {
		String topicPath = "foo/bar/baz";
		String pattern = "a/${topic}/b";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("a_foo_bar_baz_b", result);
	}

	@Test
	public void canDeriveFromTopicPathWithUnknownToken() {
		String topicPath = "foo/bar/baz";
		String pattern = "a/b/${foo.bar}";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("a_b_${foo.bar}", result);
	}

	@Test
	public void canDeriveFromTopicPathWithKnownAndUnknownToken() {
		String topicPath = "foo/bar/baz";
		String pattern = "a/b/${foo.bar}/${topic}";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("a_b_${foo.bar}_foo_bar_baz", result);
	}

	@Test
	public void canDeriveFromTopicPathWithInapplicableToken() {
		String topicPath = "foo/bar/baz";
		String pattern = "a/b/${key.version}";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("a_b_${key.version}", result);
	}

	@Test
	public void canDeriveFromTopicPathWithRepeatedToken() {
		String topicPath = "foo/bar/baz";
		String pattern = "${topic}${topic}";

		String result = pathFromTopic(pattern, topicPath);

		assertEquals("foo_bar_bazfoo_bar_baz", result);
	}

	@Test
	public void canDeriveFromRecordNoOp() {
		SinkRecord record = sinkRecord().build();
		String pattern = "foo/bar/baz";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, pattern);
	}

	@Test
	public void canDeriveFromRecordWithTopic() {
		SinkRecord record = sinkRecord().topic("kafka").build();
		String pattern = "foo/bar/${topic}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/kafka");
	}

	@Test
	public void canDeriveFromRecordWithKey() {
		SinkRecord record = sinkRecord().key(string(), "key").build();
		String pattern = "foo/bar/${key}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/key");
	}

	@Test
	public void canDeriveFromRecordWithKeyVersion() {
		SinkRecord record = sinkRecord().key(string().version(123), "key").build();
		String pattern = "foo/bar/${key.version}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/123");
	}

	@Test
	public void canDeriveFromRecordWithKeyAndVersion() {
		SinkRecord record = sinkRecord().key(string().version(123), "key").build();
		String pattern = "foo/bar/${key.version}/${key}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/123/key");
	}

	@Test
	public void canDeriveFromRecordWithKeyNullSchema() {
		SinkRecord record = sinkRecord().key(null, "key").build();
		String pattern = "foo/bar/${key}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/key");
	}

	@Test
	public void canDeriveFromRecordWithKeyVersionNullSchema() {
		SinkRecord record = sinkRecord().key(null, "key").build();
		String pattern = "foo/bar/${key.version}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/${key.version}");
	}

	@Test
	public void canDeriveFromRecordWithKeyVersionNullVersion() {
		SinkRecord record = sinkRecord().key(string().version(null), "key").build();
		String pattern = "foo/bar/${key.version}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/${key.version}");
	}

	@Test
	public void canDeriveFromRecordWithKeyAndVersionNullSchema() {
		SinkRecord record = sinkRecord().key(null, "key").build();
		String pattern = "foo/bar/${key}/${key.version}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/key/${key.version}");
	}

	@Test
	public void canDeriveFromRecordWithValueVersion() {
		SinkRecord record = sinkRecord().val(string().version(123), "val").build();
		String pattern = "foo/bar/${value.version}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/123");
	}

	@Test
	public void canDeriveFromRecordWithValueVersionNoSchema() {
		SinkRecord record = sinkRecord().val(null, "val").build();
		String pattern = "foo/bar/${value.version}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/${value.version}");
	}

	@Test
	public void canDeriveFromRecordWithValueVersionNoVersion() {
		SinkRecord record = sinkRecord().val(string().version(null), "val").build();
		String pattern = "foo/bar/${value.version}";

		String result = pathFromRecord(pattern, record);

		assertEquals(result, "foo/bar/${value.version}");
	}
}


