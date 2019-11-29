package com.gu.datalakealerts

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder, HCursor, Json }

object EventModels {

  case class MonitoringEvent(feature: Features.Feature, platform: Platforms.Platform)

  implicit val monitoringEventDecoder: Decoder[MonitoringEvent] = new Decoder[MonitoringEvent] {
    final def apply(c: HCursor): Decoder.Result[MonitoringEvent] =
      for {
        featureId <- c.downField("featureId").as[String]
        platformId <- c.downField("platformId").as[String]
      } yield {
        MonitoringEvent(Features.featureToMonitor(featureId), Platforms.platformToMonitor(platformId))
      }
  }

  implicit val monitoringEventEncoder: Encoder[MonitoringEvent] = new Encoder[MonitoringEvent] {
    final def apply(event: MonitoringEvent): Json = Json.obj(
      ("featureId", Json.fromString(event.feature.id)),
      ("platformId", Json.fromString(event.platform.id)))
  }

  case class MonitoringEventWithQueryInfo(monitoringEvent: MonitoringEvent, queryExecutionId: String)
  implicit val monitoringEventWithQueryInfoDecoder: Decoder[MonitoringEventWithQueryInfo] = deriveDecoder[MonitoringEventWithQueryInfo]
  implicit val monitoringEventWithQueryInfoEncoder: Encoder[MonitoringEventWithQueryInfo] = deriveEncoder[MonitoringEventWithQueryInfo]

}
