package com.oradian.infra.monohash

import java.io.{ByteArrayOutputStream, File, PrintStream}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

import com.oradian.infra.monohash.impl.PrintStreamLogger

import scala.jdk.StreamConverters._

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
}
