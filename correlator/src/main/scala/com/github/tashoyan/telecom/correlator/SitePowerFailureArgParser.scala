package com.github.tashoyan.telecom.correlator

import com.github.tashoyan.telecom.correlator.SitePowerFailureConfig._
import scopt.OptionParser

trait SitePowerFailureArgParser {

  val parser: OptionParser[SitePowerFailureConfig] = new OptionParser[SitePowerFailureConfig]("site-power-failure") {
    head("Site Power Failure")

    opt[String]("schema-file")
      .required()
      .valueName("<path>")
      .action((value, conf) => conf.copy(schemaFile = value))
      .validate { value =>
        if (value.isEmpty) failure("Path to events schema file must not be empty string")
        else success
      }
      .text("Full path to the Parquet location having event's schema")

    opt[String]("kafka-brokers")
      .required()
      .valueName("host1:9092[,host2:9092,...]")
      .action((value, conf) => conf.copy(kafkaBrokers = value))
      .validate { value =>
        if (value.isEmpty) failure("Kafka brokers list must not be empty string")
        else success
      }
      .text("List of comma-separated Kafka brokers")

    opt[String]("event-kafka-topic")
      .required()
      .valueName("<topic>")
      .action((value, conf) => conf.copy(eventKafkaTopic = value))
      .validate { value =>
        if (value.isEmpty) failure("Events Kafka topic must not be empty string")
        else success
      }
      .text("Kafka topic to receive events from")

    opt[String]("checkpoint-dir")
      .optional()
      .valueName("<path>")
      .action((value, conf) => conf.copy(checkpointDir = value))
      .validate { value =>
        if (value.isEmpty) failure("Checkpoint directory must not be empty string")
        else success
      }
      .text(s"Checkpoint directory on HDFS. Default is $defaultCheckpointDir.")

    opt[String]("problem-kafka-topic")
      .required()
      .valueName("<path>")
      .action((value, conf) => conf.copy(problemKafkaTopic = value))
      .validate { value =>
        if (value.isEmpty) failure("Problems Kafka topic must not be empty string")
        else success
      }
      .text("Kafka topic to send problems to")

    help("help")
    version("version")
  }

}
