package com.gu.mobiledatalakealerts

import com.amazonaws.services.lambda.runtime.Context
import com.gu.anghammarad.{ AWS, Anghammarad }
import com.gu.anghammarad.models._
import com.gu.mobiledatalakealerts.Features.{ Feature, FrictionScreen }
import com.gu.mobiledatalakealerts.Lambda.logger
import com.gu.mobiledatalakealerts.Platforms.{ Platform, iOS }
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

case class Env(app: String, stack: String, stage: String, snsTopicForAlerts: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object Env {
  def apply(): Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"),
    Option(System.getenv("SnsTopicForAlerts")).getOrElse("DEV"))
}

object Notifications {

  val env = Env()

  def alert(featureId: String, message: Option[String], stack: Stack) = {

    val notificationAttempt = Anghammarad.notify(
      subject = s"Data Lake Monitoring | Check Failed for ${featureId}",
      message = message.getOrElse(s"Check failed when monitoring ${featureId}"),
      sourceSystem = "Data Lake Alerts",
      channel = Email,
      target = List(stack),
      actions = Nil,
      topicArn = env.snsTopicForAlerts,
      client = AWS.snsClient(AwsCredentials.notificationCredentials))

    Try(Await.result(notificationAttempt, 10.seconds)) match {
      case Success(_) => logger.info("Sent notification via Anghammarad")
      case Failure(ex) => logger.error(s"Failed to send notification due to $ex")
    }
  }

}

object Lambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handler(lambdaInput: LambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    process(env, Platforms.platformToMonitor(lambdaInput.platformId), Features.featureToMonitor(lambdaInput.featureId))
  }

  def process(env: Env, platform: Platform, feature: Feature): Unit = {
    logger.info(s"Starting monitoring for $feature on $platform")
    val monitoringQuery = feature.monitoringQuery(platform)
    logger.info(s"Query will be:\n ${monitoringQuery.query}")
    val queryExecutionId = Athena.startQuery(monitoringQuery).getQueryExecutionId
    Athena.waitForQueryToComplete(queryExecutionId)
    val monitoringResult = feature.monitoringQueryResult(Athena.retrieveResult(queryExecutionId), monitoringQuery.minimumImpressionsThreshold)
    if (!monitoringResult.resultIsAcceptable) {
      Notifications.alert(feature.id, monitoringResult.additionalDebugInformation, Stack(platform.id))
    } else {
      logger.info(s"Monitoring ran successfully for ${feature.id}. No problems were detected.")
    }
  }

}

object TestIt {
  def main(args: Array[String]): Unit = {
    Lambda.process(Env(), iOS, FrictionScreen)
  }
}
