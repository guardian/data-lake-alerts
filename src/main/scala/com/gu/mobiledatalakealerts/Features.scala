package com.gu.mobiledatalakealerts

import java.time.LocalDate

import com.gu.mobiledatalakealerts.Platforms.Platform

object Features {

  val yesterday: LocalDate = LocalDate.now().minusDays(1)
  val allFeaturesWithMonitoring: List[Feature] = List(FrictionScreen)

  def featureToMonitor(featureId: String): Feature = {
    allFeaturesWithMonitoring
      .find(feature => feature.id == featureId)
      .getOrElse(throw new RuntimeException(s"Invalid feature specified: features with monitoring are ${allFeaturesWithMonitoring.map(_.id)}"))
  }

  case class MonitoringQuery(query: String, minimumThresholdInBeta: Int)
  sealed trait Feature {
    val id: String
    def query(latestBetaPrefix: String, platform: Platform): MonitoringQuery
  }

  case object FrictionScreen extends Feature {

    val id = "friction_screen"

    def query(latestBetaPrefix: String, platform: Platform): MonitoringQuery = MonitoringQuery(
      query = s"""
        |select browser_version, count (distinct page_view_id) as friction_screen_impressions
        |from clean.pageview
        |cross join unnest (component_events) x (c)
        |where received_date >= date '$yesterday'
        |and device_type like '%${platform.id.toUpperCase}%'
        |and c.component.type like '%APP_SCREEN%'
        |and c.component.campaign_code like '%friction%' and c.component.campaign_code like '%subscription_screen%'
        |and browser_version like '$latestBetaPrefix%'
        |group by 1
      """.stripMargin,
      minimumThresholdInBeta = 100)

  }

}
