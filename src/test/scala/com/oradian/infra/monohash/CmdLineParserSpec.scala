package com.oradian.infra.monohash

import java.util.{Arrays => JArrays}

import com.oradian.infra.monohash.param._

class CmdLineParserSpec extends Specification {
  sequential

  private[this] def testLogAndParse(args: String*)
                          (logChk: (LoggingLogger => MatchResult[_])*)
                          (parseChk: (MonoHashBuilder#Ready => MatchResult[_])*): Seq[MatchResult[_]] = {
    val ready = CmdLineParser.parse(JArrays.asList(args: _*), logLevel => new LoggingLogger(logLevel))
    val logger = ready.logger.asInstanceOf[LoggingLogger] // pull it out again
    logChk.map(_.apply(logger))
    parseChk.map(_.apply(ready))
  }

  private[this] def testParse(args: String*)(parseChk: (MonoHashBuilder#Ready => MatchResult[_])*): Seq[MatchResult[_]] =
    testLogAndParse(args: _*)()(parseChk: _*)

  "Empty argument handling" >> {
    "No args provided" >> {
      testParse()() must throwAn[ExitException]("""You did not specify the \[hash plan file\]""")
    }
    "Hash plan file is empty" >> {
      testParse("")() must throwAn[ExitException]("""Provided \[hash plan file\] was an empty string""")
    }
    "Export file is empty" >> {
      testParse("x", "")() must throwAn[ExitException]("""Provided \[export file\] was an empty string""")
    }
  }

  private[this] val fakePlan = java.util.UUID.randomUUID().toString
  private[this] val fakeExport = java.util.UUID.randomUUID().toString

  private[this] val fakePlanFile = new File(fakePlan)
  private[this] val fakeExportFile = new File(fakeExport)

  "Simple hash plan" >> {
    testParse(fakePlan)(
      _.hashPlan ==== fakePlanFile,
      _.export ==== null,
    )

    testParse(fakePlan, fakeExport)(
      _.hashPlan ==== fakePlanFile,
      _.export ==== fakeExportFile,
    )
  }

  "Default options" >> {
    testLogAndParse(fakePlan)()(
      _.algorithm must beTheSameAs(Algorithm.DEFAULT),
      _.concurrency must beTheSameAs(Concurrency.DEFAULT),
      _.verification must beTheSameAs(Verification.DEFAULT),
      _.hashPlan ==== fakePlanFile,
      _.export ==== null,
    )
  }

  "Default options + trace logging check" >> {
    testLogAndParse("-ltrace", fakePlan)(
      _.messages() ==== Seq(
        LogMsg(LogLevel.TRACE, s"Parsed log level: trace"),
        LogMsg(LogLevel.DEBUG, s"Using log level: trace"),
        LogMsg(LogLevel.DEBUG, s"Parsing arguments:\n  -ltrace\n  $fakePlan"),
        LogMsg(LogLevel.DEBUG, s"Using algorithm: SHA-1"),
        LogMsg(LogLevel.DEBUG, s"Using concurrency: " + Concurrency.cpuRelative(1.0).getConcurrency),
        LogMsg(LogLevel.DEBUG, s"Using verification: off"),
        LogMsg(LogLevel.TRACE, s"Remaining arguments after processing options:\n  $fakePlan"),
      )
    )()
  }

  "Option parsing" >> {
    "Log level parsing" >> {
      testParse("-l")() must throwAn[ExitException]("Missing value for log level, last argument was an alone '-l'")
      testParse("-l", "")() must throwAn[ExitException]("Empty value provided for log level")
      testParse("-l", "--")() must throwAn[ExitException]("Missing value for log level, next argument was the stop flag '--'")
      testParse("-l", fakePlan)() must throwAn[ExitException](s"Unknown log level: '$fakePlan', supported log levels are: off, error, warn, info, debug, trace")
      testParse("-l", "xxx", fakePlan)() must throwAn[ExitException]("Unknown log level: 'xxx', supported log levels are: off, error, warn, info, debug, trace")
      testLogAndParse("-l", "DEBUG", fakePlan)(
        _.logLevel ==== LogLevel.DEBUG
      )(
        _.export ==== null
      )
      testLogAndParse("-l", "OfF", "--", fakePlan)(
        _.logLevel ==== LogLevel.OFF
      )(
        _.export ==== null
      )
      testLogAndParse("-l", "off", "-l", "error", fakePlan)(
        _.logLevel ==== LogLevel.ERROR
      )(
        _.export ==== null
      )
    }

    "Algorithm parsing" >> {
      testParse("-a")() must throwAn[ExitException]("Missing value for algorithm, last argument was an alone '-a'")
      testParse("-a", "")() must throwAn[ExitException]("Empty value provided for algorithm")
      testParse("-a", "--")() must throwAn[ExitException]("Missing value for algorithm, next argument was the stop flag '--'")
      testParse("-a", fakePlan)() must throwAn[ExitException](s"Algorithm '$fakePlan' is not supported. Supported algorithms: .*GIT.*SHA.*OID")
      testParse("-axxx", fakePlan)() must throwAn[ExitException]("Algorithm 'xxx' is not supported. Supported algorithms:")
      testParse("-a", "gIt", fakePlan)(
        _.algorithm.name ==== "GIT",
        _.export ==== null,
      )
      testParse("-aShA-256", "--", fakePlan)(
        _.algorithm.name ==== "SHA-256",
        _.export ==== null,
      )
      testParse("-a", "SHA-256", "-a", "sha-512", fakePlan)(
        _.algorithm.name ==== "SHA-512",
        _.export ==== null,
      )
    }

    "Concurrency parsing" >> {
      testParse("-c")() must throwAn[ExitException]("Missing value for concurrency, last argument was an alone '-c'")
      testParse("-c", "")() must throwAn[ExitException]("Empty value provided for concurrency")
      testParse("-c", "--")() must throwAn[ExitException]("Missing value for concurrency, next argument was the stop flag '--'")
      testParse("-c", fakePlan)() must throwAn[ExitException](s"Could not parse fixed concurrency: $fakePlan")
      testParse("-cxxx", fakePlan)() must throwAn[ExitException]("Could not parse fixed concurrency: xxx")

      testParse("-c", "0", fakePlan)() must throwAn[ExitException]("Fixed concurrency cannot be lower than 1, got: 0")
      testParse("-c", "-12", fakePlan)() must throwAn[ExitException]("Fixed concurrency cannot be lower than 1, got: -12")
      testParse("-c", "1234", fakePlan)() must throwAn[ExitException]("Fixed concurrency cannot be higher than 1000, got: 1234")
      testParse("-c23", "--", fakePlan)(
        _.concurrency ==== Concurrency.fixed(23),
        _.export ==== null,
      )
      testParse("-c", "1", "-c", "34", fakePlan)(
        _.concurrency ==== Concurrency.fixed(34),
        _.export ==== null,
      )

      testParse("-c", "cpUx")() must throwAn[ExitException]("Could not parse CPU-relative concurrency: cpUx")
      testParse("-c", "cPu*0.095", fakePlan)() must throwAn[ExitException]("CPU-relative concurrency factor cannot be lower than 0\\.1, got: 0\\.095")
      testParse("-c", "CPU *1000.5", fakePlan)() must throwAn[ExitException]("CPU-relative concurrency factor cannot be higher than 10\\.0, got: 1000\\.5")
      testParse("-c", "cpu", fakePlan)(
        _.concurrency ==== Concurrency.cpuRelative(1),
        _.export ==== null,
      )
      testParse("-ccPU   *   3", fakePlan)(
        _.concurrency ==== Concurrency.cpuRelative(3),
        _.export ==== null,
      )
    }

    "Verification parsing" >> {
      testParse("-v")() must throwAn[ExitException]("Missing value for verification, last argument was an alone '-v'")
      testParse("-v", "")() must throwAn[ExitException]("Empty value provided for verification")
      testParse("-v", "--")() must throwAn[ExitException]("Missing value for verification, next argument was the stop flag '--'")
      testParse("-v", fakePlan)() must throwAn[ExitException](s"Unknown verification: '$fakePlan', supported verifications are: off, warn, require")
      testParse("-vxxx", fakePlan)() must throwAn[ExitException]("Unknown verification: 'xxx', supported verifications are: off, warn, require")
      testParse("-v", "warn", fakePlan)(
        _.verification ==== Verification.WARN,
        _.export ==== null,
      )
      testParse("-vWaRn", "--", fakePlan)(
        _.verification ==== Verification.WARN,
        _.export ==== null,
      )
      testParse("-v", "warn", "-v", "require", fakePlan, fakeExport)(
        _.verification ==== Verification.REQUIRE,
        _.export ==== fakeExportFile,
      )
    }

    "Verification 'require' demands an export argument" >> {
      testParse("-vrequire", fakePlan)() must
        throwA[ExitException]("""\[verification\] is set to 'require', but \[export file\] was not provided""")
    }

    "Complex additional options parsing with overrides" >> {
      testParse("-l", "off", "-a", "SHA-256", "-c", "2", "-aGIT", "-v", "warn", fakePlan)(
        _.algorithm.name ==== "GIT",
        _.concurrency ==== Concurrency.fixed(2),
        _.verification ==== Verification.WARN,
        _.hashPlan ==== fakePlanFile,
        _.export ==== null,
      )
      testParse("-vWaRn", "-a", "MD5", "-cCPU * 2", "-l", "ERROR", "-v", "Require", "-aSHA-384", "--", "-aplan", "-vexport")(
        _.algorithm.name ==== "SHA-384",
        _.concurrency ==== Concurrency.cpuRelative(2),
        _.verification ==== Verification.REQUIRE,
        _.hashPlan ==== new File("-aplan"),
        _.export ==== new File("-vexport"),
      )
    }
  }

  "Too many arguments" >> {
    testParse(fakePlan, fakeExport, "xyzzy")() must
      throwA[ExitException]("""There are too many arguments provided after \[hash plan file\] and \[export file\], first was: 'xyzzy'""")
  }

//  "No algorithms check" >> {
//    import java.security.Security
//    val providers = Security.getProviders()
//    try {
//      for (provider <- providers) {
//        // Can probably cause some flip-flops in CI tests.
//        // Using `sequential` so that this is the last test in the suite.
//        // Perhaps the test is not worth-it and can remain as a commented out expectation
//        Security.removeProvider(provider.getName)
//      }
//      testParse(fakePlan)() must throwAn[ExitException]("Algorithm 'SHA-1' is not supported. Supported algorithms: <none>")
//      testParse("-agIt")() must throwAn[ExitException]("Algorithm 'gIt' is not supported. Supported algorithms: <none>")
//      testParse("-aSHaaAaaA")() must throwAn[ExitException]("Algorithm 'SHaaAaaA' is not supported. Supported algorithms: <none>")
//    } finally {
//      for (provider <- providers) {
//        Security.addProvider(provider)
//      }
//      // test restoration of sanity
//      test("-a", "Ẓ̟̙̪͙͓́ͬͫͮ̿͒̈͊͊̿ͧ̂̎ͬ́͞A̤͇̺̲̪̖̥̠̳̘̻̬͍͙̣͎̝͔͋̅͒̏͗͂̑͆ͬͣ͒̾̚̕͞L̛̎̉͂ͪͫ̌ͣ̐̿͑ͩ̽̐̍͆̆͆ͦ҉̥̬̠̞̗͓̻̩͟Ġͫ͋ͪͧ̂̉͗̀̚̚҉̭͎̰͎͓̗̗̺̲̰͚̻̼̰̜͉͟O̖̙̝̥̳͕̪̥͖͍̺͊ͮͪ̾ͤ̓̏͗̐̊̀̃͊͘̕")() must throwAn[ExitException]("""Algorithm 'Ẓ̟̙̪͙͓́ͬͫͮ̿͒̈͊͊̿ͧ̂̎ͬ́͞A̤͇̺̲̪̖̥̠̳̘̻̬͍͙̣͎̝͔͋̅͒̏͗͂̑͆ͬͣ͒̾̚̕͞L̛̎̉͂ͪͫ̌ͣ̐̿͑ͩ̽̐̍͆̆͆ͦ҉̥̬̠̞̗͓̻̩͟Ġͫ͋ͪͧ̂̉͗̀̚̚҉̭͎̰͎͓̗̗̺̲̰͚̻̼̰̜͉͟O̖̙̝̥̳͕̪̥͖͍̺͊ͮͪ̾ͤ̓̏͗̐̊̀̃͊͘̕' is not supported. Supported algorithms: .*aliases.*""")
//    }
//  }
}
