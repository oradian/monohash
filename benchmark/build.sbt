import com.oradian.infra.monohash.diff.Diff
import com.oradian.infra.{monohash => mh}

organization := "com.oradian.infra"
name := "monohash-benchmark"
version := "0.0.0-SNAPSHOT"

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

scalaVersion := "2.13.4"
scalacOptions := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:_",
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:_",
  "-Yrangepos",
)

lazy val rootHasher = settingKey[RootHasher]("MonoHasher for root project")
rootHasher := {
  // you would usually override logging level to e.g. Level.Warn to make MonoHash less chatty
  val logger = new SbtLogger(sLog.value, Level.Info)
  val hashPlan = baseDirectory.value / ".." / ".monohash"
  val exportFile = baseDirectory.value / "target" / "monohash.export"
  new RootHasher(logger, hashPlan, exportFile)
}

lazy val loadLib = taskKey[Unit]("Package and load root project into harness")
loadLib := {
  val lib = baseDirectory.value / "lib"
  val cachedBuilds = baseDirectory.value / "target" / "cached-builds"

  def buildWithCaching(cacheTest: File): Boolean =
    if (cacheTest.isFile) {
      sLog.value.warn("Cached build already exists: " + cacheTest)
      true
    } else {
      sLog.value.warn("Cached build does not exist, building ...")
      val homePath = baseDirectory.value / ".."
      Resolvers.run(Some(homePath), "sbt", "clean", "package")
      val target = homePath / "target"
      val jars = IO.listFiles(target, "*.jar")
      if (jars.length == 0) {
        sLog.value.error("Cannot locate newly built jar in: " + target)
        false
      } else if (jars.length > 1) {
        sLog.value.error("Multiple jars found in: " + target)
        false
      } else {
        IO.move(jars.head, cacheTest)
        sLog.value.warn("Cached new build to: " + cacheTest)
        true
      }
    }

  rootHasher.value.onDiff { hash =>
    val jarName = s"monohash-$hash.jar"
    val libDst = lib / jarName
    if (libDst.isFile) {
      sLog.value.warn("Current built is up to date: " + libDst)
      true
    } else {
      val cacheTest = cachedBuilds / jarName
      buildWithCaching(cacheTest) && {
        val cleanup = lib.listFiles("*.jar") filter { !_.delete() }
        if (cleanup.nonEmpty) {
          sLog.value.error("Could not cleanup previous libs:" + cleanup.mkString("\n  ", "\n  ", ""))
          false
        } else {
          sLog.value.warn("Activating jar by copying it to libs: " + libDst)
          IO.copyFile(cacheTest, libDst)
          true
        }
      }
    }
  }
}

enablePlugins(JmhPlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges
