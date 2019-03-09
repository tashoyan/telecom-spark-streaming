package com.github.tashoyan.telecom.predictor

import java.sql.Timestamp

import com.github.tashoyan.telecom.event.Event._
import com.github.tashoyan.telecom.event.FireAlarmUtil._
import com.github.tashoyan.telecom.event.{Alarm, Event}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.scala.{DataStream, _}
import org.apache.flink.streaming.api.windowing.assigners.{EventTimeSessionWindows, SessionWindowTimeGapExtractor}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import scala.collection.mutable

/**
  * Fire predictor based on session windows.
  * <p>
  * A session window is created for each heat event.
  * If a smoke event occurs after a heat event,
  * and within the problem timeout interval,
  * then an alarm is created for this window.
  * <p>
  * <b>Deduplication</b>
  * <p>
  * We could partition events by (timestamp, sideId)
  * and use a tumbling window of a fixed size
  * to tka only the first event:
  * https://stackoverflow.com/a/35600175
  * However, in this case we already have the algorithm,
  * that takes the first smoke and the first heat events
  * within each session window.
  * Makes no sense to introduce a separate deduplication step.
  *
  * @param problemTimeoutMillis      Problem timeout interval in milliseconds.
  *                                  If a heat event is followed by a smoke event not later than after this timeout, then an alarm is generated.
  * @param eventOutOfOrdernessMillis Max time interval when events may come out of order.
  */
class SessionWindowFirePredictor(
    override val problemTimeoutMillis: Long,
    eventOutOfOrdernessMillis: Long
) extends FlinkFirePredictor {

  private val timestampAssigner = new BoundedOutOfOrdernessTimestampExtractor[Event](Time.milliseconds(eventOutOfOrdernessMillis)) {
    override def extractTimestamp(event: Event): Long =
      event.timestamp
  }

  private object TimeGapExtractor extends SessionWindowTimeGapExtractor[Event] {
    private val problemTimeoutMillis0 = problemTimeoutMillis

    override def extract(event: Event): Long =
      if (event.isHeat)
        problemTimeoutMillis0
      else
        1L
  }

  private type SortedEvents = mutable.SortedSet[Event]

  private object AlarmAggregator extends AggregateFunction[Event, SortedEvents, Option[Alarm]] {
    private val problemTimeoutMillis0 = problemTimeoutMillis

    override def createAccumulator(): SortedEvents = new mutable.TreeSet[Event]()

    override def add(event: Event, acc: SortedEvents): SortedEvents = acc += event

    override def merge(acc1: SortedEvents, acc2: SortedEvents): SortedEvents = acc1 ++= acc2

    override def getResult(acc: SortedEvents): Option[Alarm] = {
      for {
        smoke <- acc.find(_.isSmoke)
        heat <- acc.find(e => isInCausalRelationship(e, smoke, problemTimeoutMillis0) && e.isHeat)
      } yield Alarm(
        new Timestamp(smoke.timestamp),
        smoke.siteId,
        fireAlarmSeverity,
        s"Fire on site ${smoke.siteId}. First heat at ${new Timestamp(heat.timestamp)}."
      )
    }
  }

  private object WindowFunction extends ProcessWindowFunction[Option[Alarm], Alarm, Long, TimeWindow] {
    override def process(siteId: Long, context: Context, alarms: Iterable[Option[Alarm]], out: Collector[Alarm]): Unit =
      alarms.head.foreach(out.collect)
  }

  override def predictAlarms(events: DataStream[Event]): DataStream[Alarm] = {
    //TODO Here we assume that events are already deduplicated. Add deduplicator to the main class.
    val alarms = events
      .filter(e => isFireCandidate(e))
      .assignTimestampsAndWatermarks(timestampAssigner)
      .keyBy(_.siteId)
      .window(EventTimeSessionWindows.withDynamicGap(TimeGapExtractor))
      .trigger(EventTimeTrigger.create())
      .aggregate(
        AlarmAggregator,
        WindowFunction
      )
    alarms
  }

}
