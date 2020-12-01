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

  def previousHash: Either[String, Option[String]] = {
    val previousJars = lib.listFiles("*.jar")
    if (previousJars.isEmpty) {
      Right(None)
    } else if (previousJars.size == 1) {
      val JarPattern = "monohash-([0-9a-f]+).jar".r
      previousJars.head.getName match {
        case JarPattern(version) => Right(Some(version))
        case other => Left("Could not parse previous lib version: " + other)
      }
    } else {
      Left("Multiple previous libs found: " + previousJars.mkString("\n  ", "\n  ", ""))
    }
  }

  def build(previousHash: Option[String]): Unit = {
    rootHasher.value.onDiff(previousHash) { hash =>
      Resolvers.run(Some(baseDirectory.value / ".."),
        "sbt",
        s"""set version := "$hash"""",
        "clean",
        "package",
      )

      val src = baseDirectory.value / ".." / "target" / s"monohash-$hash.jar"
      if (!src.isFile) {
        sLog.value.error("Could not build new monohash")
        false
      } else {
        val lib = baseDirectory.value / "lib"
        val cleanup = lib.listFiles("*.jar") filter { !_.delete() }
        if (cleanup.nonEmpty) {
          sLog.value.error("Could not cleanup previous libs:" + cleanup.mkString("\n  ", "\n  ", ""))
          false
        } else {
          IO.move(src, lib / s"monohash-$hash.jar")
          true
        }
      }
    }
  }

  previousHash match {
    case Left(error) => sLog.value.error(error)
    case Right(ph) => build(ph)
  }
}

enablePlugins(JmhPlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges
