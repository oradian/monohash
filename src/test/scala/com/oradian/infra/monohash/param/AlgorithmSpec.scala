package com.oradian.infra.monohash
package param

import java.nio.file.{Files, Paths}
import java.security.{MessageDigest, Security}
import java.util.concurrent.atomic.LongAdder

import com.oradian.infra.monohash.util.Hex
import org.specs2.specification.core.Fragments

class AlgorithmSpec extends Specification with BouncyCastleHelpers {
  sequential

  "Constructors" >> {
    val s1 = new Algorithm("SHA")
    s1.equals(s1) ==== true

    val s2 = new Algorithm("SHA")
    s1.equals(s2) ==== true

    val m1 = new Algorithm("MD5")
    s1.equals(null) ==== false
    s1.equals(m1) ==== false

    new Algorithm("MD5", null) must throwAn[IllegalArgumentException]("provider cannot be null")
  }

  "Provider parsing" >> {
    Algorithm.parseString("sHa-256").name ==== "SHA-256"
    Algorithm.parseString("A @ ?") must throwA[ParamParseException]("Could not parse Algorithm: A @ ?")
    Algorithm.parseString("A @ ABC") must throwA[ParamParseException]("Could not load Security provider: ABC")
  }

  private[this] val logger = new LoggingLogger(LogLevel.TRACE)
  private[this] val seed = Random.nextLong()

  def testAlgorithms(force: Option[String], skip: Map[String, String]): Fragments = {
    inWorkspace { ws =>
      val bytes = new Random(seed).nextBytes(1024 * 1024)
      val testPath = Paths.get(ws + "blob.bin")
      Files.write(testPath, bytes)

      val algorithms = Algorithm.getAlgorithms
        .keySet.asScala.toSeq.map { name =>
        Algorithm.parseString(name + force.fold("") {
          "@" + _
        })
      }

      Fragments.foreach(algorithms) { algorithm =>
        val checkName = s"""Check: "${algorithm.name}" @ "${algorithm.provider}""""
        if (skip.isDefinedAt(algorithm.name)) {
          val explanation = skip(algorithm.name)
          s"$checkName: <$explanation> (skipped)" >> success
        } else {
          try {
            val expectedHash = MessageDigest.getInstance(algorithm.underlying).digest(bytes)
            val bytesHashed = new LongAdder
            val worker = new HashWorker(logger, algorithm, bytesHashed)
            val actualHash = worker.hashFile(testPath.toFile)
            s"$checkName: [${Hex.toHex(expectedHash)}]" >> {
              bytesHashed.longValue ==== 1024 * 1024
              actualHash ==== expectedHash
            }
          } catch {
            case e: Exception =>
              checkName >> failure(e.getMessage)
          }
        }
      }
    }
  }

  s"Security provided algorithms work with HashWorker <seed: ${"%016X" format seed}>" >> {
    testAlgorithms(force = None, skip = Map("GIT" -> "synthetic"))
  }

  s"Security provided algorithms + Bouncy Castle work with HashWorker <seed: ${"%016X" format seed}>" >> {
    initialiseBouncyCastle()
    testAlgorithms(force = Some("BC"), skip = Map(
      "GIT" -> "synthetic",
      "HARAKA-256" -> "requires a shorter source: 32 bytes",
      "HARAKA-512" -> "requires a shorter source: 64 bytes",
    ))
  }

  "Algorithm 'GIT' matches Git's blob object ID calculation" >> {
    "Check hardcoded" >> {
      inWorkspace { ws =>
        val bytes = "ABC".getBytes(UTF_8)
        val testPath = Paths.get(ws + "blob.bin")
        Files.write(testPath, bytes)
        val bytesHashed = new LongAdder
        val algorithm = Algorithm.parseString("git @ SUN")
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

        val bytesHashed = new LongAdder
        val algorithm = new Algorithm(Algorithm.GIT)
        val worker = new HashWorker(logger, algorithm, bytesHashed)
        val actualHash = worker.hashFile(testPath.toFile)
        bytesHashed.longValue ==== 1024 * 1024

        import sys.process._
        val expectedHash = Seq("git", "hash-object", testPath.toString).!!.trim
        Hex.toHex(actualHash) ==== expectedHash
      }
    }
  }

  ".toString, .hashCode & .equals" >> {
    val s1 = new Algorithm("SHA")
    val s2 = new Algorithm("SHA", Security.getProvider("BC"))

    s1.## !=== s2.##
    s1.equals(s2) ==== false

    s2.toString ==== "Algorithm(name=SHA, provider=BC)"
  }
}