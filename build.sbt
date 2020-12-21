organization := "com.oradian.infra"
name := "monohash"

libraryDependencies ++= Seq(
  "org.specs2"       %% "specs2-core"    % "4.10.5" % Test,
  "org.bouncycastle" %  "bcprov-jdk15on" % "1.67"   % Test,
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

Test / fork := true
Test / parallelExecution := false
Test / testForkedParallel := false
Test / testGrouping := {
  val opts = ForkOptions(
    javaHome = (Test / javaHome).value,
    outputStrategy = (Test / outputStrategy).value,
    bootJars = Vector.empty,
    workingDirectory = Some((Test / baseDirectory).value),
    runJVMOptions = (Test / javaOptions).value.toVector,
    connectInput = (Test / connectInput).value,
    envVars = (Test / envVars).value,
  )
  // run each test in separate forked JVM so that we can e.g.
  // - screw up Security Providers
  // - test loading of corrupted monohash.properties
  // - fiddle with Singletons via reflection ...
  (Test / definedTests).value map { test =>
    Tests.Group(test.name, Seq(test), Tests.SubProcess(opts))
  }
}

jacocoReportSettings := JacocoReportSettings(
  // output to HTML for humans, and XML for Codecov
  formats = Seq(JacocoReportFormats.ScalaHTML, JacocoReportFormats.XML),
)

Global / onLoad := { state =>
  val propertiesFile = baseDirectory.value /
    "src" / "main" / "resources" /
    "com" / "oradian" / "infra" / "monohash" / "param" / "monohash.properties"
  PropertiesVersion.update(sLog.value, propertiesFile, version.value)
  state
}

Global / onChangedBuildSource := ReloadOnSourceChanges
