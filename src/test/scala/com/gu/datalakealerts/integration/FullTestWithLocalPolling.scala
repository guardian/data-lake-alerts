package com.gu.datalakealerts.integration

import com.gu.datalakealerts.EventModels.MonitoringEvent
import com.gu.datalakealerts.{ Athena, Features, Platforms, SchedulerEnv, SchedulerLambda, WorkerLambda }
import com.gu.datalakealerts.WorkerLambda.logger

// Requires Ophan (Athena & Glue) and Dev Playground (Developer) Janus credentials
object FullTestWithLocalPolling {

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      logger.error("Please provide a featureId and a platformId e.g. sbt \"runMain com.gu.datalakealerts.RunAndQueueQuery friction_screen android\"")
    } else {
      val monitoringEvent = MonitoringEvent(Features.featureToMonitor(args(0)), Platforms.platformToMonitor(args(1)))
      SchedulerLambda.startQuery(SchedulerEnv(), monitoringEvent).map { executionId =>
        LocalPolling.waitForQueryToComplete(executionId)
        WorkerLambda.analyseQueryResults(monitoringEvent.feature, monitoringEvent.platform, executionId)
      }
    }
  }

}

object LocalPolling {

  def waitForQueryToComplete(queryExecutionId: String, shouldStopPolling: Boolean = false): Unit = {
    if (shouldStopPolling) {
      logger.info(s"Query $queryExecutionId is now complete")
    } else {
      logger.info(s"Query $queryExecutionId is still running, will poll again in 1 second")
      Thread.sleep(1000)
      val shouldStopPolling = {
        val status = Athena.retrieveQueryStatus(queryExecutionId)
        logger.info(s"Query $queryExecutionId status is: $status")
        status == "SUCCEEDED" || status == "FAILED" || status == "CANCELLED"
      }
      waitForQueryToComplete(queryExecutionId, shouldStopPolling)
    }
  }

}