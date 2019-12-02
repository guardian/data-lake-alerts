package com.gu.datalakealerts.integration

import com.gu.datalakealerts.EventModels.MonitoringEvent
import com.gu.datalakealerts.{ Features, Platforms, SchedulerEnv, SchedulerLambda }
import com.gu.datalakealerts.SchedulerLambda.logger

// Does not require Janus credentials
object ConfirmTaskWillBeScheduled {
  def main(args: Array[String]): Unit = {
    SchedulerLambda.process(SchedulerEnv(), dryRun = true)
  }
}

// Requires Ophan (Developer) Janus credentials
object ScheduleAndQueueSingleQuery {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      logger.error("Please provide a featureId and a platformId e.g. sbt \"test:runMain com.gu.datalakealerts.integration.ScheduleAndQueueSingleQuery friction_screen android\"")
    } else {
      val env = SchedulerEnv(app = "data-lake-alerts", stack = "stack", stage = "CODE") // Run with code env to send an event to a real SQS queue
      val event = MonitoringEvent(Features.featureToMonitor(args(0)), Platforms.platformToMonitor(args(1)))
      SchedulerLambda.process(env, dryRun = false, List(event))
    }
  }
}

// Requires Ophan (Developer) Janus credentials
object ScheduleAllQueries {
  def main(args: Array[String]): Unit = {
    val env = SchedulerEnv(app = "data-lake-alerts", stack = "stack", stage = "CODE") // Run with code env to send an event to a real SQS queue
    SchedulerLambda.process(env, dryRun = false, SchedulerLambda.allMonitoringEvents)
  }
}
