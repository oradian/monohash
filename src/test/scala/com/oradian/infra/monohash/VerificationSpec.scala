package com.oradian.infra.monohash

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}

import com.oradian.infra.monohash.LoggingLogger.LogMsg
import com.oradian.infra.monohash.MonoHash.ExitException
import org.specs2.matcher.MatchResult

class VerificationSpec extends MutableSpec {
  private[this] def testNoExport(verification: Verification): MatchResult[_] = {
    val logger = new LoggingLogger
    inWorkspace { source =>
      Files.writeString(Paths.get(source + "three-A.txt"), "AAA")
      try {
        val hashResults = new MonoHash(logger).run(new File(source), null, "MD5", 2, verification)
        Hex.toHex(hashResults.totalHash()) ==== "33ce171b266744dfce9c5d0e66635c5d"
      } finally {
        // check logger for no warnings and errors even if MonoHash explodes above
        logger.messages(Logger.Level.WARN) ==== Nil
      }
    }
  }

  "When export file is not provided" >> {
    "Verification 'off' & 'warn' will work" >> {
      testNoExport(Verification.OFF)
      testNoExport(Verification.WARN)
    }
    "Verification 'require' will explode" >> {
      testNoExport(Verification.REQUIRE) must
        throwAn[IOException]("""\[verification\] is set to 'require', but \[export file\] was not provided""")
    }
  }

  private[this] def testExport(verification: Verification, previousExport: String, expectedExport: String)
                              (loggerCheck: LoggingLogger => MatchResult[_]): MatchResult[_] = {
    val logger = new LoggingLogger
    inWorkspace { source =>
      Files.writeString(Paths.get(source + "three-A.txt"), "AAA")
      inWorkspace { output =>
        val exportPath = Paths.get(output + "monohash.export")
        Files.writeString(exportPath, previousExport)
        try {
          val hashResults = new MonoHash(logger).run(new File(source), exportPath.toFile, "MD5", 2, verification)
          Hex.toHex(hashResults.totalHash()) ==== "33ce171b266744dfce9c5d0e66635c5d"
        } finally {
          // check export and logger even if MonoHash explodes above
          Files.readString(exportPath) ==== expectedExport and loggerCheck(logger)
        }
      }
    }
  }

  "When export file is corrupted" >> {
    val expectedExport = "e1faffb3e614e6c2fba74296962386b7 three-A.txt\n"

    "Verification 'off' doesn't read the export file (no warnings)" >> {
      testExport(Verification.OFF, "*garbage*", expectedExport) { logger =>
        logger.messages().exists(_.msg contains "Parsing [export file]: ") ==== false
        logger.messages(Logger.Level.WARN) ==== Nil
      }
    }
    "Verification 'warn' complains about the export file, but overwrites it" >> {
      "When provided garbage" >> {
        testExport(Verification.WARN, "*garbage*", expectedExport) { logger =>
          logger.messages().exists(_.msg contains "Parsing [export file]: ") ==== true
          logger.messages().exists(_.msg startsWith "Parsed [export file]: ") ==== false
          logger.messages(Logger.Level.WARN) ==== Seq(
            LogMsg(Logger.Level.WARN, "Could not parse the previous [export file]: Could not split hash from path in line: *garbage*")
          )
        }
      }
      "When provided wrong hash length" >> {
        val previousExport = "1234e1faffb3e614e6c2fba74296962386b7 three-A.txt\n"
        testExport(Verification.WARN, previousExport, expectedExport) { logger =>
          logger.messages(Logger.Level.WARN) ==== Seq(
            LogMsg(Logger.Level.WARN, "Could not parse the previous [export file]: " +
              "Expected hash length of 32, but found a hash with 36 characters instead: '1234e1faffb3e614e6c2fba74296962386b7 three-A.txt'")
          )
        }
      }
      "When provided weird stuff instead of hex" >> {
        val previousExport = "xxfaffb3e614e6c2fba74296962386b7 three-A.txt\n"
        testExport(Verification.WARN, previousExport, expectedExport) { logger =>
          logger.messages(Logger.Level.WARN) ==== Seq(
            LogMsg(Logger.Level.WARN, "Could not parse the previous [export file]: " +
              "Expected hash of 32 hexadecimal characters at the beginning of line, but got: 'xxfaffb3e614e6c2fba74296962386b7 three-A.txt'")
          )
        }
      }
    }
    "Verification 'require' explodes without touching the export file" >> {
      testExport(Verification.REQUIRE, "*garbage*", "*garbage*") { logger =>
        logger.messages().exists(_.msg contains "Parsing [export file]: ") ==== true
        logger.messages().exists(_.msg startsWith "Parsed [export file]: ") ==== false
        // don't log warnings or errors, just explode
        logger.messages(Logger.Level.WARN) ==== Nil
      } must throwAn[IOException]("""Could not split hash from path in line: \*garbage\*""")
    }
  }

  "When previous export file exists, but there are changes" >> {
    val previousExport = "e1faffb3e614e6c2fba74296962386b6 three-A.txt\n"
    val expectedExport = "e1faffb3e614e6c2fba74296962386b7 three-A.txt\n"

    "Verification 'off' doesn't read the export file" >> {
      testExport(Verification.OFF, previousExport, expectedExport) { logger =>
        logger.messages().exists(_.msg startsWith "Parsing [export file]: ") ==== false
        logger.messages().exists(_.msg startsWith "Parsed [export file]: ") ==== false
        logger.messages(Logger.Level.WARN) ==== Nil
      }
    }
    "Verification 'warn' reads the export file and overwrites it" >> {
      testExport(Verification.WARN, previousExport, expectedExport) { logger =>
        logger.messages().exists(_.msg startsWith "Parsing [export file]: ") ==== true
        logger.messages().exists(_.msg startsWith "Parsed [export file]: ") ==== true
        logger.messages(Logger.Level.WARN) ==== Seq(
          LogMsg(Logger.Level.WARN, "Changed files:"),
          LogMsg(Logger.Level.WARN, "! e1faffb3e614e6c2fba74296962386b7: three-A.txt (was: e1faffb3e614e6c2fba74296962386b6)"),
          LogMsg(Logger.Level.WARN, ""),
        )
      }
    }
    "Verification 'require' reads the export file, but crashes without overwriting it" >> {
      testExport(Verification.REQUIRE, previousExport, previousExport) { logger =>
        logger.messages().exists(_.msg startsWith "Parsing [export file]: ") ==== true
        logger.messages().exists(_.msg startsWith "Parsed [export file]: ") ==== true
        logger.messages(Logger.Level.WARN) ==== Seq(
          LogMsg(Logger.Level.ERROR, "Changed files:"),
          LogMsg(Logger.Level.ERROR, "! e1faffb3e614e6c2fba74296962386b7: three-A.txt (was: e1faffb3e614e6c2fba74296962386b6)"),
          LogMsg(Logger.Level.ERROR, ""),
        )
      } must throwAn[ExitException]("""\[verification\] was set to 'require' and there was a difference in export results, aborting!""")
    }
  }
}
