package com.oradian.infra.monohash

import java.security.Security
import java.util.UUID

import org.specs2.execute.Result

class CmdLineParserSpec extends Specification {
  sequential

  private[this] def testPL(args: String*)
                          (parseChk: (CmdLineParser => MatchResult[_])*)
                          (logChk: (LoggingLogger => MatchResult[_])*): Result = {
    val logger = new LoggingLogger
    val parser = new CmdLineParser(args.toArray, _ => logger)
    parseChk.map(_.apply(parser))
    logChk.map(_.apply(logger))
  }

  private[this] def test(args: String*)(parseChk: (CmdLineParser => MatchResult[_])*): Result =
    testPL(args: _*)(parseChk: _*)()

  "Empty argument handling" >> {
    "No args provided" >> {
      test()() must throwAn[ExitException]("""You did not specify the \[hash plan file\]""")
    }
    "Hash plan file is empty" >> {
      test("")() must throwAn[ExitException]("""Provided \[hash plan file\] was an empty string""")
    }
    "Export file is empty" >> {
      test("x", "")() must throwAn[ExitException]("""Provided \[export file\] was an empty string""")
    }
  }

  private[this] val fakePlan = UUID.randomUUID().toString
  private[this] val fakeExport = UUID.randomUUID().toString

  "Simple hash plan" >> {
    test(fakePlan)(
      _.hashPlanPath ==== fakePlan,
      _.exportPath ==== null,
    )

    test(fakePlan, fakeExport)(
      _.hashPlanPath ==== fakePlan,
      _.exportPath ==== fakeExport,
    )
  }

  "Default options + logging check" >> {
    testPL(fakePlan)(
      _.logLevel ==== Logger.Level.INFO,
      _.algorithm.name ==== "SHA-1",
      _.concurrency must be > 0,
      _.verification ==== Verification.OFF,
      _.hashPlanPath ==== fakePlan,
      _.exportPath ==== null,
    )(
      _.messages() ==== Seq(
        LogMsg(Logger.Level.DEBUG, s"Parsing arguments:\n$fakePlan"),
        LogMsg(Logger.Level.DEBUG, s"Using log level: info"),
        LogMsg(Logger.Level.DEBUG, s"Using algorithm: SHA-1"),
        LogMsg(Logger.Level.DEBUG, s"Using concurrency: " + CmdLineParser.getCurrentDefaultConcurrency),
        LogMsg(Logger.Level.DEBUG, s"Using verification: off"),
        LogMsg(Logger.Level.TRACE, s"Remaining arguments after processing additional options:\n$fakePlan")
      )
    )
  }

  "Option parsing" >> {
    "Log level parsing" >> {
      test("-l")() must throwAn[ExitException]("Missing value for log level, last argument was an alone '-l'")
      test("-l", "")() must throwAn[ExitException]("Empty value provided for log level")
      test("-l", "--")() must throwAn[ExitException]("Missing value for log level, next argument was the stop flag '--'")
      test("-l", fakePlan)() must throwAn[ExitException](s"Unknown log level: '$fakePlan', supported log levels are: off, error, warn, info, debug, trace")
      test("-l", "xxx", fakePlan)() must throwAn[ExitException]("Unknown log level: 'xxx', supported log levels are: off, error, warn, info, debug, trace")
      test("-l", "off", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.exportPath ==== null,
      )
      test("-l", "OfF", "--", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.exportPath ==== null,
      )
      test("-l", "off", "-l", "error", fakePlan)(
        _.logLevel ==== Logger.Level.ERROR,
        _.exportPath ==== null,
      )
    }

    "Algorithm parsing" >> {
      test("-a")() must throwAn[ExitException]("Missing value for algorithm, last argument was an alone '-a'")
      test("-a", "")() must throwAn[ExitException]("Empty value provided for algorithm")
      test("-a", "--")() must throwAn[ExitException]("Missing value for algorithm, next argument was the stop flag '--'")
      test("-a", fakePlan)() must throwAn[ExitException](s"Algorithm '$fakePlan' is not supported. Supported algorithms: .*Git.*SHA.*OID")
      test("-axxx", fakePlan)() must throwAn[ExitException]("Algorithm 'xxx' is not supported. Supported algorithms:")
      test("-a", "gIt", fakePlan)(
        _.algorithm.name ==== "Git",
        _.exportPath ==== null,
      )
      test("-aShA-256", "--", fakePlan)(
        _.algorithm.name ==== "SHA-256",
        _.exportPath ==== null,
      )
      test("-a", "SHA-256", "-a", "sha-512", fakePlan)(
        _.algorithm.name ==== "SHA-512",
        _.exportPath ==== null,
      )
    }

    "Concurrency parsing" >> {
      test("-c")() must throwAn[ExitException]("Missing value for concurrency, last argument was an alone '-c'")
      test("-c", "")() must throwAn[ExitException]("Empty value provided for concurrency")
      test("-c", "--")() must throwAn[ExitException]("Missing value for concurrency, next argument was the stop flag '--'")
      test("-c", fakePlan)() must throwAn[ExitException](s"Invalid concurrency setting: '$fakePlan', expecting a positive integer")
      test("-cxxx", fakePlan)() must throwAn[ExitException]("Invalid concurrency setting: 'xxx', expecting a positive integer")
      test("-c", "0", fakePlan)() must throwAn[ExitException]("Concurrency must be a positive integer, got: '0'")
      test("-c", "-12", fakePlan)() must throwAn[ExitException]("Concurrency must be a positive integer, got: '-12'")
      test("-c", "123", fakePlan)(
        _.concurrency ==== 123,
        _.exportPath ==== null,
      )
      test("-c1234", "--", fakePlan)(
        _.concurrency ==== 1234,
        _.exportPath ==== null,
      )
      test("-c", "1", "-c", "12345", fakePlan)(
        _.concurrency ==== 12345,
        _.exportPath ==== null,
      )
    }

    "Verification parsing" >> {
      test("-v")() must throwAn[ExitException]("Missing value for verification, last argument was an alone '-v'")
      test("-v", "")() must throwAn[ExitException]("Empty value provided for verification")
      test("-v", "--")() must throwAn[ExitException]("Missing value for verification, next argument was the stop flag '--'")
      test("-v", fakePlan)() must throwAn[ExitException](s"Unknown verification: '$fakePlan', supported verifications are: off, warn, require")
      test("-vxxx", fakePlan)() must throwAn[ExitException]("Unknown verification: 'xxx', supported verifications are: off, warn, require")
      test("-v", "warn", fakePlan)(
        _.verification ==== Verification.WARN,
        _.exportPath ==== null,
      )
      test("-vWaRn", "--", fakePlan)(
        _.verification ==== Verification.WARN,
        _.exportPath ==== null,
      )
      test("-v", "warn", "-v", "require", fakePlan, fakeExport)(
        _.verification ==== Verification.REQUIRE,
        _.exportPath ==== fakeExport,
      )
    }

    "Verification 'require' demands an export argument" >> {
      test("-vrequire", fakePlan)() must
        throwA[ExitException]("""\[verification\] is set to 'require', but \[export file\] was not provided""")
    }

    "Complex additional options parsing with overrides" >> {
      test("-l", "off", "-a", "SHA-256", "-c", "2", "-aGIT", "-v", "warn", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.algorithm.name ==== "Git",
        _.concurrency ==== 2,
        _.verification ==== Verification.WARN,
        _.hashPlanPath ==== fakePlan,
        _.exportPath ==== null,
      )
      test("-vWaRn", "-a", "MD5", "-c123", "-l", "ERROR", "-v", "Require", "-aSHA-384", "--", "-aplan", "-vexport")(
        _.logLevel ==== Logger.Level.ERROR,
        _.algorithm.name ==== "SHA-384",
        _.concurrency ==== 123,
        _.verification ==== Verification.REQUIRE,
        _.hashPlanPath ==== "-aplan",
        _.exportPath ==== "-vexport",
      )
    }
  }

  "Too many arguments" >> {
    test(fakePlan, fakeExport, "xyzzy")() must
      throwA[ExitException]("""There are too many arguments provided after \[hash plan file\] and \[export file\], first was: 'xyzzy'""")
  }

  "No algorithms check" >> {
    val providers = Security.getProviders()
    try {
      for (provider <- providers) {
        // Can prolly cause some flip-flops in CI tests.
        // Using `sequential` so that this is the last test in the suite.
        // Perhaps the test is not worth-it and can remain as a commented out expectation
        Security.removeProvider(provider.getName)
      }
      test()() must throwAn[ExitException]("Algorithm 'SHA-1' is not supported. Supported algorithms: <none>")
      test("-agIt")() must throwAn[ExitException]("Algorithm 'gIt' is not supported. Supported algorithms: <none>")
      test("-aSHaaAaaA")() must throwAn[ExitException]("Algorithm 'SHaaAaaA' is not supported. Supported algorithms: <none>")
    } finally {
      for (provider <- providers) {
        Security.addProvider(provider)
      }
      // test restoration of sanity
      test("-a", "Ẓ̟̙̪͙͓́ͬͫͮ̿͒̈͊͊̿ͧ̂̎ͬ́͞A̤͇̺̲̪̖̥̠̳̘̻̬͍͙̣͎̝͔͋̅͒̏͗͂̑͆ͬͣ͒̾̚̕͞L̛̎̉͂ͪͫ̌ͣ̐̿͑ͩ̽̐̍͆̆͆ͦ҉̥̬̠̞̗͓̻̩͟Ġͫ͋ͪͧ̂̉͗̀̚̚҉̭͎̰͎͓̗̗̺̲̰͚̻̼̰̜͉͟O̖̙̝̥̳͕̪̥͖͍̺͊ͮͪ̾ͤ̓̏͗̐̊̀̃͊͘̕")() must throwAn[ExitException]("""Algorithm 'Ẓ̟̙̪͙͓́ͬͫͮ̿͒̈͊͊̿ͧ̂̎ͬ́͞A̤͇̺̲̪̖̥̠̳̘̻̬͍͙̣͎̝͔͋̅͒̏͗͂̑͆ͬͣ͒̾̚̕͞L̛̎̉͂ͪͫ̌ͣ̐̿͑ͩ̽̐̍͆̆͆ͦ҉̥̬̠̞̗͓̻̩͟Ġͫ͋ͪͧ̂̉͗̀̚̚҉̭͎̰͎͓̗̗̺̲̰͚̻̼̰̜͉͟O̖̙̝̥̳͕̪̥͖͍̺͊ͮͪ̾ͤ̓̏͗̐̊̀̃͊͘̕' is not supported. Supported algorithms: .*Git.*SHA.*OID""")
    }
  }
}
