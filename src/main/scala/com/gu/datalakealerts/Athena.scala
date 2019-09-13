package com.gu.datalakealerts

import com.amazonaws.services.athena.{ AmazonAthena, AmazonAthenaClient }
import com.amazonaws.services.athena.model.{ GetQueryExecutionRequest, GetQueryResultsRequest, ResultConfiguration, ResultSet, StartQueryExecutionRequest, StartQueryExecutionResult }
import com.gu.datalakealerts.Features.MonitoringQuery
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.JavaConverters._

object Athena {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val client: AmazonAthena = AmazonAthenaClient
    .builder()
    .withCredentials(AwsCredentials.athenaCredentials)
    .build()

  def startQuery(monitoringQuery: MonitoringQuery): StartQueryExecutionResult = {
    val startQueryRequest: StartQueryExecutionRequest = new StartQueryExecutionRequest()
      .withQueryString(monitoringQuery.query)
      .withWorkGroup("primary") //TODO: consider using a dedicated workgroup
    client.startQueryExecution(startQueryRequest)
  }

  def waitForQueryToComplete(queryExecutionId: String, shouldStopPolling: Boolean = false): Unit = {
    val getQueryExecutionRequest: GetQueryExecutionRequest = new GetQueryExecutionRequest()
      .withQueryExecutionId(queryExecutionId)
    if (shouldStopPolling) {
      logger.info(s"Query $queryExecutionId is now complete")
    } else {
      logger.info(s"Query $queryExecutionId is still running, will poll again in 1 second")
      Thread.sleep(1000)
      val shouldStopPolling = {
        val status = client.getQueryExecution(getQueryExecutionRequest).getQueryExecution.getStatus.getState
        logger.info(s"Query $queryExecutionId status is: $status")
        status == "SUCCEEDED" || status == "FAILED" || status == "CANCELLED"
      }
      waitForQueryToComplete(queryExecutionId, shouldStopPolling)
    }
  }

  def retrieveResult(queryExecutionId: String): ResultSet = {
    val retrieveQueryRequest: GetQueryResultsRequest = new GetQueryResultsRequest()
      .withQueryExecutionId(queryExecutionId)
    client.getQueryResults(retrieveQueryRequest).getResultSet
  }

}

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

}
