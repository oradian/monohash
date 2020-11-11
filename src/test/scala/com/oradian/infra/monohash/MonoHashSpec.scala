package com.oradian.infra.monohash

import java.io.{File, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._

class MonoHashSpec extends MutableSpec {
  "System test directory" >> {
    val logger = new LoggingLogger
    val plan = resources
    val args = Array("-ltrace", plan)
    val parser = new CmdLineParser(args, _ => logger)
    val monoHash = new MonoHash(parser.logger)
    val results = monoHash.run(parser.hashPlanFile, parser.exportFile, parser.algorithm, parser.concurrency, parser.verification)

    val actualListing = (results.iterator.asScala map { entry =>
      entry.getKey -> Hex.toHex(entry.getValue)
    }).toIndexedSeq

    val expectedListing = Files.walk(Paths.get(resources))
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
    }.sortBy(_._1)

    actualListing ==== expectedListing
  }

  "System test plan+export" >> {
    inWorkspace { workspace =>
      val logger = new LoggingLogger
      val plan = resources + "basePath/00-default/.monohash"
      val export = workspace + "00-export.txt"
      val args = Array("-ltrace", plan, export)
      val parser = new CmdLineParser(args, _ => logger)
      val monoHash = new MonoHash(parser.logger)
      val results = monoHash.run(parser.hashPlanFile, parser.exportFile, parser.algorithm, parser.concurrency, parser.verification)
      val actualExportBytes = Files.readAllBytes(Paths.get(export))
      val actualHash = results.totalHash()

      val md = MessageDigest.getInstance("SHA-1")
      val emptyHash = Hex.toHex(md.digest()) // file was empty
      val expectedExportBytes = s"$emptyHash .monohash\n".getBytes(UTF_8)
      md.reset()
      val expectedHash = md.digest(expectedExportBytes)

      actualExportBytes ==== expectedExportBytes
      actualHash ==== expectedHash
    }
  }

  "Hash plan must exist" >> {
    inWorkspace { ws =>
      val logger = new LoggingLogger
      val missingPlan = new File(ws + "non-existant")
      new MonoHash(logger).run(missingPlan, null, "x", 1, null) must
        throwAn[IOException]("""\[hash plan file\] must point to an existing file or directory""")
    }
  }

  "Export path must be a file" >> {
    inWorkspace { source =>
      inWorkspace { output =>
        val logger = new LoggingLogger
        val plan = new File(source)
        val exportDirectory = new File(output)
        new MonoHash(logger).run(plan, exportDirectory, "SHA-256", 2, Verification.OFF) must
          throwAn[IOException]("""\[export file\] is not a file: """)
      }
    }
  }
}
