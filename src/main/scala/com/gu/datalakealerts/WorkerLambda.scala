package com.gu.datalakealerts

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.gu.anghammarad.{ AWS, Anghammarad }
import com.gu.anghammarad.models._
import com.gu.datalakealerts.Features.Feature
import com.gu.datalakealerts.WorkerLambda.logger
import com.gu.datalakealerts.Platforms.Platform
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success, Try }

class LambdaInput() {

  var featureId: String = _
  def getFeatureId(): String = featureId
  def setFeatureId(theFeature: String): Unit = featureId = theFeature

  var platformId: String = _
  def getPlatformId(): String = platformId
  def setPlatformId(thePlatform: String): Unit = platformId = thePlatform

  var queryExecutionId: String = _
  def getqueryExecutionId(): String = queryExecutionId
  def setqueryExecutionId(theQueryExecutionId: String): Unit = queryExecutionId = theQueryExecutionId

}

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

  def handler(lambdaInput: LambdaInput, context: Context): Unit = {
    val env = WorkerEnv()
    logger.info(s"Starting $env")
    process(
      env,
      Features.featureToMonitor(lambdaInput.featureId),
      Platforms.platformToMonitor(lambdaInput.platformId),
      lambdaInput.queryExecutionId)
  }

  def analyseQueryResults(feature: Feature, platform: Platform, queryExecutionId: String) = {
    val monitoringResult = feature.monitoringQueryResult(
      Athena.retrieveResult(queryExecutionId),
      feature.monitoringQuery(platform).minimumImpressionsThreshold)
    if (!monitoringResult.resultIsAcceptable) {
      Notifications.alert(
        feature = feature,
        executionId = queryExecutionId,
        monitoringResult.additionalInformation,
        stackForProductionAlerts = Stack(platform.id) //This stack will be overridden in other environments (to avoid spam)
      )
    } else {
      logger.info(s"Monitoring ran successfully for ${feature.id} on ${platform.id}. No problems were detected.\n${monitoringResult.additionalInformation}.")
    }
  }

  def process(env: WorkerEnv, feature: Feature, platform: Platform, queryExecutionId: String): Unit = {

    // Don't interact with SQS when running locally (to avoid all contributors requiring Ophan dev account access)
    val queryStatus = if (env.stage == "DEV") { Athena.retrieveQueryStatus(queryExecutionId) } else { "SUCCEEDED" }

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
      logger.info(s"Query for ${feature.id} on ${platform.id} is still running. Adding a new message to SQS... will try again in ${delayBeforeNextCheck} seconds")
      // It's necessary to explicitly add the message back onto the queue; by default a successful lambda execution will remove the message
      Sqs.enqueueRunningQuery(
        MonitoringEventWithQueryInfo(MonitoringEvent(feature, platform), queryExecutionId),
        Sqs.queueName(env.app, env.stage),
        180)
    }
  }

}

object TestWorker {
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      logger.error("Please provide a featureId, a platformId, and a query execution id e.g. sbt \"runMain com.gu.datalakealerts.TestWorker friction_screen android 0c97cd8a-8b23-4f53-996b-97c357ee089a\"")
    } else {
      WorkerLambda.process(WorkerEnv(), Features.featureToMonitor(args(0)), Platforms.platformToMonitor(args(1)), args(2))
    }
  }
}
