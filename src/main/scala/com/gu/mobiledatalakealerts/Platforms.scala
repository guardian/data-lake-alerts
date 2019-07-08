package com.gu.mobiledatalakealerts

object Platforms {

  sealed trait Platform { val id: String }
  case object Android extends Platform { val id = "android" }
  case object iOS extends Platform { val id = "ios" }

  def platformToMonitor(platformId: String): Platform = platformId match {
    case Android.id => Android
    case iOS.id => iOS
    case _ => throw new RuntimeException(s"Invalid platform specified: platform must be ${Android.id} or ${iOS.id}")
  }

}
