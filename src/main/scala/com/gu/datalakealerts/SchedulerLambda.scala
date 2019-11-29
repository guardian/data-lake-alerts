package com.gu.datalakealerts

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.datalakealerts.EventModels.{ MonitoringEvent, MonitoringEventWithQueryInfo }
import org.slf4j.{ Logger, LoggerFactory }

import scala.util.{ Failure, Success, Try }

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

  def handler(context: Context): Unit = {
    val env = SchedulerEnv()
    logger.info(s"Starting $env")
    process(env)
  }

  def startQuery(env: SchedulerEnv, event: MonitoringEvent): Try[String] = Try {
    logger.info(s"Starting monitoring for ${event.feature} on ${event.platform}")
    val monitoringQuery = event.feature.monitoringQuery(event.platform)
    logger.info(s"Query will be:\n ${monitoringQuery.query}")
    Athena.startQuery(monitoringQuery).getQueryExecutionId
  }

  def queueQuery(env: SchedulerEnv, event: MonitoringEvent, queryExecutionId: String): Try[SendMessageResult] = Try {
    val eventWithQueryInfo = MonitoringEventWithQueryInfo(event, queryExecutionId)
    val queueName = Sqs.queueName(env.app, env.stage)
    logger.info(s"Adding query id $queryExecutionId to the $queueName queue so that the results can be checked later...")
    Sqs.enqueueRunningQuery(eventWithQueryInfo, queueName, 30)
  }

  val allMonitoringEvents: List[MonitoringEvent] = Features.allFeaturesWithMonitoring.flatMap {
    feature => feature.platformsToMonitor.map(MonitoringEvent(feature, _))
  }

  def process(env: SchedulerEnv, dryRun: Boolean = false, eventsToMonitor: List[MonitoringEvent] = allMonitoringEvents): Unit = {
    logger.info(s"Running Data Lake Alerts scheduler...")
    if (dryRun) {
      logger.info(s"No queries will be started as dryRun was set to true. The monitoring events which would have been scheduled are: $eventsToMonitor")
    } else {
      logger.info(s"Attempting to start queries for the following monitoring events: $eventsToMonitor")
      eventsToMonitor.map { event =>
        val attempt = startQuery(env, event).flatMap(executionId => queueQuery(env, event, executionId))
        attempt match {
          case Success(result) => logger.info(s"Successfully started query and added it to SQS: $result")
          case Failure(exception) => logger.error(s"Failed to start or queue query due to: $exception")
        }
      }
    }
  }

}
