scalaVersion := "2.12.13"
scalacOptions := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:_",
  "-unchecked",
)

libraryDependencies += "nu.studer" % "java-ordered-properties" % "1.0.4"
