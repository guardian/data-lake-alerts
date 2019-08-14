package com.gu.datalakealerts

import java.text.NumberFormat

object AlertInformation {

  def percentageChange(actualImpressions: Int, expectedImpressions: Int): BigDecimal = {
    BigDecimal((actualImpressions.toDouble / expectedImpressions.toDouble) - 1).setScale(2, BigDecimal.RoundingMode.HALF_UP)
  }

  def describePercentageChange(percentageChange: BigDecimal): String = percentageChange match {
    case _ if percentageChange > 0 => s"${NumberFormat.getPercentInstance.format(percentageChange)} above the threshold"
    case _ if percentageChange < 0 => s"${NumberFormat.getPercentInstance.format(-percentageChange)} below the threshold"
    case _ => s"Threshold was met exactly"
  }

  def describeResults(actualImpressions: Int, expectedImpressions: Int): String = {
    val percentageChangeDescription = describePercentageChange(percentageChange(actualImpressions, expectedImpressions))
    s"Actual impressions: $actualImpressions | Expected impressions: $expectedImpressions | Percentage: $percentageChangeDescription"
  }

}

