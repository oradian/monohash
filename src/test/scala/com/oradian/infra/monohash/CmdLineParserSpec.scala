package com.oradian.infra.monohash

import java.io.File
import java.util.UUID

import com.oradian.infra.monohash.MonoHash.ExitException
import com.oradian.infra.monohash.impl.PrintStreamLogger
import org.specs2.execute.Result
import org.specs2.matcher.MatchResult

class CmdLineParserSpec extends MutableSpec {
  private[this] def withParser(args: String*)(f: CmdLineParser => MatchResult[_]*): Result = {
    val parser = new CmdLineParser(
      args.toArray,
      (logLevel: Logger.Level) => new PrintStreamLogger(System.err, logLevel),
    )
    Result.foreach(f) { expectation =>
      expectation(parser)
    }
  }

  "Empty argument handling" >> {
    "No args provided" >> {
      withParser()() must throwAn[ExitException]("You did not specify the \\[hash plan file\\]!")
    }
    "Hash plan file is empty" >> {
      withParser("")() must throwAn[ExitException]("Provided \\[hash plan file\\] was an empty string!")
    }
    "Export file is empty" >> {
      withParser("x", "")() must throwAn[ExitException]("Provided \\[export file\\] was an empty string!")
    }
  }

  private[this] val fakePlan = UUID.randomUUID().toString
  private[this] val fakeExport = UUID.randomUUID().toString

  "Simple hash plan" >> {
    withParser(fakePlan)(
      _.hashPlanFile ==== new File(fakePlan),
      _.exportFile ==== null,
    )

    withParser(fakePlan, fakeExport)(
      _.hashPlanFile ==== new File(fakePlan),
      _.exportFile ==== new File(fakeExport),
    )
  }

  "Default options" >> {
    withParser(fakePlan)(
      _.logLevel ==== Logger.Level.INFO,
      _.algorithm ==== "SHA-1",
      _.concurrency must be > 0,
      _.verification ==== Verification.OFF,
      _.hashPlanFile ==== new File(fakePlan),
      _.exportFile ==== null,
    )
  }

  "Option parsing" >> {
    "Log level parsing" >> {
      withParser("-l")() must throwAn[ExitException]("Missing value for log level, last argument was an alone '-l'")
      withParser("-l", "--")() must throwAn[ExitException]("Missing value for log level, next argument was the stop flag '--'")
      withParser("-l", fakePlan)() must throwAn[ExitException](s"Unknown log level: '$fakePlan', supported log levels are: off, error, warn, info, debug, trace")
      withParser("-l", "xxx", fakePlan)() must throwAn[ExitException]("Unknown log level: 'xxx', supported log levels are: off, error, warn, info, debug, trace")
      withParser("-l", "off", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.exportFile ==== null,
      )
      withParser("-l", "OfF", "--", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.exportFile ==== null,
      )
      withParser("-l", "off", "-l", "error", fakePlan)(
        _.logLevel ==== Logger.Level.ERROR,
        _.exportFile ==== null,
      )
    }

    "Algorithm parsing" >> {
      withParser("-a")() must throwAn[ExitException]("Missing value for algorithm, last argument was an alone '-a'")
      withParser("-a", "--")() must throwAn[ExitException]("Missing value for algorithm, next argument was the stop flag '--'")
      withParser("-a", fakePlan)() must throwAn[ExitException](s"Algorithm '$fakePlan' is not supported. Supported algorithms:")
      withParser("-axxx", fakePlan)() must throwAn[ExitException]("Algorithm 'xxx' is not supported. Supported algorithms:")
      withParser("-a", "SHA-256", fakePlan)(
        _.algorithm ==== "SHA-256",
        _.exportFile ==== null,
      )
      withParser("-aSHA-256", "--", fakePlan)(
        _.algorithm ==== "SHA-256",
        _.exportFile ==== null,
      )
      withParser("-a", "SHA-256", "-a", "SHA-512", fakePlan)(
        _.algorithm ==== "SHA-512",
        _.exportFile ==== null,
      )
    }

    "Concurrency parsing" >> {
      withParser("-c")() must throwAn[ExitException]("Missing value for concurrency, last argument was an alone '-c'")
      withParser("-c", "--")() must throwAn[ExitException]("Missing value for concurrency, next argument was the stop flag '--'")
      withParser("-c", fakePlan)() must throwAn[ExitException](s"Invalid concurrency setting: '$fakePlan', expecting a positive integer")
      withParser("-cxxx", fakePlan)() must throwAn[ExitException]("Invalid concurrency setting: 'xxx', expecting a positive integer")
      withParser("-c", "0", fakePlan)() must throwAn[ExitException]("Concurrency must be a positive integer, got: '0'")
      withParser("-c", "-12", fakePlan)() must throwAn[ExitException]("Concurrency must be a positive integer, got: '-12'")
      withParser("-c", "123", fakePlan)(
        _.concurrency ==== 123,
        _.exportFile ==== null,
      )
      withParser("-c1234", "--", fakePlan)(
        _.concurrency ==== 1234,
        _.exportFile ==== null,
      )
      withParser("-c", "1", "-c", "12345", fakePlan)(
        _.concurrency ==== 12345,
        _.exportFile ==== null,
      )
    }

    "Verification parsing" >> {
      withParser("-v")() must throwAn[ExitException]("Missing value for verification, last argument was an alone '-v'")
      withParser("-v", "--")() must throwAn[ExitException]("Missing value for verification, next argument was the stop flag '--'")
      withParser("-v", fakePlan)() must throwAn[ExitException](s"Unknown verification: '$fakePlan', supported verifications are: off, warn, require")
      withParser("-vxxx", fakePlan)() must throwAn[ExitException]("Unknown verification: 'xxx', supported verifications are: off, warn, require")
      withParser("-v", "warn", fakePlan)(
        _.verification ==== Verification.WARN,
        _.exportFile ==== null,
      )
      withParser("-vWaRn", "--", fakePlan)(
        _.verification ==== Verification.WARN,
        _.exportFile ==== null,
      )
      withParser("-v", "warn", "-v", "require", fakePlan, fakeExport)(
        _.verification ==== Verification.REQUIRE,
        _.exportFile ==== new File(fakeExport),
      )
    }

    "Verification 'require' demands an export argument" >> {
      withParser("-vrequire", fakePlan)() must
        throwA[ExitException]("""\[verification\] is set to 'require', but \[export file\] was not provided""")
    }

    "Complex additional options parsing with overrides" >> {
      withParser("-l", "off", "-a", "SHA-256", "-c", "2", "-v", "warn", fakePlan)(
        _.logLevel ==== Logger.Level.OFF,
        _.algorithm ==== "SHA-256",
        _.concurrency ==== 2,
        _.verification ==== Verification.WARN,
        _.hashPlanFile ==== new File(fakePlan),
        _.exportFile ==== null,
      )
      withParser("-vWaRn", "-a", "MD5", "-c123", "-l", "ERROR", "-v", "Require", "-aSHA-512/256", "--", "-aplan", "-vexport")(
        _.logLevel ==== Logger.Level.ERROR,
        _.algorithm ==== "SHA-512/256",
        _.concurrency ==== 123,
        _.verification ==== Verification.REQUIRE,
        _.hashPlanFile ==== new File("-aplan"),
        _.exportFile ==== new File("-vexport"),
      )
    }
  }
}
