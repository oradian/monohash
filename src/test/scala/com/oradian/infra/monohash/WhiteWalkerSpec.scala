package com.oradian.infra.monohash

import java.io.File
import java.security.MessageDigest

import org.specs2.matcher.MatchResult

import scala.jdk.CollectionConverters._

class WhiteWalkerSpec extends MutableSpecification {
  private[this] val logger = new LoggingLogger
  private[this] val Algorithm = "SHA-1"

  private def test(path: String)(expectedFiles: String*): MatchResult[Seq[(String, Seq[Byte])]] = {
    val planPath = new File(resources + s"whiteWalker/$path/.monohash")
    val hashPlan = HashPlan.apply(logger, planPath)
    val actualHashResults = WhiteWalker.apply(logger, Algorithm, hashPlan, 1).asScala.toSeq.map(kv => (kv.getKey, kv.getValue.toSeq))

    val expectedHashResults = expectedFiles.toSeq map { file =>
      val nameHash = MessageDigest.getInstance(Algorithm)
        .digest(file.replaceFirst(".*/", "").getBytes("UTF-8"))
      (file, nameHash.toSeq)
    }

    actualHashResults ==== expectedHashResults
  }

  "simple-patterns" >> test("00-simple-patterns")(
    "2/d", "2/f",
  )

  "wildcard-patterns" >> test("01-wildcard-patterns")(
    "1/a", "1/c",
  )

  "folder whitelisting" >> test("02-folder-whitelisting")(
    "whitelist-with-slash/b",
    "whitelist-without-slash/a",
  )

  "folder blacklisting" >> test("03-folder-blacklisting")(
    "normal/c",
  )

  "all by defaults" >> test("04-all-by-default")(
    "harness/1/a", "harness/1/b", "harness/1/c",
    "harness/2/d", "harness/2/e", "harness/2/f",
  )

  "just dots" >> test("05-just-dots")(
    "harness/1/a", "harness/1/b", "harness/1/c",
    "harness/2/d", "harness/2/e", "harness/2/f",
  )
}
