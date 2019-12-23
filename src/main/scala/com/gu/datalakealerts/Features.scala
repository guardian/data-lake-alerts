package com.gu.datalakealerts

import java.time.LocalDate

import com.amazonaws.services.athena.model.ResultSet
import com.gu.datalakealerts.Platforms.{ Android, Platform, iOS }
import com.gu.datalakealerts.apps.ResultHandler

object Features {

  val allFeaturesWithMonitoring: List[Feature] = List(FrictionScreen, OlgilEpic, BrazeEpic, OlgilBanner, BrazeBanner)

  def yesterday: LocalDate = LocalDate.now().minusDays(1)

  def featureToMonitor(featureId: String): Feature = {
    allFeaturesWithMonitoring
      .find(feature => feature.id == featureId)
      .getOrElse(throw new RuntimeException(s"Invalid feature specified: features with monitoring are ${allFeaturesWithMonitoring.map(_.id)}"))
  }

  case class MonitoringQuery(query: String, minimumImpressionsThreshold: Int)
  case class MonitoringQueryResult(resultIsAcceptable: Boolean, additionalInformation: String)

  sealed trait Feature {
    val id: String
    val platformsToMonitor: List[Platform] = List(iOS, Android)
    def monitoringQuery(platform: Platform): MonitoringQuery
    def monitoringQueryResult(resultSet: ResultSet, minimumImpressionsThreshold: Int): MonitoringQueryResult
  }

  case object FrictionScreen extends Feature {

    val id = "friction_screen"

    def monitoringQuery(platform: Platform): MonitoringQuery = {

      val query = s"""
        |select browser_version, count (distinct page_view_id) as friction_screen_impressions
        |from clean.pageview
        |cross join unnest (component_events) x (c)
        |where received_date = date '$yesterday'
        |and device_type like '%${platform.id.toUpperCase}%'
        |and c.component.type like '%APP_SCREEN%'
        |and c.component.campaign_code like '%friction%' and c.component.campaign_code like '%subscription_screen%'
        |group by 1""".stripMargin

      val minimumImpressionsThreshold = platform match {
        case Android => 10000
        case iOS => 40000
      }

      MonitoringQuery(query, minimumImpressionsThreshold)

    }

    def monitoringQueryResult(resultSet: ResultSet, minimumImpressionsThreshold: Int): MonitoringQueryResult =
      ResultHandler.checkThresholdMetAcrossAppVersions(resultSet, minimumImpressionsThreshold)

  }

  case object OlgilEpic extends Feature {
    val id = "olgil_epic"

    def monitoringQueryResult(resultSet: ResultSet, minimumImpressionsThreshold: Int): MonitoringQueryResult =
      ResultHandler.checkThresholdMetAcrossAppVersions(resultSet, minimumImpressionsThreshold)

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case Android =>
          MonitoringQuery(s"""
            |select browser_version, count (distinct page_view_id) as epic_impressions
            |from clean.pageview
            |cross join unnest (ab_tests) x (ab)
            |where received_date = date '$yesterday'
            |and path not like '%.mp3%'
            |and device_type like '%ANDROID%'
            |and ab.name like '%epic%'
            |and ab.completed = True
            |group by 1
          """.stripMargin, 48305)
        case iOS =>
          MonitoringQuery(s"""
            |select browser_version, count (distinct page_view_id) as epic_impressions
            |from clean.pageview
            |cross join unnest (ab_tests) x (ab)
            |where received_date = date '$yesterday'
            |and path not like '%.mp3%'
            |and device_type like '%IOS%'
            |and ab.name like '%epic%'
            |and ab.completed = False
            |group by 1
          """.stripMargin, 185000)
      }
    }
  }

  case object BrazeEpic extends Feature {
    val id = "braze_epic"

    def monitoringQueryResult(resultSet: ResultSet, minimumImpressionsThreshold: Int): MonitoringQueryResult =
      ResultHandler.checkThresholdMetAcrossAppVersions(resultSet, minimumImpressionsThreshold)

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case Android =>
          MonitoringQuery(s"""
                             |select browser_version, count (distinct page_view_id)
                             |from clean.pageview
                             |cross join unnest (component_events) x (c)
                             |where received_date = date '$yesterday'
                             |and device_type like '%ANDROID%'
                             |and c.component.type = 'APP_EPIC'
                             |and c.action = 'VIEW'
                             |group by 1
          """.stripMargin, 162170)
        case iOS =>
          MonitoringQuery(s"""
            |select browser_version, count (distinct page_view_id)
            |from clean.pageview 
            |cross join unnest (component_events) x (c)
            |where received_date = date '$yesterday'
            |and device_type like '%IOS%'
            |and c.component.type = 'APP_EPIC'
            |and c.action = 'VIEW'
            |group by 1
          """.stripMargin, 103000)
      }
    }
  }
  case object OlgilBanner extends Feature {
    val id = "olgil_banner"
    override val platformsToMonitor = List(iOS)

    def monitoringQueryResult(resultSet: ResultSet, minimumImpressionsThreshold: Int): MonitoringQueryResult =
      ResultHandler.checkThresholdMetAcrossAppVersions(resultSet, minimumImpressionsThreshold)

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case iOS =>
          MonitoringQuery(s"""
            |select browser_version, count (distinct page_view_id) as banner_impressions
            |from clean.pageview
            |cross join unnest (ab_tests) x (ab)
            |where received_date = date '$yesterday'
            |and path not like '%.mp3%'
            |and device_type like '%IOS%'
            |and ab.name like '%banner%'
            |and ab.completed = False
            |group by 1
          """.stripMargin, 17038)
        case _ => throw new RuntimeException("Only iOS platform is supported.")

      }
    }
  }

  case object BrazeBanner extends Feature {
    val id = "braze_banner"
    override val platformsToMonitor = List(iOS)

    def monitoringQueryResult(resultSet: ResultSet, minimumImpressionsThreshold: Int): MonitoringQueryResult =
      ResultHandler.checkThresholdMetAcrossAppVersions(resultSet, minimumImpressionsThreshold)

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case iOS =>
          MonitoringQuery(s"""
            |select browser_version, count (distinct page_view_id)
            |from clean.pageview 
            |cross join unnest (component_events) x (c)
            |where received_date = date '$yesterday'
            |and device_type like '%IOS%'
            |and c.component.type = 'APP_ENGAGEMENT_BANNER'
            |and c.action = 'VIEW'
            |group by 1
          """.stripMargin, 2893)
        case _ => throw new RuntimeException("Only iOS platform is supported.")
      }
    }
  }
}
