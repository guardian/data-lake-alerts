name := "mobile-data-lake-alerts"

organization := "com.gu"

description:= "Query the data lake and alert if thresholds are breached"

version := "1.0"

scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-Ywarn-dead-code"
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-lambda-java-log4j2" % "1.1.0",
  "com.gu" %% "anghammarad-client" % "1.0.4",
  "org.slf4j" % "slf4j-api" % "1.7.26",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.8.2", // 2.11.2 leads to NoClassDefFoundError
  "com.amazonaws" % "aws-java-sdk-athena" % "1.11.577"
)

enablePlugins(RiffRaffArtifact)

assemblyJarName := s"${name.value}.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cfn.yaml"), s"${name.value}-cfn/cfn.yaml")