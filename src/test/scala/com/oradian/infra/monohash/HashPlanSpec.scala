package com.oradian.infra.monohash

import java.io.IOException
import java.nio.charset.MalformedInputException
import java.util.{Arrays => JArrays}

import com.oradian.infra.monohash.param.LogLevel

class HashPlanSpec extends Specification {
  sequential

  private[this] def test(plan: String, logger: Logger = new LoggingLogger(LogLevel.TRACE)): HashPlan =
    HashPlan.apply(logger, new File(resources + plan + "/.monohash"))

  "Enforces UTF-8" >> {
    "Explodes on invalid encoding" >> {
      test("hashPlan/00-incorrect-encoding") must throwA[MalformedInputException]
    }
    "Accepts Unicode via UTF-8" >> {
      test("hashPlan/01-correct-encoding").whitelist ==== JArrays.asList(resources + "hashPlan/01-correct-encoding/$ £ ¥ €")
    }
  }

  "Ability to override default basePath" >> {
    test("basePath/00-default").basePath ==== resources + "basePath/00-default/"
    test("basePath/01-empty") must throwA(new IllegalArgumentException(s"base path override cannot be empty"))
    test("basePath/02-override").basePath ==== resources + "basePath/01-empty/"
    test("basePath/03-duplicate").basePath ==== resources + "basePath/02-override/"
    test("basePath/04-multiple") must throwA(new IllegalArgumentException("There is more than one base path override: '@../02-override/', '@../03-duplicate/'"))
    test("basePath/05-non-existent").basePath ==== resources + "basePath/05-non-existent/no-show/"
  }

  "Use canonical paths throughout hash plan" >> {
    test("basePath/01-empty/../00-default").basePath ==== resources + "basePath/00-default/"
  }

  "Blacklisting support" >> {
    test("blacklist/00-default").blacklist ==== null
    test("blacklist/01-empty") must throwA(new IllegalArgumentException("blacklist pattern cannot be empty"))
    test("blacklist/02-simple").blacklist.pattern ==== "\\Q.monohash\\E"
    test("blacklist/03-wildcards").blacklist.pattern ==== """.*|\Q.\E.*|.*\Q.\E.*"""
  }

  "Whitelisting support" >> {
    test("whitelist/00-default").whitelist ==== JArrays.asList(resources + "whitelist/00-default/")
    test("whitelist/01-dot").whitelist ==== JArrays.asList(resources + "whitelist/01-dot/")
    test("whitelist/02-non-existent").whitelist ==== JArrays.asList(resources + "whitelist/02-non-existent/no-show/")
    test("whitelist/03-escapes").whitelist ==== JArrays.asList(
      resources + "whitelist/03-escapes/!important!.txt",
      resources + "whitelist/03-escapes/@sbt.boot.properties",
      resources + "whitelist/03-escapes/#1.log",
    )

    "Duplicate whitelist entries" >> {
      val logger = new LoggingLogger(LogLevel.TRACE)
      test("whitelist/04-duplicates", logger).whitelist ==== JArrays.asList(resources + "whitelist/04-duplicates/../01-dot/")
      logger.messages(LogLevel.WARN) ==== Seq(
        LogMsg(LogLevel.WARN, "Whitelist entry '../01-dot/' is a duplicate - please review the [hash plan]"),
        LogMsg(LogLevel.WARN, "Whitelist entry '../01-dot/' is a duplicate - please review the [hash plan]"),
      )
    }
  }

  "Process directory as empty hash plan" >> {
    val logger = new LoggingLogger(LogLevel.TRACE)
    val hashPlan = HashPlan.apply(logger, new File(resources))
    hashPlan.basePath ==== resources
    hashPlan.whitelist ==== JArrays.asList(resources)
    hashPlan.blacklist ==== null
  }

  "HashPlans must be rooted in reality" >> {
    val logger = new LoggingLogger(LogLevel.TRACE)

    HashPlan.apply(logger, new File("\u0000")) must
      throwAn[IOException]("Could not resolve canonical path for \\[hash plan\\]: '\u0000'")

    HashPlan.apply(logger, new File("\u0000"), Array.emptyByteArray) must
      throwAn[IOException]("Could not resolve canonical path for \\[hash plan\\]'s parent: '\u0000'")
  }
}
