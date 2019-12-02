name := "data-lake-alerts"

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

val awsJavaSdkVersion = "1.11.683"
val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % awsJavaSdkVersion,
  "com.amazonaws" % "aws-java-sdk-sqs" % awsJavaSdkVersion,
  "com.amazonaws" % "aws-java-sdk-athena" % awsJavaSdkVersion,
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-lambda-java-events" % "2.2.7",
  "com.amazonaws" % "aws-lambda-java-log4j2" % "1.1.0",
  "com.gu" %% "anghammarad-client" % "1.0.4",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.slf4j" % "slf4j-api" % "1.7.26",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.8.2",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test"
)

enablePlugins(RiffRaffArtifact)

assemblyJarName := s"${name.value}.jar"
assemblyMergeStrategy in assembly := {
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat" => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cfn.yaml"), s"${name.value}-cfn/cfn.yaml")