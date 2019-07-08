package com.gu.mobiledatalakealerts

import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.profile.ProfileCredentialsProvider

object AwsCredentials {

  val athenaCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("ophan")
  // FIXME Add correct credentials provider for a lambda
  )

  val notificationCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile")
  // FIXME Add correct credentials provider for a lambda
  )

}
