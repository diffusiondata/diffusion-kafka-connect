# Diffusion Kafka Connector

_**Diffusion** is an Intelligent Data Platform that includes everything you need to consume,
enrich, and deliver event-data to power your event-driven real-time applications._

The [Diffusion Kafka Connector](https://github.com/diffusiondata/diffusion-kafka-connect) is a Kafka connector application 
that can be used to integrate Kafka with Diffusion server. Source and sink connectors are provided to publish 
messages from [Kafka](http://kafka.apache.org) topics to [Diffusion](https://www.diffusiondata.com) topics and vice versa. 

Connecting a Diffusion server enables real-time streaming of data stored in Kafka 
to edge clients like web browsers, mobile apps, and IoT devices, reliably and at scale.

The connector is [verified](https://www.confluent.io/hub/push/diffusion-connector) by the Confluent Verified Integrations Program. It is compatible with both on-prem Diffusion and Diffusion Cloud, versions 6.9 and above.

## Building

### Prerequisites
- Maven (minimum 3.8)
- Java (minimum java 11)
1.  Clone the repository:

    `git clone https://github.com/pushtechnology/diffusion-kafka-connect`

2.  Make the jar that contains the connector:

    `mvn clean package`

The resulting jar is at `target/diffusion-kafka-connector.jar`

This will also create `target/components/packages/DiffusionData-Diffusion-Connector-1.0.0.zip` file.
This `.zip` file can be used to add the Diffusion Kafka connector in the Confluent platform on-prem 
or in Confluent cloud.

_Note_: This version of the connector uses Kafka connect API version: 3.9.0.

## Pre-requisite to run the connector

1. Java environment (minimum Java 11) 

2. An instance of the Diffusion server

3. An instance of the Kafka server configured to run Kafka connectors

## Setting up the Diffusion server
The Diffusion server can be run locally with an installer or as a Docker image. 
See [here](https://docs.diffusiondata.com/docs/quickstartguide/onprem/getting-started/install.html) 
for more details. 

_Note_: [A docker compose file](./docker-compose.yml) is provided that also includes a 
Diffusion image together with other components required to start the Diffusion 
Kafka connector.

DiffusionData also provides a fully managed 
[SaaS cloud offering](https://docs.diffusiondata.com/docs/quickstartguide/cloud/getting-started/account.html).

To manage the features of the server including Diffusion topics, [Diffusion Management Console](https://docs.diffusiondata.com/docs/quickstartguide/onprem/console/console-overview.html) can be used. 

When running the server locally, a default user is available with the principal
set to `admin` and the password set to `password`. This user has administrator-level
permissions. These credentials can be used in the connector configuration to
establish a connection with the server. A sample configuration is provided
below.

When using the Diffusion server in the cloud, a user (principal) must be created
with the appropriate permissions to authenticate the connector. If you intend to
run the sink connector, ensure that this principal has sufficient permissions to
create topics and publish values under paths that will be mapped from Kafka.

## Running the Connector

## Locally with Confluent stack

To run the connector locally, run the provided sample [docker-compose.yml](docker-compose.yml) file 
to start up Kafka, Diffusion, and other required components. This docker file has 
been created using the one provided by [Confluent](https://github.com/confluentinc/cp-all-in-one/blob/7.9.0-post/cp-all-in-one/docker-compose.yml).

> **_NOTE:_** The `diffusion-kafka-connector.jar` file created during the build should be 
 added into the folder specified in the volume mount path for the `connect` containers.

The connector instances can then be added either via the control centre or by using the Kafka connect REST API. 

### Via Control Centre

Once the stack is up and running, the Confluent control centre can be accessed 
via `http://localhost:9021/clusters`. An instance of the Diffusion source and sink 
connector can be added in the `Connect` tab of the control centre.

> **_NOTE:_** If *DiffusionSinkConnector* and *DiffusionSourceConnector* are not listed in the list of available connectors, it's likely because of invalid mount of the connector folder or missing JAR file.

A connector instance can be added using the provided UI or by uploading the connector config file. The sample config files 
are provided in the `config` folder, that are also specified below.

### Via REST API
It is also possible to add and manage connector instances using REST APIs. See [here](https://docs.confluent.io/platform/current/connect/references/restapi.html) for details.

Here's the sample configuration to add a Sink connector via the REST API or in via the Control centre:

> **_NOTE:_** replace `wss://<diffusion_server_hostname>` with the appropriate Diffusion server URL.

```json
{
  "name": "DiffusionSinkConnector",
  "config": {
    "tasks.max": "1",
    "connector.class": "com.diffusiondata.connect.diffusion.sink.DiffusionSinkConnector",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "topics": "price",
    "diffusion.url": "wss://<diffusion_server_hostname>",
    "diffusion.username": "admin",
    "diffusion.password": "password",
    "diffusion.destination": "kafka/${topic}"
  }
}
```
Once the sink connector instance is added with the above configuration, when the 
Kafka topic `price` is updated, this is reflected in the Diffusion topic `kafka/price`. 
This can be viewed from the *Topics* tab in the [Diffusion Management Console](https://docs.diffusiondata.com/docs/latest/manual/html/administratorguide/systemmanagement/r_diffusion_monitoring_console.html).

Here's the sample configuration to add a Source connector the REST API or in via the Control centre:

```json
{
  "name": "DiffusionSourceConnector",
  "config": {
    "tasks.max": "1",
    "connector.class": "com.diffusiondata.connect.diffusion.source.DiffusionSourceConnector",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "kafka.topic": "diffusion.price",
    "diffusion.url": "wss://<diffusion_server_hostname>",
    "diffusion.username": "admin",
    "diffusion.password": "password",
    "diffusion.selector": "?source/kafka/.*"
  }
}
```

Once the source connector instance is added with the above configuration, when any 
Diffusion topic matching the `?source/kafka/.*` topic selector is updated, its 
value will be reflected in the `diffusion.price` kafka topic. A Diffusion topic matching 
that topic selector such as `source/kafka/price` can be created from the  *Topics* 
tab in the [Diffusion Management Console](https://docs.diffusiondata.com/docs/latest/manual/html/administratorguide/systemmanagement/r_diffusion_monitoring_console.html).

## In Confluent Cloud as Custom Connector 
The connector can be used in Confluent Cloud as a [Custom Connector](https://docs.confluent.io/cloud/current/connectors/bring-your-connector/overview.html).

The following steps expect that you have created a Confluent Cloud account and 
configured the cluster in one of the [supported regions](https://docs.confluent.io/cloud/current/connectors/bring-your-connector/custom-connector-fands.html#cc-byoc-regions.). 

1. Build the `.zip` folder as specified in the [build](#Building) section above.
2. In the Kafka cloud instance, Select: 
   > Environment > cluster > Connectors
3. Click "Add plugin"
4. Give the plugin a recognizable name, and set the Connector class to `com.diffusiondata.connect.diffusion.sink.DiffusionSinkConnector` for connector of *Sink* type or `com.diffusiondata.connect.diffusion.source.DiffusionSourceConnector` for connector of *Source* type. 
5. Upload the `.zip` file from the first step.
6. Add `diffusion.password` as a `Sensitive properties`.

You can then add an instance of the connector. 
1. Specify the API key. For this, you can use an existing key or create a new one.
2. Provide the JSON configuration for the connector. 

    #### Sample JSON configuration for the Source connector
    ```json
    {
    "name": "DiffusionSourceConnector",
    "tasks.max": "1",
    "connector.class": "com.diffusiondata.connect.diffusion.source.DiffusionSourceConnector",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "kafka.topic": "kafka",
    "diffusion.url": "wss://<diffusion_server_hostname>",
    "diffusion.username": "admin",
    "diffusion.password": "password",
    "diffusion.selector": "?source/kafka/.*"
    }
    ```

    #### Sample JSON configuration for the Sink connector
    ```json
    {
      "name": "DiffusionSinkConnector",
      "tasks.max": "1",
      "connector.class": "com.diffusiondata.connect.diffusion.sink.DiffusionSinkConnector",
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "org.apache.kafka.connect.json.JsonConverter",
      "value.converter.schemas.enable": "false",
      "topics": "kafka",
      "diffusion.url": "wss://<diffusion_server_hostname>",
      "diffusion.username": "admin",
      "diffusion.password": "password",
      "diffusion.destination": "kafka/${topic}/${key}"
    }
    ```   

3. In the `Connection Endpoints` section, specify the Diffusion server URL to be added in the allow-list.
4. After submitting the form, the connector instance will be active in a few minutes.

## DiffusionConnector Configs

In addition to the configs supplied by the Kafka Connect API, the 
connector supports the following configs:

#### Common Connector config

| Config             | Value Range | Default | Description                                                          |
|--------------------|-------------|---------|----------------------------------------------------------------------|
| diffusion.username | String | REQUIRED (No default) | The name of the principal with which to authenticate with Diffusion. |
| diffusion.password | String | REQUIRED (No default) | The password with which to authenticate with Diffusion.              |
| diffusion.url      | String | REQUIRED (No default) | The full URL with which to connect to Diffusion.                     |

#### Source Connector

| Config | Value Range | Default | Description                                                                                                                              |
|--------|-------------|---------|------------------------------------------------------------------------------------------------------------------------------------------|
| diffusion.selector | String | REQUIRED (No default) | The topic selector used to subscribe to source topics in Diffusion. Can be any valid topic selector, e.g. "?topics/source/.*/json-format". |
| diffusion.poll.interval | Int | 1000 | The back-off interval to wait (in milliseconds) when there are no messages to return to Kafka.                                           |
| diffusion.poll.size | Int | 128 | The maximum number of messages to batch when pushing to Kafka.                                                                           | 
| kafka.topic | String | REQUIRED (No default) | The pattern to be used when mapping Diffusion messages to destination Kafka topics.                                                      |

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
Assuming a JSON value received from a Diffusion topic of "foo/bar":

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

The connector will send and receive JSON values, with support for primitive data types 
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

*   The connector can subscribe to JSON, string, int64 or double topic types.
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

*   The Diffusion Kafka Connector should have a single task for both Sink or Source. This is a result 
    of being unable to rationally distribute addressed topics across multiple tasks, given that 
    the semantics of Diffusion's topic selectors are resolved at runtime.
*   To parallelise operations, it is recommended to run multiple instances of the Diffusion Kafka Connector
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

This connector is available under the Apache License 2.0.
