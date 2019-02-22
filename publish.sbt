publishTo := Some(
  if (version.value endsWith "-SNAPSHOT")
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

licenses += (("MIT", url("https://opensource.org/licenses/MIT")))
startYear := Some(2019)

scmInfo := Some(ScmInfo(
  url("https://github.com/oradian/monohash")
, "scm:git:https://github.com/oradian/monohash.git"
, Some("scm:git:git@github.com:oradian/monohash.git")
))

pomExtra :=
<developers>
  <developer>
    <id>melezov</id>
    <name>Marko Elezovic</name>
    <url>https://github.com/melezov</url>
  </developer>
</developers>

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }

homepage := Some(url("https://github.com/oradian/monohash"))
