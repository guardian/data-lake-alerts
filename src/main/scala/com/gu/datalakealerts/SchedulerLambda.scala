package com.gu.datalakealerts

import com.amazonaws.services.lambda.runtime.Context
import org.slf4j.{ Logger, LoggerFactory }

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
      logger.info(s"The worker lambda will not be invoked as dryRun was set to true. The monitoring events which would have been scheduled are: $allMonitoringEvents")
    } else {
      allMonitoringEvents.map { monitoringEvent =>
        logger.info(s"Invoking worker lambda for monitoring event: $monitoringEvent")
        InvokeWorker.run(monitoringEvent, env.stage)
      }
    }
  }

}

object TestScheduler {
  def main(args: Array[String]): Unit = {
    SchedulerLambda.process(SchedulerEnv(), dryRun = true)
  }
}
