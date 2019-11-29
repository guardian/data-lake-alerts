package com.gu.datalakealerts

import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.{ SendMessageRequest, SendMessageResult }
import com.gu.datalakealerts.EventModels.MonitoringEventWithQueryInfo
import io.circe.syntax._

object Sqs {

  def queueName(app: String, stage: String) = s"$app-$stage" //This is also defined in cfn.yml

  private val sqsClient = AmazonSQSClient
    .builder()
    .withCredentials(AwsCredentials.ophanCredentials)
    .build()

  def enqueueRunningQuery(monitoringEventWithQueryInfo: MonitoringEventWithQueryInfo, queueName: String, delayInSeconds: Int): SendMessageResult = {
    val queueUrl = sqsClient.getQueueUrl(queueName).getQueueUrl
    val messageRequest = new SendMessageRequest()
      .withQueueUrl(queueUrl)
      .withMessageBody(monitoringEventWithQueryInfo.asJson.toString())
      .withDelaySeconds(delayInSeconds)
    sqsClient.sendMessage(messageRequest)
  }

}
