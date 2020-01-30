package com.gu.datalakealerts

import java.time.LocalDate

import com.gu.datalakealerts.Checks.{ Check, TotalImpressionsIsGreaterThan }
import com.gu.datalakealerts.Platforms.{ Android, Platform, Ios }

object Features {

  val allFeaturesWithMonitoring: List[Feature] = List(FrictionScreen, OlgilEpic, BrazeEpic, OlgilBanner, BrazeBanner)

  def yesterday: LocalDate = LocalDate.now().minusDays(1)

  def featureToMonitor(featureId: String): Feature = {
    allFeaturesWithMonitoring
      .find(feature => feature.id == featureId)
      .getOrElse(throw new RuntimeException(s"Invalid feature specified: features with monitoring are ${allFeaturesWithMonitoring.map(_.id)}"))
  }

  case class MonitoringQuery(query: String, checks: List[Check])

  sealed trait Feature {
    val id: String
    val platformsToMonitor: List[Platform] = List(Ios, Android)
    def monitoringQuery(platform: Platform): MonitoringQuery
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
        case Ios => 40000
      }

      val checks: List[Check] = List(TotalImpressionsIsGreaterThan(minimumImpressionsThreshold))

      MonitoringQuery(query, checks)

    }

  }

  case object OlgilEpic extends Feature {

    val id = "olgil_epic"

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case Android =>
          MonitoringQuery(
            s"""
            |select browser_version, count (distinct page_view_id) as epic_impressions
            |from clean.pageview
            |cross join unnest (ab_tests) x (ab)
            |where received_date = date '$yesterday'
            |and path not like '%.mp3%'
            |and device_type like '%ANDROID%'
            |and ab.name like '%epic%'
            |and ab.completed = True
            |group by 1
          """.stripMargin,
            List(TotalImpressionsIsGreaterThan(48305)))
        case Ios =>
          MonitoringQuery(
            s"""
            |select browser_version, count (distinct page_view_id) as epic_impressions
            |from clean.pageview
            |cross join unnest (ab_tests) x (ab)
            |where received_date = date '$yesterday'
            |and path not like '%.mp3%'
            |and device_type like '%IOS%'
            |and ab.name like '%epic%'
            |and ab.completed = False
            |group by 1
          """.stripMargin,
            List(TotalImpressionsIsGreaterThan(185000)))
      }
    }
  }

  case object BrazeEpic extends Feature {

    val id = "braze_epic"

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case Android =>
          MonitoringQuery(
            s"""
            |select browser_version, count (distinct page_view_id)
            |from clean.pageview
            |cross join unnest (component_events) x (c)
            |where received_date = date '$yesterday'
            |and device_type like '%ANDROID%'
            |and c.component.type = 'APP_EPIC'
            |and c.action = 'VIEW'
            |group by 1
          """.stripMargin,
            List(TotalImpressionsIsGreaterThan(162170)))
        case Ios =>
          MonitoringQuery(
            s"""
            |select browser_version, count (distinct page_view_id)
            |from clean.pageview 
            |cross join unnest (component_events) x (c)
            |where received_date = date '$yesterday'
            |and device_type like '%IOS%'
            |and c.component.type = 'APP_EPIC'
            |and c.action = 'VIEW'
            |group by 1
          """.stripMargin,
            List(TotalImpressionsIsGreaterThan(103000)))
      }
    }
  }
  case object OlgilBanner extends Feature {

    val id = "olgil_banner"

    override val platformsToMonitor = List(Ios)

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case Ios =>
          MonitoringQuery(
            s"""
            |select browser_version, count (distinct page_view_id) as banner_impressions
            |from clean.pageview
            |cross join unnest (ab_tests) x (ab)
            |where received_date = date '$yesterday'
            |and path not like '%.mp3%'
            |and device_type like '%IOS%'
            |and ab.name like '%banner%'
            |and ab.completed = False
            |group by 1
          """.stripMargin,
            List(TotalImpressionsIsGreaterThan(17038)))
        case _ => throw new RuntimeException(s"Only Ios platform is supported for feature: $id.")
      }
    }
  }

  case object BrazeBanner extends Feature {

    val id = "braze_banner"

    override val platformsToMonitor = List(Ios)

    def monitoringQuery(platform: Platform): MonitoringQuery = {
      platform match {
        case Ios =>
          MonitoringQuery(
            s"""
            |select browser_version, count (distinct page_view_id)
            |from clean.pageview 
            |cross join unnest (component_events) x (c)
            |where received_date = date '$yesterday'
            |and device_type like '%IOS%'
            |and c.component.type = 'APP_ENGAGEMENT_BANNER'
            |and c.action = 'VIEW'
            |group by 1
            """.stripMargin,
            List(TotalImpressionsIsGreaterThan(2893)))
        case _ => throw new RuntimeException(s"Only Ios platform is supported for feature: $id.")
      }
    }
  }
}
