package com.gu.datalakealerts

import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model.{ InvocationType, InvokeRequest }

object InvokeWorker {

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

    client.invoke(request)

  }

}
