package com.gu.datalakealerts

import com.amazonaws.auth.{ AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider }
import com.amazonaws.auth.profile.ProfileCredentialsProvider

object AwsCredentials {

  val athenaCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("ophan"),
    new EnvironmentVariableCredentialsProvider())

  val notificationCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("developerPlayground"),
    new EnvironmentVariableCredentialsProvider())

}
