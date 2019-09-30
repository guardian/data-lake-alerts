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

  def alert(featureId: String, executionId: String, message: String, stackForProductionAlerts: Stack) = {

    val stack = env.stage match {
      case "PROD" => stackForProductionAlerts
      case _ => Stack("testing-alerts") // Alerts sent in DEV and CODE will go to the 'anghammarad.test.alerts' Google Group (to avoid spam)
    }

    val notificationAttempt = Anghammarad.notify(
      subject = s"Data Lake Monitoring | Check Failed for ${featureId}",
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
    process(env, Platforms.platformToMonitor(lambdaInput.platformId), Features.featureToMonitor(lambdaInput.featureId))
  }

  def process(env: WorkerEnv, platform: Platform, feature: Feature): Unit = {
    logger.info(s"Starting monitoring for $feature on $platform")
    val monitoringQuery = feature.monitoringQuery(platform)
    logger.info(s"Query will be:\n ${monitoringQuery.query}")
    val queryExecutionId = Athena.startQuery(monitoringQuery).getQueryExecutionId
    Athena.waitForQueryToComplete(queryExecutionId)
    val monitoringResult = feature.monitoringQueryResult(Athena.retrieveResult(queryExecutionId), monitoringQuery.minimumImpressionsThreshold)
    if (!monitoringResult.resultIsAcceptable) {
      Notifications.alert(
        featureId = feature.id,
        executionId = queryExecutionId,
        monitoringResult.additionalInformation,
        stackForProductionAlerts = Stack(platform.id) //This stack will be overridden in other environments (to avoid spam)
      )
    } else {
      logger.info(s"Monitoring ran successfully for ${feature.id} on ${platform.id}. No problems were detected.\n${monitoringResult.additionalInformation}.")
    }
  }

}

object TestWorker {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      logger.error("Please provide a platformId and a featureId e.g. sbt \"runMain com.gu.datalakealerts.TestWorker android friction_screen\"")
    } else {
      WorkerLambda.process(WorkerEnv(), Platforms.platformToMonitor(args(0)), Features.featureToMonitor(args(1)))
    }
  }
}
