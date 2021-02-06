organization := "com.oradian.infra.single"
name := "single-java"

libraryDependencies ++= Seq(
)

crossPaths := false
autoScalaLibrary := false

doc / javacOptions := Seq(
  "-encoding", "UTF-8",
  "-source", "8",
)
javacOptions := (doc / javacOptions).value ++ (Seq(
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

run / fork := true

Global / onChangedBuildSource := ReloadOnSourceChanges
