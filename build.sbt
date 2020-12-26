organization := "com.oradian.infra"
name := "monohash"

libraryDependencies ++= Seq(
  "org.bouncycastle" %  "bcprov-jdk15on"         % "1.67"   % Test,
  "com.oradian.util" %  "exit-denied"            % "0.1.0"  % Test,
  "org.specs2"       %% "specs2-core"            % "4.10.5" % Test,
  "com.topdesk"      %  "time-transformer-agent" % "1.3.0"  % Test,
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
  def opts(extraOpts: Seq[String]) = ForkOptions(
    javaHome = (Test / javaHome).value,
    outputStrategy = (Test / outputStrategy).value,
    bootJars = Vector.empty,
    workingDirectory = Some((Test / baseDirectory).value),
    runJVMOptions = extraOpts.toVector ++ (Test / javaOptions).value,
    connectInput = false,
    envVars = (Test / envVars).value,
  )

  val timeTransformAgent =
    (Test / externalDependencyClasspath).value.files
      .find(_.getName.contains("time-transformer-agent"))
      .getOrElse(sys.error("Could not locate time-transformer-agent in the classpath"))

  val exitDenied =
    (Test / externalDependencyClasspath).value.files
      .find(_.getName.contains("exit-denied"))
      .getOrElse(sys.error("Could not locate exit-denied in the classpath"))

  // Run each test in separate forked JVM so that we can e.g.
  // - screw up Security Providers
  // - test loading of corrupted monohash.properties (which destroys all param classes)
  // - use per-test JVM agents (i.e. currently time travelling)
  // - fiddle with Singletons via reflection without cleanup ...

  (Test / definedTests).value.sortBy(_.name).map { test =>
    val agentLibs = (
      (if (test.name endsWith ".util.FormatSpec") Seq(timeTransformAgent) else Nil) ++
      (if (test.name endsWith ".MonoHashSpec") Seq(exitDenied) else Nil)
    ).map(lib => "-javaagent:" + lib.getPath)

    Tests.Group(test.name, Seq(test), Tests.SubProcess(opts(agentLibs)))
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
