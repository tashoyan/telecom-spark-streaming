package com.github.tashoyan.telecom.predictor

import com.github.tashoyan.telecom.event._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.OutputMode

object SparkPredictorMain extends SparkPredictorArgParser {

  def main(args: Array[String]): Unit = {
    parser.parse(args, SparkPredictorConfig()) match {
      case Some(config) => doMain(config)
      case None => sys.exit(1)
    }
  }

  private def doMain(config: SparkPredictorConfig): Unit = {
    println(config)

    implicit val spark: SparkSession = SparkSession.builder()
      .getOrCreate()
    spark.sparkContext
      .setLogLevel("WARN")

    val eventReceiver = new KafkaEventReceiver(config.kafkaBrokers, config.kafkaEventTopic)
    val eventDeduplicator = new DefaultEventDeduplicator(config.watermarkIntervalMillis)
    val kafkaEvents = eventReceiver.receiveEvents()
    val events = eventDeduplicator.deduplicateEvents(kafkaEvents)

    val firePredictor = new GroupStateFirePredictor(config.problemTimeoutMillis, config.watermarkIntervalMillis)
    val alarms = firePredictor.predictAlarms(events)

    val alarmSender = new KafkaStreamingSender[Alarm](
      config.kafkaBrokers,
      config.kafkaAlarmTopic,
      Alarm.objectIdColumn,
      config.checkpointDir,
      OutputMode.Update()
    )
    val query = alarmSender.sendingQuery(alarms)
    query.awaitTermination()
  }

}
