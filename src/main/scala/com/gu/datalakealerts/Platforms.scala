package com.gu.datalakealerts

object Platforms {

  sealed trait Platform { val id: String }
  case object Android extends Platform { val id = "android" }
  case object Ios extends Platform { val id = "ios" }

  def platformToMonitor(platformId: String): Platform = platformId match {
    case Android.id => Android
    case Ios.id => Ios
    case _ => throw new RuntimeException(s"Invalid platform specified: platform must be ${Android.id} or ${Ios.id}")
  }

}
