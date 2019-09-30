package com.gu.datalakealerts

case class MonitoringEvent(feature: Features.Feature, platform: Platforms.Platform) {

  def toJson = s"""
     |{
     |  "featureId": "${feature.id}",
     |  "platformId": "${platform.id}"
     |}
   |""".stripMargin

}
