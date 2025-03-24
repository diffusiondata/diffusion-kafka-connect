### Introduction

The [Diffusion Kafka Connector](https://github.com/diffusiondata/diffusion-kafka-connect) is a Kafka connector application 
that can be used to integrate Kafka with Diffusion server. Source and sink connectors are provided to publish 
messages from [Kafka](http://kafka.apache.org) topics to [Diffusion](https://www.diffusiondata.com) topics and vice versa. 

Connecting a Diffusion server enables real-time streaming of data stored in Kafka to endpoints like web browsers, mobile apps, and IoT devices, reliably and at scale.

The adapter is [verified](https://www.confluent.io/hub/push/diffusion-connector) by the Confluent Verified Integrations Program. It is compatible with both on prem Diffusion and Diffusion Cloud, versions 6.9 and above.

### Building

These instructions assume you are using [Maven](https://maven.apache.org/).

1.  Clone the repository:

    `git clone https://github.com/pushtechnology/diffusion-kafka-connect`

2.  Make the jar that contains the connector:

    `mvn clean package`

The resulting jar is at target/diffusion-kafka-connector.jar

### Pre-Running Steps

1.  Set up an instance of [Diffusion](https://www.diffusiondata.com/developers/release/latest/) or [Diffusion Cloud](https://www.diffusiondata.com/developers/cloud/latest/) that should be accessible from the machine on which you are running Kafka.

2.  Ensure that your instance of Diffusion can authenticate the principal/password pair
    that this connector will be configured with. If you intend to run the sink connector,
    ensure that this principal has sufficient permissions to create topics and publish
    values under paths that will be mapped from Kafka.

### Running a Connector

1.  Copy the diffusion-kafka-connector.jar to whichever directory you have configured Kafka
    to load plugins from.

2.  If running this connector within Confluent Platform, use the dashboard to 
    create a new sink/source connector. The dashboard will provide a configuration
    UI that contains all required fields.
    
    > NOTE: If using the [confluent docker compose file](https://github.com/confluentinc/cp-all-in-one/blob/7.9.0-post/cp-all-in-one/docker-compose.yml), 
the path where the plugin (diffusion-kafka-connector.jar) is available can be mounted in the volume of the `connect` docker container in the path configured for connectors via `CONNECT_PLUGIN_PATH` var, as follows :

    ```JSON
      ...
      ...
      connect:
        image: cnfldemos/cp-server-connect-datagen:0.6.4-7.6.0
        environment:
          ...
          CONNECT_PLUGIN_PATH: "/usr/share/java,/usr/share/confluent-hub-components"
        volumes:
          # The diffusion-kafka-connector.jar would be in the "/local/kafka/connectPlugin/plugins" path
          - /local/kafka/connectPlugin/plugins:/usr/share/confluent-hub-components
    ```
    
3.  If you are running this connector against vanilla Kafka, create a configuration
	file for the Diffusion Kafka connector and copy it to the place where you will run 
	Kafka connect. The configuration should set up the proper Kafka and Diffusion 
	topic patterns, as well as connection and authentication details for Diffusion.
    Sample configuration files for the source and sink connectors are provided
    at configs/.

### DiffusionConnector Configs

In addition to the configs supplied by the Kafka Connect API, the 
adapter supports the following configs:

#### Common Connector config

| Config | Value Range | Default | Description |
|--------|-------------|---------|-------------|
| diffusion.username | String | REQUIRED (No default) | The name of the principal with which to authenticate with Diffusion. |
| diffusion.password | String | REQUIRED (No default) | The password with which to authenticate with Diffusion. |
| diffusion.host | String | REQUIRED (No default) | The hostname with which to connect to Diffusion. |
| diffusion.port | String | REQUIRED (No default) | The port against which to connect to Diffusion. |

#### Source Connector

| Config | Value Range | Default | Description |
|--------|-------------|---------|-------------|
| diffusion.selector | String | REQUIRED (No default) | The topic selector used to subscribe to source topics in Diffusion. May be any valid topic selector, e.g. "?topics/source/.*/json-format". |
| diffusion.poll.interval | Int | 1000 | The back-off interval to wait (in milliseconds) when there are no messages to return to Kafka. |
| diffusion.poll.size | Int | 128 | The maximum number of messages to batch when pushing to Kafka. | 
| kafka.topic | String | REQUIRED (No default) | The pattern to be used when mapping Diffusion messages to destination Kafka topics. |

#### Sink Connector

| Config | Value Range | Default | Description |
|--------|-------------|---------|-------------|
| diffusion.destination | String | REQUIRED (No default) | The pattern to be used when mapping Kafka messages to destination Diffusion topics.  |


### Destination Patterns

When mapping between Diffusion and Kafka, the configured destination patterns will be evaluated against the available message/metadata and 
used to resolve a distinct topic against which to publish. This allows for messages received on a single topic to be delivered to multiple
destination topics based on associated metadata. Patterns may contain one or more tokens, which will be replaced by values (if available) 
at runtime. If no mapping is required, then simply provide a concrete topic path to establish a direct 1:1 mapping.

When mapping paths for source connectors, invalid characters will be converted to the allowable set of Kafka topic characters (alphanumeric, `_`).
If the mapping contains a token referencing the inbound Diffusion topic path (which will likely contain `/` characters), these will be converted
to underscores automatically.

#### Available tokens
| Token | Connector Type | Associated value |
|-------|----------------|------------------|
| `${key}` | Sink | The String representation of a given Record's key. |
| `${key.version}` | Sink | The version of the given Record's key schema. |
| `${value.version}` | Sink | The version of the given Record's value schema. |
| `${topic}` | Sink and Source |  The topic of a given Kafka or Diffusion message. |

#### Examples - Source Connector
Assuming a JSON value received a topic of "foo/bar":

| Pattern | Result |
|---------|--------|
| `foo`   | `foo` |
| `${topic}` | `foo_bar` |
| `diffusion_${topic}` | `diffusion_foo_bar` |

#### Examples - Sink Connector
Assuming a SinkRecord with a topic of "bar", a key of "baz", a key schema version of "1.2" and a value schema version of 3.1:

| Pattern | Result |
|---------|--------|
| `foo`   | `foo` |
| `foo/${topic}` | `foo/bar` |
| `foo/${topic}/${key.version}` | `foo/bar/1.2` |
| `foo/${value.version}/${topic}` | `foo/3.1/bar` |

### Schema Support and Data Model

The adapter will send and receive JSON values, with support for primitive data types 
(e.g. integer, float, or string types), as well as Arrays, Maps and Structs. 

The sink connector handles the conversion in the following way:

*   All values (primitive, Arrays, Maps, Structs) will be serialised to a 
	Diffusion-compatible JSON format
*   Messages published to Diffusion will be done so in an optimistic fashion,
    with topics created as necessary. Any topics created by the Source Connector
    will be of the JSON topic type. If a destination topic does not exist, and
    the source connector is unable to create it, an error will be raised and 
    the connector will be stopped. 
*   Maps that have non-string keys will result in an error, since JSON only
	allows for primitive-keyed maps/objects
*   Structs will be serialised according to their defined schema;
    Arrays and Maps will have their entries serialised in linear order. 

The source connector takes a similar approach in handling the conversion
from a Diffusion JSON message into a SourceRecord with a relevant Schema.

*   The connector can subscribe to JSON, string, int64 or float topic types.
*   The topic path that the Diffusion message was received on will be set
	as the key for Kafka, with an associated String schema.
*   The JSON body will be deserialised into a Kafka-appropriate type. Where
	possible, the Value schema will be inferred from the message value; in
	cases where this can not be done (e.g. nested Arrays or Maps) the schema
	will contain the top-level structural type
*   Structs cannot be synthesised, due to the lack of pre-defined schemas
	within the JSON payload. For this reason, a round-trip from Kafka to
	Diffusion and back would result in a struct becoming parsed as a Map
	with a String key schema and Object value schema.
	
### Delivery Model

The delivery guarantees of Kafka do not map directly to Diffusion's implicit delivery modes.
Furthermore, since Diffusion does not have the concept of user-specific message order or 
topic partitions - instead relying solely on a last-write-wins model per topic - parallelism
of connector tasks is difficult to achieve. The general behaviour should be understood as:

*   The Diffusion Kafka Adapter should have a single task for both Sink or Source. This is a result 
    of being unable to rationally distribute addressed topics across multiple tasks, given that 
    the semantics of Diffusion's topic selectors are resolved at runtime.
*   To parallelise operations, it is recommended to run multiple instances of the Diffusion Kafka Adapter
    with separate configurations to target subsets of source or destination topics.  
*   Message order is dependent on the upstream source. The source connector is guaranteed to deliver
    messages in-order for a given source topic, but is unable to provide Kafka with useful offsets
    since the Diffusion client does not have access to ordering data that exists outside the lifespan
    of a given source task.
*   The sink connector will commit offsets of messages on regular intervals when it is confirmed that
    they have been published successfully to Diffusion. It is possible for some offsets to not be 
    committed, despite being published, if the Diffusion connection is lost immediately after publication
    but before the Connect framework commits offsets.
 
### License

This adapter is available under the Apache License 2.0.
 
