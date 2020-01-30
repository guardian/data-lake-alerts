package com.gu.datalakealerts

import com.gu.datalakealerts.apps.ResultHandler.ImpressionCounts
import com.gu.datalakealerts.apps.ResultHandler.ImpressionCounts.VersionWithImpressionCount

object Checks {

  case class IndividualResult(resultIsAcceptable: Boolean, checkName: String, resultInformation: String)

  def summariseResults(results: List[IndividualResult]): String = {
    results.map(result => s"${result.checkName}: ${result.resultInformation}").mkString("\n")
  }

  def messageFromFailures(failedChecks: List[IndividualResult]): String = {
    "The following checks failed:\n\n" + s"${summariseResults(failedChecks)}"
  }

  sealed trait Check

  case class TotalImpressionsIsGreaterThan(expectedImpressions: Int) extends Check {
    def checkThresholdMetAcrossAppVersions(allVersionWithImpressionCounts: List[VersionWithImpressionCount], minimumImpressionsThreshold: Int): IndividualResult = {
      val totalImpressions = ImpressionCounts.getTotalImpressions(allVersionWithImpressionCounts)
      val resultIsAcceptable = totalImpressions > minimumImpressionsThreshold
      IndividualResult(resultIsAcceptable, "MinimumThresholdIsGreaterThan", ResultInformation.describeResults(totalImpressions, expectedImpressions))
    }
  }

  def performChecks(allVersionsWithImpressionCounts: List[VersionWithImpressionCount], checks: List[Check]): List[IndividualResult] = checks.map {
    case check @ TotalImpressionsIsGreaterThan(expectedImpressions) => check.checkThresholdMetAcrossAppVersions(allVersionsWithImpressionCounts, expectedImpressions)
  }

}
