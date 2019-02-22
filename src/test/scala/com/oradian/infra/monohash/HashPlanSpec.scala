package com.oradian.infra.monohash

import java.io.File
import java.util.Arrays

class HashPlanSpec extends MutableSpecification {
  private[this] val logger = new LoggingLogger

  private[this] def test(plan: String): HashPlan =
    HashPlan.apply(logger, new File(resources + plan + "/.monohash"))

  "Use canonical paths in hash plan" >> {
    test("basePath/01-empty/../00-default").plan ==== new File(resources + "basePath/00-default/.monohash")
  }

  "Use canonical paths in hash plan" >> {
    test("basePath/01-empty/../00-default").plan ==== new File(resources + "basePath/00-default/.monohash")
  }

  "Ability to override default basePath" >> {
    test("basePath/00-default").basePath ==== resources + "basePath/00-default/"
    test("basePath/01-empty") must throwA(new IllegalArgumentException(s"base path override cannot be empty!"))
    test("basePath/02-override").basePath ==== resources + "basePath/01-empty/"
    test("basePath/03-duplicate").basePath ==== resources + "basePath/02-override/"
    test("basePath/04-multiple") must throwA(new IllegalArgumentException("There is more than one base path override: '@../02-override/', '@../03-duplicate/'"))
    test("basePath/05-non-existent").basePath ==== resources + "basePath/05-non-existent/no-show/"
  }

  "Blacklisting support" >> {
    test("blacklist/00-default").blacklist ==== null
    test("blacklist/01-empty") must throwA(new IllegalArgumentException("blacklist pattern cannot be empty!"))
    test("blacklist/02-simple").blacklist.pattern ==== "\\Q.monohash\\E"
    test("blacklist/03-wildcards").blacklist.pattern ==== """.*|\Q.\E.*|.*\Q.\E.*"""
  }

  "Whitelisting support" >> {
    test("whitelist/00-default").whitelist ==== Arrays.asList(resources + "whitelist/00-default/")
    test("whitelist/01-dot").whitelist ==== Arrays.asList(resources + "whitelist/01-dot/")
    test("whitelist/02-non-existent").whitelist ==== Arrays.asList(resources + "whitelist/02-non-existent/no-show/")
    test("whitelist/03-escapes").whitelist ==== Arrays.asList(resources + "whitelist/03-escapes/!important!.txt", resources + "whitelist/03-escapes/@sbt.boot.properties", resources + "whitelist/03-escapes/#1.log")
  }
}
