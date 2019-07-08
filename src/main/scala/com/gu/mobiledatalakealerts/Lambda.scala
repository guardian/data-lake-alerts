package com.gu.mobiledatalakealerts

import com.amazonaws.services.lambda.runtime.Context
import com.gu.anghammarad.{ AWS, Anghammarad }
import com.gu.anghammarad.models._
import com.gu.mobiledatalakealerts.Features.{ Feature, FrictionScreen }
import com.gu.mobiledatalakealerts.Platforms.{ Platform, iOS }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success }

class LambdaInput() {

  var featureId: String = _
  def getFeatureId(): String = featureId
  def setFeatureId(theFeature: String): Unit = featureId = theFeature

  var platformId: String = _
  def getPlatformId(): String = platformId
  def setPlatformId(thePlatform: String): Unit = platformId = thePlatform

  var latestBetaPrefix: String = _
  def getLatestBetaPrefix(): String = latestBetaPrefix
  def setLatestBetaPrefix(thePrefix: String): Unit = latestBetaPrefix = thePrefix

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

object Lambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handler(lambdaInput: LambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    process(env, lambdaInput.latestBetaPrefix, Platforms.platformToMonitor(lambdaInput.platformId), Features.featureToMonitor(lambdaInput.featureId))
  }

  def process(env: Env, latestBetaPrefix: String, platform: Platform, feature: Feature): Unit = {
    logger.info(s"Starting monitoring for $feature on $platform (latest beta prefix is ${latestBetaPrefix})")
    val query = feature.query(latestBetaPrefix, platform)
    logger.info(s"Query will be:\n $query")
    val queryExecutionId = Athena.startQuery(query).getQueryExecutionId
    Athena.waitForQueryToComplete(queryExecutionId)
    val currentVersions = ImpressionCounts.getImpressionCounts(Athena.retrieveResult(queryExecutionId))
    logger.info(s"Versions: $currentVersions")
    val impressionsForVersionPrefix = currentVersions.map(_.impressions).sum
    val minimumAcceptableValue = query.minimumThresholdInBeta
    logger.info(s"Expected to find at least $minimumAcceptableValue impressions. Found: $impressionsForVersionPrefix impressions")
    if (impressionsForVersionPrefix < minimumAcceptableValue) {
      logger.info(s"Sending notification via Anghammarad")
      val versionSummary = currentVersions.map(version => s"${version.versionNumber}: ${version.impressions}").mkString("\n")
      val notificationMessage = s"Expected to find at least $minimumAcceptableValue impressions, but only found: ${impressionsForVersionPrefix}. $versionSummary"
      val notificationAttempt = Anghammarad.notify(
        subject = "Data Lake Monitoring",
        message = notificationMessage,
        sourceSystem = "Data Lake Alerts",
        channel = Email,
        target = List(Stack(platform.id)),
        actions = Nil,
        topicArn = env.snsTopicForAlerts,
        client = AWS.snsClient(AwsCredentials.notificationCredentials))
      notificationAttempt.onComplete {
        case Success(_) => logger.info("Sent notification via Anghammarad")
        case Failure(ex) => logger.error(s"Failed to send notification due to $ex")
      }
    } else {
      logger.info(s"Count is not below the minimum acceptable value of $minimumAcceptableValue")
    }
  }

}

object TestIt {
  def main(args: Array[String]): Unit = {
    Lambda.process(Env(), "8.0", iOS, FrictionScreen)
  }
}
