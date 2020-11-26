package com.oradian.infra.monohash

import java.nio.file.{Files, Paths}
import java.security.{MessageDigest, Security}
import java.util.concurrent.atomic.LongAdder

import org.specs2.specification.core.Fragments

import scala.util.Random

class AlgorithmSpec extends Specification {
  private[this] val seed = Random.nextLong()

  s"Security provided algorithms work with HashWorker <seed: ${"%016X" format seed}>" >> {
    inWorkspace { ws =>
      val bytes = new Random(seed).nextBytes(1024 * 1024)
      val testPath = Paths.get(ws + "blob.bin")
      Files.write(testPath, bytes)

      val algorithms = Security.getAlgorithms("MessageDigest").asScala.toSeq.sorted
      Fragments.foreach(algorithms) { algorithm =>
        val logger = new LoggingLogger
        val expectedHash = MessageDigest.getInstance(algorithm).digest(bytes)
        val bytesHashed = new LongAdder
        val worker = new HashWorker(logger, new Algorithm(algorithm), bytesHashed)
        val actualHash = worker.hashFile(testPath.toFile)
        s"Check: $algorithm [${Hex.toHex(expectedHash)}]" >> {
          bytesHashed.longValue ==== 1024 * 1024
          actualHash ==== expectedHash
        }
      }
    }
  }

  "Algorithm 'GIT' matches Git's blob object ID calculation" >> {
    "Check hardcoded" >> {
      inWorkspace { ws =>
        val bytes = "ABC".getBytes(UTF_8)
        val testPath = Paths.get(ws + "blob.bin")
        Files.write(testPath, bytes)
        val logger = new LoggingLogger
        val bytesHashed = new LongAdder
        val algorithm = new Algorithm("git", Security.getProvider("SUN"))
        val worker = new HashWorker(logger, algorithm, bytesHashed)
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

        val logger = new LoggingLogger
        val bytesHashed = new LongAdder
        val algorithm = new Algorithm("git")
        val worker = new HashWorker(logger, algorithm, bytesHashed)
        val actualHash = worker.hashFile(testPath.toFile)
        bytesHashed.longValue ==== 1024 * 1024

        import sys.process._
        val expectedHash = Seq("git", "hash-object", testPath.toString).!!.trim
        Hex.toHex(actualHash) ==== expectedHash
      }
    }
  }

  "Default provider vs provided provider" >> {
    val defaultProvider = new Algorithm("md5").init(() => 0L).getProvider
    val explicitProvider = new Algorithm("md5", defaultProvider).init(() => 0L).getProvider
    defaultProvider must beTheSameAs(explicitProvider)
  }

  "Test 'Should not happen' loss of digest service from provider" >> {
    val algorithm = new Algorithm("SHA-1")

    val underlyingField = algorithm.getClass.getField("underlying")
    underlyingField.setAccessible(true)
    underlyingField.set(algorithm, "Not Available")

    algorithm.init(() => ???) must throwA[RuntimeException](
      s"""Unable to resolve 'Not Available' MessageDigest via provider '${
        algorithm.provider.getName}', even though this was previously successful""")
  }
}
