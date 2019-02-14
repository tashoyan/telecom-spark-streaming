package com.github.tashoyan.telecom.predictor

import java.sql.Timestamp
import java.util.concurrent.TimeUnit

import com.github.tashoyan.telecom.event.Event
import org.apache.spark.sql.streaming.GroupState
import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSuite

class FireAlarmStateFunctionTest extends FunSuite with MockFactory {

  private val problemTimeoutMillis = TimeUnit.SECONDS.toMillis(10)

  private val siteId = 1L
  private val severity = "MAJOR"
  private val heatInfo = "Heat on site"
  private val smokeInfo = "Smoke on site"

  def heatEvent(timestamp: Timestamp): Event =
    Event(timestamp, siteId, severity, heatInfo)

  def smokeEvent(timestamp: Timestamp): Event =
    Event(timestamp, siteId, severity, smokeInfo)

  test("state exists [N] / state timed out [-] / heat [N] / smoke [N] / smoke-heat timeout [-]") {
    val state: GroupState[ProblemState] = mock[GroupState[ProblemState]]
    (state.exists _)
      .expects()
      .atLeastOnce()
      .returns(false)

    val events = Iterator.empty

    val alarmStateFunction = new FireAlarmStateFunction(problemTimeoutMillis)

    val alarms = alarmStateFunction.updateAlarmState(siteId, events, state)
    assert(alarms.isEmpty, "Expected none alarms")
  }

  test("state exists [N] / state timed out [-] / heat [Y] / smoke [N] / smoke-heat timeout [-]") {
    val heatTimestamp = new Timestamp(1000L)
    val events = Iterator(heatEvent(heatTimestamp))

    val state: GroupState[ProblemState] = mock[GroupState[ProblemState]]
    inSequence {
      (state.exists _)
        .expects()
        .atLeastOnce()
        .returns(false)
      inAnyOrder {
        (state.update _)
          .expects(ProblemState(siteId, heatTimestamp))
          .once()
        (state.setTimeoutTimestamp(_: Long))
          .expects(heatTimestamp.getTime + problemTimeoutMillis)
          .once()
      }
    }

    val alarmStateFunction = new FireAlarmStateFunction(problemTimeoutMillis)

    val alarms = alarmStateFunction.updateAlarmState(siteId, events, state)
    assert(alarms.isEmpty, "Expected none alarms")
  }

  test("state exists [N] / state timed out [-] / heat [N] / smoke [Y] / smoke-heat timeout [-]") {
    val smokeTimestamp = new Timestamp(1000L)
    val events = Iterator(smokeEvent(smokeTimestamp))

    val state: GroupState[ProblemState] = mock[GroupState[ProblemState]]
    (state.exists _)
      .expects()
      .atLeastOnce()
      .returns(false)

    val alarmStateFunction = new FireAlarmStateFunction(problemTimeoutMillis)

    val alarms = alarmStateFunction.updateAlarmState(siteId, events, state)
    assert(alarms.isEmpty, "Expected none alarms")
  }

  test("state exists [N] / state timed out [-] / heat [Y] / smoke [Y] / smoke-heat timeout [N]") {
    val heatTimestamp = new Timestamp(1000L)
    val smokeTimestamp = new Timestamp(heatTimestamp.getTime + problemTimeoutMillis / 2)
    val events = Iterator(heatEvent(heatTimestamp), smokeEvent(smokeTimestamp))

    val state: GroupState[ProblemState] = mock[GroupState[ProblemState]]
    (state.exists _)
      .expects()
      .atLeastOnce()
      .returns(false)
    val alarmStateFunction = new FireAlarmStateFunction(problemTimeoutMillis)

    val alarms = alarmStateFunction.updateAlarmState(siteId, events, state).toSeq
    assert(alarms.length === 1, "Expected 1 alarm")
    val alarm = alarms.head
    assert(alarm.timestamp === smokeTimestamp)
    assert(alarm.siteId === siteId)
    assert(alarm.info.toLowerCase.contains("fire "))
  }

  test("state exists [N] / state timed out [-] / heat [Y] / smoke [Y] / smoke-heat timeout [Y]") {
    val heatTimestamp = new Timestamp(1000L)
    val smokeTimestamp = new Timestamp(heatTimestamp.getTime + problemTimeoutMillis * 2)
    val events = Iterator(heatEvent(heatTimestamp), smokeEvent(smokeTimestamp))

    val state: GroupState[ProblemState] = mock[GroupState[ProblemState]]
    (state.exists _)
      .expects()
      .atLeastOnce()
      .returns(false)
    val alarmStateFunction = new FireAlarmStateFunction(problemTimeoutMillis)

    val alarms = alarmStateFunction.updateAlarmState(siteId, events, state).toSeq
    assert(alarms.isEmpty, "Expected none alarms")
  }

}
