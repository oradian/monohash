organization := "com.oradian.infra"
name := "monohash"
version := "0.8.0"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "4.10.5" % Test,
)

crossPaths := false
autoScalaLibrary := false

javacOptions in doc := Seq(
  "-encoding", "UTF-8",
  "-source", "8",
)
javacOptions := (javacOptions in doc).value ++ (Seq(
  "-deprecation",
  "-parameters",
  "-target", "8",
  "-Xlint",
) ++ sys.env.get("JAVA8_HOME").map { jdk8 =>
  Seq("-bootclasspath", jdk8 + "/jre/lib/rt.jar")
}.getOrElse(Nil))

scalaVersion := "2.13.4" // for tests only
scalacOptions := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:_",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:_",
  "-Yrangepos",
)

fork in Test := true

jacocoReportSettings := JacocoReportSettings(
  // output to HTML for humans, and XML for Codecov
  formats = Seq(JacocoReportFormats.ScalaHTML, JacocoReportFormats.XML),
)

Global / onChangedBuildSource := ReloadOnSourceChanges
