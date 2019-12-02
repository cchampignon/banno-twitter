name := "banno"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "com.twitter" % "hbc-core" % "2.2.0",
  "com.typesafe.akka" %% "akka-stream-typed" % "2.6.0",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.0",
  "com.typesafe.akka" %% "akka-http" % "10.1.11",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.11",
  "com.vdurmont" % "emoji-java" % "5.1.1",
  "io.lemonlabs" %% "scala-uri" % "1.5.1",

  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.0" % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
)
