package com.gu.datalakealerts

import org.scalatest._
import ResultInformation._

class ResultInformationTest extends FlatSpec {

  "percentageChange" should "correctly calculate the percentage change if the threshold is exceeded" in {
    val result = percentageChange(actualImpressions = 110, expectedImpressions = 100)
    assert(result == 0.10)
  }

  "percentageChange" should "correctly calculate the percentage change if the threshold is not met" in {
    val result = percentageChange(actualImpressions = 90, expectedImpressions = 100)
    assert(result == -0.10)
  }

  "percentageChange" should "correctly identify a case where the threshold is met exactly" in {
    val result = percentageChange(actualImpressions = 100, expectedImpressions = 100)
    assert(result == 0)
  }

  "describePercentageChange" should "accurately describe a case where the threshold is exceeded" in {
    val description = describePercentageChange(0.10)
    assert(description == "10% above the threshold")
  }

  "describePercentageChange" should "accurately describe a case where the threshold is not met" in {
    val description = describePercentageChange(-0.10)
    assert(description == "10% below the threshold")
  }

  "describePercentageChange" should "accurately describe a case where the threshold is met exactly" in {
    val description = describePercentageChange(0)
    assert(description == "Threshold was met exactly")
  }

}
