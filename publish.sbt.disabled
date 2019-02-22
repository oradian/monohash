import xerial.sbt.Sonatype._

sonatypeProjectHosting := Some(GitHubHosting("oradian", "monohash", "marko.elezovic@oradian.com"))
developers := List(
  Developer(id="melezov", name="Marko Elezovic", email="marko.elezovic@oradian.com", url=url("https://github.com/melezov"))
)

licenses += (("MIT", url("https://opensource.org/licenses/MIT")))
startYear := Some(2019)

sonatypeProfileName := "oradian"
publishMavenStyle := true

publishTo := Some(
  if (version.value endsWith "-SNAPSHOT") {
    Opts.resolver.sonatypeSnapshots
  } else {
    Opts.resolver.sonatypeStaging
  }
)
