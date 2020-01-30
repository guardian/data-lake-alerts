package com.gu.datalakealerts

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.gu.anghammarad.{ AWS, Anghammarad }
import com.gu.anghammarad.models._
import com.gu.datalakealerts.EventModels.{ MonitoringEvent, MonitoringEventWithQueryInfo }
import com.gu.datalakealerts.Features.Feature
import com.gu.datalakealerts.WorkerLambda.logger
import com.gu.datalakealerts.Platforms.Platform
import com.gu.datalakealerts.apps.ResultHandler.ImpressionCounts
import org.slf4j.{ Logger, LoggerFactory }
import io.circe.parser.decode

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success, Try }

case class WorkerEnv(app: String, stack: String, stage: String, snsTopicForAlerts: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object WorkerEnv {

  // Only used when running locally
  lazy val ssmClient = AWSSimpleSystemsManagementClientBuilder
    .standard()
    .withCredentials(AwsCredentials.developerPlaygroundCredentials)
    .build()

  // Only used when running locally
  lazy val snsTopicFromParameterStore = {
    logger.info("Reading Anghammarad SNS topic from Parameter Store...")
    ssmClient.getParameter(
      new GetParameterRequest().withName("/DEV/data-lake-alerts/anghammarad-sns-topic")).getParameter.getValue
  }

  def apply(): WorkerEnv = WorkerEnv(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"),
    Option(System.getenv("SnsTopicForAlerts")).getOrElse(snsTopicFromParameterStore))
}

object Notifications {

  val env = WorkerEnv()

  def alert(feature: Feature, executionId: String, message: String, stackForProductionAlerts: Stack) = {

    val stack = env.stage match {
      case "PROD" => stackForProductionAlerts
      case _ => Stack("testing-alerts") // Alerts sent in DEV and CODE will go to the 'anghammarad.test.alerts' Google Group (to avoid spam)
    }

    val notificationAttempt = Anghammarad.notify(
      subject = s"Data Lake Monitoring | Check Failed for ${feature.id}",
      message = message,
      sourceSystem = "Data Lake Alerts",
      channel = Email,
      target = List(stack),
      actions = List(Action(
        cta = "View Query Results [Requires Ophan AWS Console Access]",
        url = s"https://eu-west-1.console.aws.amazon.com/athena/home?region=eu-west-1#query/history/${executionId}")),
      topicArn = env.snsTopicForAlerts,
      client = AWS.snsClient(AwsCredentials.developerPlaygroundCredentials))

    Try(Await.result(notificationAttempt, 10.seconds)) match {
      case Success(_) => logger.info("Sent notification via Anghammarad")
      case Failure(ex) => logger.error(s"Failed to send notification due to $ex")
    }

  }

}

object WorkerLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handler(lambdaInput: SQSEvent, context: Context): Unit = {
    val env = WorkerEnv()
    logger.info(s"Starting $env")
    lambdaInput.getRecords.asScala.map { rawInput =>
      val parseAttempt = decode[MonitoringEventWithQueryInfo](rawInput.getBody)
      parseAttempt.map { eventWithQueryInfo =>
        process(
          env,
          eventWithQueryInfo.monitoringEvent.feature,
          eventWithQueryInfo.monitoringEvent.platform,
          eventWithQueryInfo.queryExecutionId)
      }
    }
  }

  def analyseQueryResults(feature: Feature, platform: Platform, queryExecutionId: String) = {
    val athenaResults = Athena.retrieveResult(queryExecutionId)
    val impressionCountsByVersion = ImpressionCounts.getImpressionCounts(athenaResults)
    val checksToRun = feature.monitoringQuery(platform).checks
    val allResults = Checks.performChecks(impressionCountsByVersion, checksToRun)
    val failures = allResults.filter(_.resultIsAcceptable == false)
    if (failures.nonEmpty) {
      Notifications.alert(
        feature = feature,
        executionId = queryExecutionId,
        Checks.messageFromFailures(failures),
        stackForProductionAlerts = Stack(platform.id) //This stack will be overridden in other environments (to avoid spam)
      )
    } else {
      logger.info(s"Monitoring ran successfully for ${feature.id} on ${platform.id}. No problems were detected.\n${Checks.summariseResults(allResults)}.")
    }
  }

  def process(env: WorkerEnv, feature: Feature, platform: Platform, queryExecutionId: String): Unit = {
    val queryStatus = Athena.retrieveQueryStatus(queryExecutionId)
    if (Athena.queryHasSuccessfulState(queryStatus)) {
      analyseQueryResults(feature, platform, queryExecutionId)
    } else if (Athena.queryHasUnsuccessfulState(queryStatus)) {
      Notifications.alert(
        feature,
        queryExecutionId,
        "Athena failed to complete your query. Consequently data-lake-alerts was unable to check the results today.",
        stackForProductionAlerts = Stack(platform.id) //This stack will be overridden in other environments (to avoid spam)
      )
    } else {
      val delayBeforeNextCheck = 180
      val queueName = Sqs.queueName(env.app, env.stage)
      logger.info(s"Query for ${feature.id} on ${platform.id} is still running. Adding a new message to $queueName queue... will try again in ${delayBeforeNextCheck} seconds")
      // It's necessary to explicitly add the message back onto the queue; by default a successful lambda execution will remove the message
      Sqs.enqueueRunningQuery(
        MonitoringEventWithQueryInfo(MonitoringEvent(feature, platform), queryExecutionId),
        queueName,
        180)
    }
  }

}

