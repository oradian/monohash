package com.oradian.infra.monohash

import java.nio.file.{Files, Paths}
import java.security.MessageDigest

import com.oradian.infra.monohash.impl.PrintStreamLogger

class MonoHashSpec extends Specification {
  private[this] def systemTest(args: String*): ((Int, String), String) =
    withPS { err =>
      withPS { out =>
        MonoHash.main(args.toArray, out, err)
      }
    }

  "System test directory" >> {
    val (actualHash, actualExport) = inWorkspace { ws =>
      val ((exitCode, out), err) = systemTest("-lwarn", resources, ws + "export")
      exitCode ==== ExitException.SUCCESS
      err must beEmpty

      val exportBytes = Files.readAllBytes(Paths.get(ws + "export"))
      (out, new String(exportBytes, UTF_8))
    }

    val expectedExport = Files.walk(Paths.get(resources))
      .filter(_.toFile.isFile)
      .toScala(IndexedSeq)
      .map { file =>
        val path = Paths.get(resources).relativize(file).toString.replace('\\', '/')
        val hash = {
          val md = MessageDigest.getInstance("SHA-1")
          val body = Files.readAllBytes(file)
          md.digest(body).map("%02x".format(_)).mkString
        }
        path -> hash
      }.sortBy(_._1).map { case (path, hash) => s"$hash $path\n" }.mkString
    actualExport ==== expectedExport

    val expectedHash = MessageDigest.getInstance("SHA-1")
      .digest(expectedExport.getBytes(UTF_8))
    actualHash ==== (Hex.toHex(expectedHash) + PrintStreamLogger.NL)
  }

  "System test plan without export" >> {
    val actualHash = {
      val plan = resources + "basePath/00-default/.monohash"
      val ((exitCode, out), err) = systemTest("-ltrace", "-aOID.2.16.840.1.101.3.4.2.3", plan)
      exitCode ==== ExitException.SUCCESS
      err must not contain "[warn]"
      err must not contain "[error]"
      out
    }

    val md = MessageDigest.getInstance("OID.2.16.840.1.101.3.4.2.3")
    val emptyHash = Hex.toHex(md.digest()) // file was empty
    val expectedExport = s"$emptyHash .monohash\n"
    md.reset()
    val expectedHash = md.digest(expectedExport.getBytes(UTF_8))
    actualHash ==== (Hex.toHex(expectedHash) + PrintStreamLogger.NL)
  }

  "Hash plan sanity checks" >> {
    "Hash plan must either be a file or a directory" >> {
      inWorkspace { ws =>
        val missingPlan = ws + "non-existent"
        val ((exitCode, _), _) = systemTest(missingPlan)
        exitCode ==== ExitException.HASH_PLAN_FILE_NOT_FOUND
      }
    }
    "Hash plan cannot end with a slash/backslash if it's a regular file" >> {
      Seq('/', '\\') map { slash =>
        inWorkspace { ws =>
          val trailingSlash = ws + "regular.monohash" + slash
          new File(trailingSlash.init).createNewFile()
          val ((exitCode, out), err) = systemTest(trailingSlash)
          exitCode ==== ExitException.HASH_PLAN_FILE_ENDS_WITH_SLASH
          out must beEmpty
          err must contain("The [hash plan file] must not end with a slash: " + trailingSlash)
        }
      }
    }
    "Hash plan can end with a slash/backslash if it's an existing directory" >> {
      Seq('/', '\\') map { slash =>
        inWorkspace { ws =>
          val trailingSlash = ws + "folder-to-monohash" + slash
          new File(trailingSlash.init).mkdir()
          val ((exitCode, out), err) = systemTest("-lwarn", trailingSlash)
          exitCode ==== ExitException.SUCCESS
          out ==== ("da39a3ee5e6b4b0d3255bfef95601890afd80709" + PrintStreamLogger.NL)
          err must beEmpty
        }
      }
    }
  }

  "Export path sanity checks" >> {
    "Export path must be a file" >> {
      val ((exitCode, out), err) = inWorkspace { source =>
        inWorkspace { ws =>
          val output = ws.init // strip off trailing slash
          systemTest(source, output)
        }
      }
      exitCode ==== ExitException.EXPORT_FILE_IS_NOT_A_FILE
      out must beEmpty
      err must contain("[export file] is not a file: ")
    }
    "Export path cannot end with a slash/backslash" >> {
      Seq('/', '\\') map { slash =>
        inWorkspace { ws =>
          val trailingSlash = ws + "trailing-slash" + slash
          val ((exitCode, out), err) = systemTest(ws, trailingSlash)
          exitCode ==== ExitException.EXPORT_FILE_ENDS_WITH_SLASH
          out must beEmpty
          err must contain("The [export file] must not end with a slash: " + trailingSlash)
        }
      }
    }
  }

  "I/O error handling" >> {
    "Cannot read hash plan" >> {
      val ((exitCode, out), err) = inWorkspace { ws =>
        val plan = ws + "plan"
        new File(plan).createNewFile()
        forbid(plan) {
          systemTest(plan)
        }
      }
      exitCode ==== ExitException.HASH_PLAN_CANNOT_READ
    }

    "Cannot read export file" >> {
      val ((exitCode, out), err) = inWorkspace { source =>
        inWorkspace { ws =>
          val export = ws + "export.txt"
          new File(export).createNewFile()
          forbid(export) {
            systemTest("-vrequire", source, export)
          }
        }
      }
      exitCode ==== ExitException.EXPORT_FILE_REQUIRED_BUT_CANNOT_READ
    }

    "Cannot write export file" >> {
      val ((exitCode, out), err) = inWorkspace { source =>
        new File(source + "something").createNewFile()
        inWorkspace { ws =>
          val export = ws + "export.txt"
          new File(export).createNewFile()
          forbid(export) {
            systemTest("-vwarn", source, export)
          }
        }
      }
      exitCode ==== ExitException.EXPORT_FILE_CANNOT_WRITE
    }
    "Cannot list whitelist directory" >> {
      val ((exitCode, out), err) = inWorkspace { ws =>
        forbid(ws) {
          systemTest("-vwarn", ws)
        }
      }
      exitCode ==== ExitException.MONOHASH_EXECUTION_ERROR
      err must contain("java.io.IOException: Could not list children for path: ")
    }
  }

  "Non-canonical files handling" >> {
    "Broken hash plan path" >> {
      val logger = new LoggingLogger
      new MonoHash(logger).run(new File("\u0000"), null, null, 0, null) must
        throwAn[ExitException]("Could not resolve canonical path of \\[hash plan file\\]: \u0000")
    }
    "Broken export path" >> {
      val logger = new LoggingLogger
      new MonoHash(logger).run(new File(resources), new File("\u0000"), null, 0, null) must
        throwAn[ExitException]("Could not resolve canonical path of \\[export file\\]: \u0000")
    }
  }

  "MonoHash.main() entry point system test" >> {
    withPS { err =>
      val oldErr = System.err
      System.setErr(err)
      try {

        withPS { out =>
          val oldOut = System.out
          System.setOut(out)
          try {

            val old = System.getSecurityManager
            System.setSecurityManager(new SecurityManager {
              override def checkExit(exitCode: Int): Unit = throw new ExitException("main-check", exitCode)

              override def checkPermission(perm: java.security.Permission): Unit = ()
            })

            try {
              MonoHash.main(Array()) ==== ()
            } catch {
              case e: ExitException =>
                e.exitCode ==== 2000
                e.getMessage ==== "main-check"
            } finally {
              System.setSecurityManager(old)
            }

          } finally {
            System.setOut(oldOut)
          }
        }._2 ==== "" // out

      } finally {
        System.setErr(oldErr)
      }
    }._2 must contain("You did not specify the [hash plan file]") // err
  }
}
