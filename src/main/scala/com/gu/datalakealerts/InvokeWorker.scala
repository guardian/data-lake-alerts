package com.gu.datalakealerts

import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model.{ InvocationType, InvokeRequest, InvokeResult }
import org.slf4j.{ Logger, LoggerFactory }

import scala.util.{ Failure, Success, Try }

object InvokeWorker {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val client = AWSLambdaClient
    .builder()
    .withCredentials(AwsCredentials.ophanCredentials)
    .build()

  def run(monitoringEvent: MonitoringEvent, stage: String) = {

    val functionToInvoke = if (stage.equals("PROD")) {
      "data-lake-alerts-worker-PROD"
    } else {
      "data-lake-alerts-worker-CODE"
    }

    val request: InvokeRequest = new InvokeRequest()
      .withFunctionName(functionToInvoke)
      .withInvocationType(InvocationType.Event) // This is asynchronous, and gives us retries for free. See: https://docs.aws.amazon.com/lambda/latest/dg/API_Invoke.html#API_Invoke_RequestSyntax
      .withPayload(monitoringEvent.toJson)

    Try { client.invoke(request) } match {
      case Success(invokeResult: InvokeResult) =>
        val invocationWasSuccessful = invokeResult.getStatusCode >= 200 && invokeResult.getStatusCode < 300
        if (invocationWasSuccessful) {
          logger.info(s"Successfully triggered worker lambda with event: $monitoringEvent")
        } else {
          logger.error(s"Failed to invoke lambda for event ${monitoringEvent} due to ${invokeResult.getFunctionError}")
        }
      case Failure(throwable: Throwable) => logger.error(s"Failed to invoke lambda for $monitoringEvent due to $throwable")
    }

  }

}
