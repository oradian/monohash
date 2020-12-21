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

//Test / jacocoIncludes := Seq(
//  "com.oradian.infra.monohash.param.CmdLineParser*"
//)
jacocoReportSettings := JacocoReportSettings(
  // output to HTML for humans, and XML for Codecov
  formats = Seq(JacocoReportFormats.ScalaHTML, JacocoReportFormats.XML),
)

//Global / testOptions := Seq(Tests.Filter { test =>
//  test.contains("CmdLineParser")
//})

Global / onLoad := { state =>
  val propertiesFile = baseDirectory.value /
    "src" / "main" / "resources" /
    "com" / "oradian" / "infra" / "monohash" / "param" / "monohash.properties"
  PropertiesVersion.update(sLog.value, propertiesFile, version.value)
  state
}

Global / onChangedBuildSource := ReloadOnSourceChanges

//enablePlugins(SbtProguard)
//proguardVersion in Proguard := "7.0.1"
//proguardOptions in Proguard += ProguardOptions.keepMain("com.oradian.infra.monohash.MonoHash")
////proguardOptions in Proguard ++= Seq("-dontnote", "-dontwarn", "-ignorewarnings", "-dontobfuscate")
//proguardOptions in Proguard ++= Seq("@d:\\Code\\monohash\\monohash.pro")
//
//
//lazy val pd1 = taskKey[Unit]("Diff proguards 1")
//
//pd1 := {
//  val ws = file("d:/mh")
//  IO.delete(ws)
//
//  val diff = ws / "diff"
//  diff.mkdirs()
//
//  import sys.process._
//  Process(command = Seq("git", "init"), cwd = diff).!
//
//  val src = file("target") / "monohash-0.8.0-SNAPSHOT.jar"
//  val dst = ws / src.name.replace("jar", "zip")
//
//  Process(command = Seq("python", "d:/Scala/Krakatau/disassemble.py", "-out", dst.getPath, src.getPath)).!
//  IO.unzip(dst, diff)
//
//  Process(command = Seq("git", "add", "--all"), cwd = diff).!
//  Process(command = Seq("git", "commit", "-m", "init"), cwd = diff).!
//
//  val com = diff / "com"
//  IO.delete(com)
//
//  val srcProguard = file("target") / "monohash-0.8.0-SNAPSHOT-pro.jar"
//  val dstProguard = ws / src.name.replace(".jar", "-pro.zip")
//  Process(command = Seq("python", "d:/Scala/Krakatau/disassemble.py", "-out", dstProguard.getPath, srcProguard.getPath)).!
//  IO.unzip(dstProguard, diff)
//
//  Process(command = Seq("git", "add", "--all"), cwd = diff).!
//}
