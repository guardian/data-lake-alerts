package com.gu.datalakealerts.apps

import com.amazonaws.services.athena.model.ResultSet

import scala.collection.JavaConverters._

object ResultHandler {

  object ImpressionCounts {

    case class VersionWithImpressionCount(versionNumber: String, impressions: Int) {
      def summary = s"$versionNumber: $impressions"
    }

    def getImpressionCounts(result: ResultSet): List[VersionWithImpressionCount] = {
      result.getRows.asScala.toList.drop(1).map { row =>
        val datum = row.getData
        VersionWithImpressionCount(datum.get(0).getVarCharValue, datum.get(1).getVarCharValue.toInt)
      }
    }

    def getTotalImpressions(allVersionWithImpressionCounts: List[VersionWithImpressionCount]): Int = {
      allVersionWithImpressionCounts.map(_.impressions).sum
    }

  }

}
