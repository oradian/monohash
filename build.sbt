organization := "com.oradian.infra"
name := "monohash"
version := "0.6.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.specs2"  %% "specs2-core" % "4.10.5" % Test,
  "com.lihaoyi" %% "sourcecode"  % "0.2.1" % Test,
)

crossPaths := false
autoScalaLibrary := false

javacOptions := (Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-parameters",
  "-source", "8",
  "-target", "8",
  "-Xlint",
) ++ sys.env.get("JAVA8_HOME").map { jdk8 =>
  Seq("-bootclasspath", jdk8 + "/jre/lib/rt.jar")
}.getOrElse(Nil))

scalaVersion := "2.13.3" // for tests only
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

Global / onChangedBuildSource := ReloadOnSourceChanges
