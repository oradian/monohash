package com.oradian.infra.monohash

import java.nio.file.{Files, Paths}
import java.security.{MessageDigest, Security}
import java.util.concurrent.atomic.LongAdder

import com.oradian.infra.monohash.util.Hex
import org.specs2.specification.core.Fragments

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
    val defaultProvider = new Algorithm("md5").init(() => ???).getProvider
    val explicitProvider = new Algorithm("md5", defaultProvider).init(() => ???).getProvider
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

  "Merge test - code golf exercise" >> {
    def mergeTest(s: Set[String]*)(d: (String, Seq[String])*): MatchResult[_] = {
      val (algorithms, aliases) = s.partition(_.size == 1)

      Algorithm.linkAlgorithms(
        algorithms.map(_.head).toSet.asJava,
        aliases.map(s => new java.util.TreeSet(s.asJava): java.util.SortedSet[String]).asJava,
      ).asScala.toSeq.map { case (k, set) => k -> set.asScala.toSeq } ==== d
    }

    mergeTest()()

    mergeTest(
      Set("A"),
    )(
      "A" -> Seq(),
    )

    mergeTest(
      Set("A"),
      Set("B"),
    )(
      "A" -> Seq(),
      "B" -> Seq(),
    )

    mergeTest(
      Set("A", "B"),
      Set("B"),
    )(
      "B" -> Seq("A")
    )

    mergeTest(
      Set("3", "4"),
      Set("1", "2"),
    )(
      "1" -> Seq("2"),
      "3" -> Seq("4"),
    )

    mergeTest(
      Set("A", "B"),
      Set("B", "C"),
      Set("D"),
      Set("E", "F"),
      Set("F", "D"),
      Set("G", "H"),
      Set("H", "I"),
      Set("I", "SHA-1"),
      Set("C", "I")
    )(
      "D" -> Seq("E", "F"),
      "SHA-1" -> Seq("A", "B", "C", "G", "H", "I"),
    )
  }
}
