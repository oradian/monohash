package com.oradian.infra.monohash

import java.nio.file.{Files, Paths}

import com.oradian.infra.monohash.util.Hex

class VerificationSpec extends Specification {
  "When export file is not provided" >> {
    def testNoExportProvided(verification: Verification): MatchResult[_] = {
      val logger = new LoggingLogger
      inWorkspace { ws =>
        Files.write(Paths.get(ws + "three-A.txt"), "AAA".getBytes(UTF_8))
        val algorithm = new Algorithm("MD5")
        try {
          val hashResults = new MonoHash(logger).run(new File(ws), null, algorithm, 2, verification)
          Hex.toHex(hashResults.hash()) ==== "33ce171b266744dfce9c5d0e66635c5d"
        } finally {
          // check logger for no warnings and errors even if MonoHash explodes above
          logger.messages(Logger.Level.WARN) ==== Nil
        }
      }
    }

    "Verification 'off' & 'warn' will work" >> {
      testNoExportProvided(Verification.OFF)
      testNoExportProvided(Verification.WARN)
    }
    "Verification 'require' will explode" >> {
      testNoExportProvided(Verification.REQUIRE) must
        throwAn[ExitException]("""\[verification\] is set to 'require', but \[export file\] was not provided""")
    }
  }

  "When export file is provided, but missing" >> {
    def testExportProvidedButMissing(verification: Verification)
                                    (loggerCheck: LoggingLogger => MatchResult[_]): MatchResult[_] = {
      val logger = new LoggingLogger
      inWorkspace { ws =>
        Files.write(Paths.get(ws + "three-A.txt"), "AAA".getBytes(UTF_8))
        val missingExport = new File(ws + "export.missing")
        val algorithm = new Algorithm("MD5")
        val hashResults = new MonoHash(logger).run(new File(ws), missingExport, algorithm, 2, verification)
        Hex.toHex(hashResults.hash()) ==== "33ce171b266744dfce9c5d0e66635c5d" and
        loggerCheck(logger) and
        Files.readAllBytes(missingExport.toPath) ==== "e1faffb3e614e6c2fba74296962386b7 three-A.txt\n".getBytes(UTF_8)
      }
    }

    "Verification 'off' produces no warnings" >> {
      testExportProvidedButMissing(Verification.OFF)(_.messages(Logger.Level.WARN) ==== Nil)
    }
    "Verification 'warn' produces additional warnings" >> {
      testExportProvidedButMissing(Verification.WARN) { logger =>
        logger.messages(Logger.Level.ERROR) ==== Nil
        logger.messages(Logger.Level.WARN) ==== Seq(
          LogMsg(Logger.Level.WARN, """Added files:
+ e1faffb3e614e6c2fba74296962386b7 three-A.txt

""")
        )
      }
    }
    "Verification 'require' will explode" >> {
      testExportProvidedButMissing(Verification.REQUIRE) { _ => ??? } must
        throwAn[ExitException]("""\[verification\] is set to 'require', but previous \[export file\] was not found: """)
    }
  }

  private[this] def testExport(verification: Verification, previousExport: String, expectedExport: String)
                              (loggerCheck: LoggingLogger => MatchResult[_]): MatchResult[_] = {
    val logger = new LoggingLogger
    inWorkspace { source =>
      Files.write(Paths.get(source + "three-A.txt"), "AAA".getBytes(UTF_8))
      inWorkspace { output =>
        val exportPath = Paths.get(output + "monohash.export")
        Files.write(exportPath, previousExport.getBytes(UTF_8))
        val algorithm = new Algorithm("MD5")
        try {
          val hashResults = new MonoHash(logger).run(new File(source), exportPath.toFile, algorithm, 2, verification)
          Hex.toHex(hashResults.hash()) ==== "33ce171b266744dfce9c5d0e66635c5d"
        } finally {
          new String(Files.readAllBytes(exportPath), UTF_8) ==== expectedExport and loggerCheck(logger)
        }
      }
    }
  }

  "When export file is corrupted" >> {
    val expectedExport = "e1faffb3e614e6c2fba74296962386b7 three-A.txt\n"

    "Verification 'off' doesn't read the export file (no warnings)" >> {
      testExport(Verification.OFF, "*garbage*", expectedExport) { logger =>
        logger.messages().exists(_.msg == "Diffing against previous export ...") ==== false
        logger.messages().exists(_.msg startsWith "Diffed against previous export") ==== false
        logger.messages(Logger.Level.WARN) ==== Nil
      }
    }
    "Verification 'warn' complains about the export file, but overwrites it" >> {
      "When provided garbage" >> {
        testExport(Verification.WARN, "*garbage*", expectedExport) { logger =>
          logger.messages().exists(_.msg == "Diffing against previous export ...") ==== true
          logger.messages().exists(_.msg startsWith "Diffed against previous export") ==== false
          logger.messages(Logger.Level.WARN) ==== Seq(
            LogMsg(Logger.Level.WARN, "Could not diff against the previous [export file]: " +
              "Cannot parse export line #1: *garbage*")
          )
        }
      }
      "When provided wrong hash length" >> {
        val previousExport = "1234e1faffb3e614e6c2fba74296962386b7 three-A.txt\n"
        testExport(Verification.WARN, previousExport, expectedExport) { logger =>
          logger.messages(Logger.Level.WARN) ==== Seq(
            LogMsg(Logger.Level.WARN, "Could not diff against the previous [export file]: " +
              "Could not split hash from path in export line #1: " + previousExport.init)
          )
        }
      }
      "When provided weird stuff instead of hex" >> {
        val previousExport = expectedExport.replace('7', 'q')
        testExport(Verification.WARN, previousExport, expectedExport) { logger =>
          logger.messages(Logger.Level.WARN) ==== Seq(
            LogMsg(Logger.Level.WARN, "Could not diff against the previous [export file]: " +
              "Cannot parse export line #1: " + previousExport.init)
          )
        }
      }
    }
    "Verification 'require' explodes without touching the export file" >> {
      testExport(Verification.REQUIRE, "*garbage*", "*garbage*") { logger =>
        logger.messages().exists(_.msg == "Diffing against previous export ...") ==== true
        logger.messages().exists(_.msg startsWith "Diffed against previous export") ==== false
        logger.messages(Logger.Level.WARN) ==== Seq(
          LogMsg(Logger.Level.ERROR, "Could not diff against the previous [export file]: " +
            "Cannot parse export line #1: *garbage*")
        )
      } must throwAn[ExitException]("""\[verification\] was set to 'require', but there was a difference in export results""")
    }
  }

  "When previous export file exists, but there are changes" >> {
    val previousExport = "e1faffb3e614e6c2fba74296962386b6 three-A.txt\n"
    val expectedExport = "e1faffb3e614e6c2fba74296962386b7 three-A.txt\n"

    "Verification 'off' doesn't diff the export file" >> {
      testExport(Verification.OFF, previousExport, expectedExport) { logger =>
        logger.messages().exists(_.msg startsWith "Read previous [export file]") ==== true
        logger.messages().exists(_.msg == "Diffing against previous export ...") ==== false
        logger.messages().exists(_.msg startsWith "Diffed against previous export") ==== false
        logger.messages().exists(_.msg startsWith "Wrote to [export file]") ==== true
        logger.messages(Logger.Level.WARN) ==== Nil
      }
    }

    "Verification 'warn' reads the export file and overwrites it" >> {
      testExport(Verification.WARN, previousExport, expectedExport) { logger =>
        logger.messages().exists(_.msg startsWith "Read previous [export file]") ==== true
        logger.messages().exists(_.msg == "Diffing against previous export ...") ==== true
        logger.messages().exists(_.msg startsWith "Diffed against previous export") ==== true
        logger.messages().exists(_.msg startsWith "Wrote to [export file]") ==== true
        logger.messages(Logger.Level.WARN) ==== Seq(
          LogMsg(Logger.Level.WARN, """Modified files:
! e1faffb3e614e6c2fba74296962386b7 three-A.txt (previously: e1faffb3e614e6c2fba74296962386b6)

""")
        )
      }
    }

    "Verification 'require' reads the export file, but crashes without overwriting it" >> {
      testExport(Verification.REQUIRE, previousExport, previousExport) { logger =>
        logger.messages().exists(_.msg startsWith "Read previous [export file]") ==== true
        logger.messages().exists(_.msg == "Diffing against previous export ...") ==== true
        logger.messages().exists(_.msg startsWith "Diffed against previous export") ==== true
        logger.messages().exists(_.msg startsWith "Wrote to [export file]") ==== false
        logger.messages(Logger.Level.WARN) ==== Seq(
          LogMsg(Logger.Level.ERROR, """Modified files:
! e1faffb3e614e6c2fba74296962386b7 three-A.txt (previously: e1faffb3e614e6c2fba74296962386b6)

""")
        )
      } must throwAn[ExitException]("""\[verification\] was set to 'require', but there was a difference in export results""")
    }
  }

  "Verifications do not overwrite the file on success" >> {
    inWorkspace { source =>
      Files.write(Paths.get(source + "random.bin"), Random.nextBytes(1024 * 1024))
      inWorkspace { output =>
        locally {
          val logger = new LoggingLogger
          val parser = new CmdLineParser(Array("-voff", source, output + "export"), _ => logger)
          new MonoHash(logger).run(parser)
        }

        val export = new File(output + "export")

        for (verification <- Verification.values.toSeq) yield {
          export.setLastModified(System.currentTimeMillis() - 60 * 60 * 1000)

          // different OS will report with different time resolutions,
          // re-read last modified time to ensure successful match below
          val momentInPast = export.lastModified

          val logger = new LoggingLogger
          val parser = new CmdLineParser(Array("-v", verification.name, source, output + "export"), _ => logger)
          new MonoHash(logger).run(parser)

          val messages = logger.messages()
          messages.exists(_.msg startsWith "Read previous [export file]") ==== true
          messages.exists(_.msg == "Diffing against previous export ...") ==== false
          messages.exists(_.msg startsWith "Diffed against previous export") ==== false
          messages.exists(_.msg startsWith "Wrote to [export file]") ==== false
          messages.exists(_.msg.contains("Previous hash result was identical, no need to update the [export file]")) ==== true

          export.lastModified() ==== momentInPast
        }
      }
    }
  }

  "Empty diff check" >> {
    val logger = new LoggingLogger
    inWorkspace { ws =>
      val missingExport = new File(ws + "export.missing")
      val algorithm = new Algorithm("MD5")
      new MonoHash(logger).run(new File(ws), missingExport, algorithm, 2, Verification.WARN)

      logger.messages().exists(_.msg startsWith "Read previous [export file]") ==== false
      logger.messages().exists(_.msg == "Diffing against previous export ...") ==== true
      logger.messages().exists(_.msg startsWith "Diffed against previous export") ==== true
      logger.messages(Logger.Level.WARN) ==== Seq(
        LogMsg(Logger.Level.WARN, "Previous [export file] were not read and there were no entries in current run to build a diff from"),
      )
      logger.messages().exists(_.msg startsWith "Wrote to [export file]") ==== true
    }
  }

  "Hash different, but diff is empty (different ordering)" >> {
    val logger = new LoggingLogger
    inWorkspace { source =>
      Files.write(Paths.get(source + "three-A.txt"), "AAA".getBytes(UTF_8))
      Files.write(Paths.get(source + "three-B.txt"), "BBB".getBytes(UTF_8))
      inWorkspace { output =>
        val export = new File(output + "wrong-order.txt")
        Files.write(export.toPath, """2bb225f0ba9a58930757a868ed57d9a3 three-B.txt
e1faffb3e614e6c2fba74296962386b7 three-A.txt
""".getBytes(UTF_8))
        val algorithm = new Algorithm("MD5")
        new MonoHash(logger).run(new File(source), export, algorithm, 2, Verification.WARN)
        logger.messages(Logger.Level.WARN) ==== Seq(
          LogMsg(Logger.Level.WARN, "Running diff against previous [export file] produced no differences, but the exports were not identical"),
        )
      }
    }
  }
}
