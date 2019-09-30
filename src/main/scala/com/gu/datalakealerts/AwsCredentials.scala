package com.gu.datalakealerts

import com.amazonaws.auth.{ AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider }
import com.amazonaws.auth.profile.ProfileCredentialsProvider

object AwsCredentials {

  val ophanCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("ophan"),
    new EnvironmentVariableCredentialsProvider())

  val developerPlaygroundCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("developerPlayground"),
    new EnvironmentVariableCredentialsProvider())

}
