package com.oradian.infra.monohash

class WhiteWalkerSpec extends Specification {
  private[this] val logger = new LoggingLogger
  private[this] val algorithm = new Algorithm("SHA-1")

  private[this] def test(path: String)(expectedFiles: String*): MatchResult[Seq[(String, Seq[Byte])]] = {
    val planPath = new File(resources + s"whiteWalker/$path/.monohash")
    val hashPlan = HashPlan.apply(logger, planPath)
    val actualHashResults = WhiteWalker.apply(logger, hashPlan, algorithm, 1).toMap.asScala.view.mapValues(_.toSeq).toSeq

    val expectedHashResults = expectedFiles.map { file =>
      val nameHash = algorithm.init(() => ???)
        .digest(file.replaceFirst(".*/", "").getBytes("UTF-8"))
      (file, nameHash.toSeq)
    }

    actualHashResults ==== expectedHashResults
  }

  "Simple-patterns" >> test("00-simple-patterns")(
    "2/d", "2/f",
  )

  "Wildcard-patterns" >> test("01-wildcard-patterns")(
    "1/a", "1/c",
  )

  "Folder whitelisting" >> test("02-folder-whitelisting")(
    "whitelist-with-slash/b",
    "whitelist-without-slash/a",
  )

  "Folder blacklisting" >> test("03-folder-blacklisting")(
    "normal/c",
  )

  "All by defaults" >> test("04-all-by-default")(
    "harness/1/a", "harness/1/b", "harness/1/c",
    "harness/2/d", "harness/2/e", "harness/2/f",
  )

  "Just dots" >> test("05-just-dots")(
    "harness/1/a", "harness/1/b", "harness/1/c",
    "harness/2/d", "harness/2/e", "harness/2/f",
  )
}
