package com.gu.datalakealerts

import com.amazonaws.services.lambda.runtime.Context
import org.slf4j.{ Logger, LoggerFactory }

import scala.util.Try

case class SchedulerEnv(app: String, stack: String, stage: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object SchedulerEnv {
  def apply(): SchedulerEnv = SchedulerEnv(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"))
}

object SchedulerLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handler(lambdaInput: LambdaInput, context: Context): Unit = {
    val env = SchedulerEnv()
    logger.info(s"Starting $env")
    process(env)
  }

  def process(env: SchedulerEnv, dryRun: Boolean = false): Unit = {
    logger.info(s"Running Data Lake Alerts scheduler...")
    val allMonitoringEvents = Features.allFeaturesWithMonitoring.flatMap {
      feature => feature.platformsToMonitor.map(MonitoringEvent(feature, _))
    }
    if (dryRun) {
      logger.info(s"No queries will be started as dryRun was set to true. The monitoring events which would have been scheduled are: $allMonitoringEvents")
    } else {
      logger.info(s"Attempting to start queries for the following monitoring events: $allMonitoringEvents")
      allMonitoringEvents.map { event =>
        Try {
          logger.info(s"Starting monitoring for ${event.feature} on ${event.platform}")
          val monitoringQuery = event.feature.monitoringQuery(event.platform)
          logger.info(s"Query will be:\n ${monitoringQuery.query}")
          val queryExecutionId = Athena.startQuery(monitoringQuery).getQueryExecutionId
          val eventWithQueryInfo = MonitoringEventWithQueryInfo(event, queryExecutionId)
          Sqs.enqueueRunningQuery(eventWithQueryInfo, Sqs.queueName(env.app, env.stage), 30)
        }
      }
    }
  }

}

object TestScheduler {
  def main(args: Array[String]): Unit = {
    SchedulerLambda.process(SchedulerEnv(), dryRun = true)
  }
}
