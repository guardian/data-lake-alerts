package com.gu.datalakealerts

import com.amazonaws.services.athena.{ AmazonAthena, AmazonAthenaClient }
import com.amazonaws.services.athena.model.{ GetQueryExecutionRequest, GetQueryResultsRequest, ResultSet, StartQueryExecutionRequest, StartQueryExecutionResult }
import com.gu.datalakealerts.Features.MonitoringQuery
import org.slf4j.{ Logger, LoggerFactory }

object Athena {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val client: AmazonAthena = AmazonAthenaClient
    .builder()
    .withCredentials(AwsCredentials.ophanCredentials)
    .build()

  def startQuery(monitoringQuery: MonitoringQuery): StartQueryExecutionResult = {
    val startQueryRequest: StartQueryExecutionRequest = new StartQueryExecutionRequest()
      .withQueryString(monitoringQuery.query)
      .withWorkGroup("primary") //TODO: consider using a dedicated workgroup
    client.startQueryExecution(startQueryRequest)
  }

  def retrieveQueryStatus(queryExecutionId: String): String = {
    val getQueryExecutionRequest: GetQueryExecutionRequest = new GetQueryExecutionRequest().withQueryExecutionId(queryExecutionId)
    val status = client.getQueryExecution(getQueryExecutionRequest).getQueryExecution.getStatus.getState
    logger.info(s"Query $queryExecutionId status is: $status")
    status
  }

  def queryHasUnsuccessfulState(queryStatus: String): Boolean = {
    queryStatus == "FAILED" || queryStatus == "CANCELLED"
  }

  def queryHasSuccessfulState(queryStatus: String): Boolean = {
    queryStatus == "SUCCEEDED"
  }

  def retrieveResult(queryExecutionId: String): ResultSet = {
    val retrieveQueryRequest: GetQueryResultsRequest = new GetQueryResultsRequest()
      .withQueryExecutionId(queryExecutionId)
    client.getQueryResults(retrieveQueryRequest).getResultSet
  }

}
