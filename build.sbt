organization := "com.oradian.infra"
name := "monohash"
version := "0.5.0"

libraryDependencies ++= Seq(
  "org.specs2"  %% "specs2-core" % "4.4.1" % Test,
  "com.lihaoyi" %% "sourcecode"  % "0.1.5" % Test,
)

crossPaths := false
autoScalaLibrary := false

scalaVersion := "2.12.8" // for tests only
scalacOptions := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:_",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:_",
  "-Yrangepos",
)

fork in run := true
