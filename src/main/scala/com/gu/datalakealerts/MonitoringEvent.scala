package com.gu.datalakealerts

case class MonitoringEvent(feature: Features.Feature, platform: Platforms.Platform)

case class MonitoringEventWithQueryInfo(monitoringEvent: MonitoringEvent, queryExecutionId: String) {
  def toJson = s"""
     |{
     |  "featureId": "${monitoringEvent.feature.id}",
     |  "platformId": "${monitoringEvent.platform.id}",
     |  "queryExecutionId": "${queryExecutionId}"
     |}
   |""".stripMargin
}
