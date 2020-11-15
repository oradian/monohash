package com.oradian.infra.monohash

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.util.concurrent.atomic.LongAdder

import org.specs2.mutable.Specification

import scala.util.Random

class EnvelopeSpec extends Specification {
  "Envelope 'raw' matches vanilla hashing" >> {
    inWorkspace { ws =>
      val random = Random.nextBytes(1024 * 1024)
      val md = MessageDigest.getInstance("SHA-512");
      val expectedHash = md.digest(random)

      val testPath = Paths.get(ws + "blob.bin")
      Files.write(testPath, random)
      val logger = new LoggingLogger
      val bytesHashed = new LongAdder
      val worker = new HashWorker(logger, md, Envelope.RAW, bytesHashed)
      val actualHash = worker.hashFile(testPath.toFile)
      bytesHashed.longValue ==== 1024 * 1024
      actualHash ==== expectedHash
    }
  }

  "Envelope 'git' matches Git's blob object ID calculation" >> {
    "Check hardcoded" >> {
      inWorkspace { ws =>
        val bytes = "ABC".getBytes(UTF_8)
        val testPath = Paths.get(ws + "blob.bin")
        Files.write(testPath, bytes)
        val logger = new LoggingLogger
        val bytesHashed = new LongAdder
        val md = MessageDigest.getInstance("SHA")
        val worker = new HashWorker(logger, md, Envelope.GIT, bytesHashed)
        val actualHash = worker.hashFile(testPath.toFile)
        bytesHashed.longValue ==== 3
        Hex.toHex(actualHash) ==== "48b83b862ebc57bd3f7c34ed47262f4b402935af"
      }
    }

    "Check using Git" >> {
      inWorkspace { ws =>
        val random = Random.nextBytes(1024 * 1024)
        val testPath = Paths.get(ws + "blob.bin")
        Files.write(testPath, random)

        val md = MessageDigest.getInstance("SHA");
        val logger = new LoggingLogger
        val bytesHashed = new LongAdder
        val worker = new HashWorker(logger, md, Envelope.GIT, bytesHashed)
        val actualHash = worker.hashFile(testPath.toFile)
        bytesHashed.longValue ==== 1024 * 1024

        import sys.process._
        val expectedHash = Seq("git", "hash-object", testPath.toString).!!.trim
        Hex.toHex(actualHash) ==== expectedHash
      }
    }
  }
}
