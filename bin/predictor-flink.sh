#!/bin/sh

set -o nounset
set -o errexit
set -o pipefail

kafka_brokers="$(hostname):9092"
kafka_event_topic="events"
kafka_alarm_topic="alarms"
watermark_interval_millis=$((10 * 60 * 1000))
problem_timeout_millis=$((20 * 1000))

jar_file="$(ls predictor-flink/target/predictor-flink-*.jar | grep -vi javadoc || true)"
if test -z "$jar_file"
then
    echo "Cannot find the application jar file. Is the project built? Exiting."
    exit 1
fi

export HADOOP_CLASSPATH=$(hadoop classpath)

app_name="$(basename $0)"
flink run \
-ynm "$app_name" \
-m yarn-cluster \
-p 10 \
-ys 2 \
-yjm 1G \
-ytm 2G \
-c com.github.tashoyan.telecom.predictor.FlinkPredictorMain \
"$jar_file" \
--kafka-brokers "$kafka_brokers" \
--kafka-event-topic "$kafka_event_topic" \
--kafka-alarm-topic "$kafka_alarm_topic" \
--watermark-interval-millis "$watermark_interval_millis" \
--problem-timeout-millis "$problem_timeout_millis"