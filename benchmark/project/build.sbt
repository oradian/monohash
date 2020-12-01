scalaVersion := "2.12.12"
scalacOptions := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:_",
  "-unchecked",
)

lazy val stableLibrary = settingKey[ModuleID]("Last published (stable) MonoHash library")
stableLibrary := {
  val readme = IO.read(baseDirectory.value /  ".." / ".." / "README.md")
  val stableLibraryPattern = """(?s).*libraryDependencies \+= "([^"]+)" % "([^"]+)" % "([^"]+)".*""".r
  readme match {
    case stableLibraryPattern(group, artifact, version) => group % artifact % version
    case _ => sys.error("Could not extract " + stableLibrary.toString)
  }
}

libraryDependencies += stableLibrary.value
