name := "example-omega-jvm"

version := "0.1"

scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  "com.github.jnr" % "jnr-ffi" % "2.2.10",
  "com.typesafe.akka" %% "akka-actor" % "2.5.32",
  "com.typesafe.akka" %% "akka-http" % "10.2.7",
  "com.typesafe.akka" %% "akka-stream" % "2.5.32",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.7",
  "io.github.paoloboni" %% "spray-json-derived-codecs" % "2.3.5"
)

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-Xfatal-warnings",
  "-Xlint:_,-byname-implicit"
)
