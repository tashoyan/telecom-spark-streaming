package com.github.tashoyan.telecom.event

object KafkaEventStream {

  /**
    * Data set column used for keys when reading/writing data from/to Kafka topics.
    * This value is specified in the Structured Streaming + Kafka Integration Guide.
    */
  val keyColumn = "key"

  /**
    * Data set column used for values when reading/writing data from/to Kafka topics.
    * This value is specified in the Structured Streaming + Kafka Integration Guide.
    */
  val valueColumn = "value"

  /**
    * Default value of the timeout to poll data from Kafka topics.
    * This value is specified in the Structured Streaming + Kafka Integration Guide.
    */
  val defaultPollTimeoutMs: Long = 512

}
