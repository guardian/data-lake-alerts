package com.gu.datalakealerts

import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.{ SendMessageRequest, SendMessageResult }

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
      .withMessageBody(monitoringEventWithQueryInfo.toJson)
      .withDelaySeconds(delayInSeconds)
    sqsClient.sendMessage(messageRequest)
  }

}
