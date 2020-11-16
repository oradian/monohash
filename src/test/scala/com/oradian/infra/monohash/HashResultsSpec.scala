package com.oradian.infra.monohash

import java.io.File
import java.nio.file.Files

import org.specs2.matcher.MatchResult

import scala.jdk.CollectionConverters._
import scala.util.Random

class HashResultsSpec extends Specification {
  private[this] val logger: LoggingLogger = new LoggingLogger()
  private[this] val algorithm = new Algorithm("SHA-1")
  private[this] val lengthInBytes = algorithm.lengthInBytes

  private[this] def genRandomHashResults(): HashResults = HashResults.apply(
    logger,
    algorithm,
    new java.util.TreeMap[String, Array[Byte]](Seq.fill(Random.nextInt(100) + 1) {
      val name = new String(Array.fill(Random.nextInt(100) + 1) {
        Random.nextPrintableChar()
      })
      name -> Random.nextBytes(lengthInBytes)
    }.toMap.asJava).entrySet(),
  )

  "Save / load roundtrip test" >> {
    val hashResults = genRandomHashResults()
    val roundtripFile = File.createTempFile("hashResults-", "." + algorithm.name)
    hashResults.export(roundtripFile)
    val lines = Files.readAllBytes(roundtripFile.toPath)
    val hashResultsFromFile = HashResults.apply(logger, algorithm, lines)

    hashResults.size ==== hashResultsFromFile.size
    val ldf = hashResults.toMap.asScala
    val rdf = hashResultsFromFile.toMap.asScala
    val res = ldf forall { case (lf, ld) =>
      rdf(lf) ==== ld
    }

    val bytes = Files.readAllBytes(roundtripFile.toPath)
    val md = algorithm.init(() => 0L)
    md.digest(bytes) ==== hashResults.hash()

    roundtripFile.delete()
    res
  }

  private[this] def test(actual: HashResults, expected: Seq[(String, Array[Byte])]): MatchResult[_] =
    actual.toMap.asScala.view.mapValues(_.toSeq).toSeq ====
    expected.map(k => (k._1, k._2.toSeq))

  "HashResults.toMap is pre-sorted" >> {
    val results = genRandomHashResults().toMap
    val asSeq = results.asScala.view.mapValues(_.toSeq).toSeq
    val sorted = asSeq.sortBy(_._1)
    asSeq ==== sorted
  }

  "HashResults can be empty" >> {
    val hr = HashResults.apply(logger, algorithm, Array.emptyByteArray)
    hr.size ==== 0
    test(hr, Seq.empty)
  }

  "HashResults corruption handling" >> {
    "Missing last newline" >> {
      val hash = algorithm.init(() => 0L).digest(Random.nextBytes(100))
      val line = s"${Hex.toHex(hash)} missing".getBytes(UTF_8)
      val hr = HashResults.apply(logger, algorithm, line)
      hr.size() ==== 1
      test(hr, Seq("missing" -> hash))
    }

    "Garbage instead of line" >> {
      val line = "*garbage*\n".getBytes(UTF_8)
      val hr = HashResults.apply(logger, algorithm, line)
      hr.size() ==== 1
      hr.toMap must throwA[ExportParsingException]("""Cannot parse export line #0: \*garbage\*""")
    }

    "Missing separator after hash" >> {
      val hash1 = algorithm.init(() => 0L).digest(Random.nextBytes(100))
      val hash2 = algorithm.init(() => 0L).digest(Random.nextBytes(100))
      val lines = s"""${Hex.toHex(hash1)} ok/path
${Hex.toHex(hash2)}XgarbageX
""".getBytes(UTF_8)
      val hr = HashResults.apply(logger, algorithm, lines)
      hr.size() ==== 2
      hr.toMap must throwA[ExportParsingException](s"""Could not split hash from path in export line #1: ${Hex.toHex(hash2)}XgarbageX""")
    }
  }
}
