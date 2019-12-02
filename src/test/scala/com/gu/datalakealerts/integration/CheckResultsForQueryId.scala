package com.gu.datalakealerts.integration

import com.gu.datalakealerts.{ Features, Platforms, WorkerEnv, WorkerLambda }
import com.gu.datalakealerts.SchedulerLambda.logger

// Requires Ophan (Developer) and Dev Playground (Developer) Janus credentials
object CheckResultsForQueryId {

  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      logger.error("Please provide a featureId, a platformId and a query execution id e.g. sbt \"runMain com.gu.datalakealerts.TestWorker friction_screen android 0c97cd8a-8b23-4f53-996b-97c357ee089a\"")
    } else {
      val env = WorkerEnv(app = "data-lake-alerts", stack = "stack", stage = "CODE", WorkerEnv.snsTopicFromParameterStore) // Run with code env to send an event to a real SQS queue
      WorkerLambda.process(env, Features.featureToMonitor(args(0)), Platforms.platformToMonitor(args(1)), args(2))
    }
  }

}
