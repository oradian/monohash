package com.oradian.infra.monohash

import java.io.{ByteArrayOutputStream, PrintStream, RandomAccessFile}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

import com.oradian.infra.monohash.impl.PrintStreamLogger

class MonoHashSpec extends Specification {
  private[this] def withPS[T](f: PrintStream => T): (T, String) = {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos, true, UTF_8.name)
    val res = f(ps)
    val str = new String(baos.toByteArray, UTF_8)
    (res, str)
  }

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

  private[this] def withLock[T](path: String)(f: => T): T = {
    val raf = new RandomAccessFile(path, "rw")
    try {
      val fc = raf.getChannel
      try {
        val lock = fc.lock()
        try {
          f
        } finally {
          lock.release()
        }
      } finally {
        fc.close()
      }
    } finally {
      raf.close()
    }
  }

  "I/O error handling" >> {
    "Cannot read hash plan" >> {
      val ((exitCode, out), err) = inWorkspace { ws =>
        val plan = ws + "plan"
        withLock(plan) {
          systemTest(plan)
        }
      }
      exitCode ==== ExitException.HASH_PLAN_CANNOT_READ
    }

    "Cannot read export file" >> {
      val ((exitCode, out), err) = inWorkspace { source =>
        inWorkspace { ws =>
          val export = ws + "export.txt"
          withLock(export) {
            systemTest("-vrequire", source, export)
          }
        }
      }
      exitCode ==== ExitException.EXPORT_FILE_REQUIRED_BUT_CANNOT_READ
    }

    "Cannot write export file" >> {
      val ((exitCode, out), err) = inWorkspace { source =>
        // Interestingly, if we try to write an empty file over a file that is currently
        // locked using the RandomAccessFile("rw")'s FileChannel.lock it will succeed!
        // This is why we'll create "something" in source dir so that the export file is not empty,
        // otherwise the process would happily truncate the export file and exit successfully
        new File(source + "something").createNewFile()
        inWorkspace { ws =>
          val export = ws + "export.txt"
          withLock(export) {
            systemTest("-vwarn", source, export)
          }
        }
      }
      exitCode ==== ExitException.EXPORT_FILE_CANNOT_WRITE
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
}
