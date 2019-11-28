name := "banno"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "com.twitter" % "hbc-core" % "2.2.0",
  "com.typesafe.akka" %% "akka-stream" % "2.6.0",
  "com.typesafe.akka" %% "akka-http" % "10.1.10",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.10",
)
