package com.oradian.infra.monohash

import java.nio.file.Files
import java.security.MessageDigest

import com.oradian.infra.monohash.param.{Algorithm, LogLevel}
import com.oradian.infra.monohash.util.Hex

class HashResultsSpec extends Specification {
  sequential

  private[this] val logger = new LoggingLogger(LogLevel.TRACE)
  private[this] val algorithm = Algorithm.DEFAULT
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
    inWorkspace { ws =>
      val hashResults = genRandomHashResults()
      val roundtripFile = new File(ws + "export.bin")
      hashResults.export(roundtripFile)
      val lines = Files.readAllBytes(roundtripFile.toPath)
      val hashResultsFromFile = HashResults.apply(logger, algorithm, lines)

      hashResults.size ==== hashResultsFromFile.size
      val ldf = hashResults.toMap.asScala
      val rdf = hashResultsFromFile.toMap.asScala
      ldf forall { case (lf, ld) =>
        rdf(lf) ==== ld
      }

      val bytes = Files.readAllBytes(roundtripFile.toPath)
      val md = algorithm.init(() => ???)
      md.digest(bytes) ==== hashResults.hash()
    }
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
    hr.## ==== 0

    // equality checks
    (hr == hr) ==== true
    hr.equals(null) ==== false

    // hash caching test
    val emptySha = MessageDigest.getInstance("SHA").digest()
    hr.hash() ==== emptySha
    hr.hash() ==== emptySha

    test(hr, Seq.empty)
  }

  "HashResults corruption handling" >> {
    "Missing last newline" >> {
      // to test off-by-one errors in Arrays.copyOf resizing
      for (i <- 1 to 500 by 150) yield {
        val hash = algorithm.init(() => ???).digest(Random.nextBytes(i))
        val line = s"${Hex.toHex(hash)} ${"x" * i}".getBytes(UTF_8)
        val hr = HashResults.apply(logger, algorithm, line)
        hr.size() ==== 1
        test(hr, Seq("x" * i -> hash))
      }
    }

    "Garbage instead of line" >> {
      val line = "*garbage*\n".getBytes(UTF_8)
      val hr = HashResults.apply(logger, algorithm, line)
      hr.size() ==== 1
      hr.toMap must throwA[ExportParsingException]("""Cannot parse export line #1: \*garbage\*""")
    }

    "Missing separator after hash" >> {
      val hash1 = algorithm.init(() => ???).digest(Random.nextBytes(100))
      val hash2 = algorithm.init(() => ???).digest(Random.nextBytes(100))
      val lines = s"""${Hex.toHex(hash1)} ok/path
${Hex.toHex(hash2)}XgarbageX
""".getBytes(UTF_8)
      val hr = HashResults.apply(logger, algorithm, lines)
      hr.size() ==== 2
      hr.toMap must throwA[ExportParsingException](s"""Could not split hash from path in export line #2: ${Hex.toHex(hash2)}XgarbageX""")
    }

    "Empty path string" >> {
      val lines = "da39a3ee5e6b4b0d3255bfef95601890afd80709 \n".getBytes(UTF_8)
      val hr = HashResults.apply(logger, algorithm, lines)
      hr.size() ==== 1
      hr.toMap must throwA[ExportParsingException]("Path was empty on line #1: da39a3ee5e6b4b0d3255bfef95601890afd80709 ")
    }

    "Multiple identical paths" >> {
      val hash = algorithm.init(() => ???).digest(Random.nextBytes(100))
      val lines = (s"""${Hex.toHex(hash)} /collision/path
""" * 2).getBytes(UTF_8)

      val hr = HashResults.apply(logger, algorithm, lines)
      hr.size() ==== 2
      hr.toMap must throwA[ExportParsingException]("At least two export lines found with identical paths '/collision/path'")
    }

    "Malformed UTF-8 in path" >> {
      val hash = algorithm.init(() => ???).digest()
      val lines = (
        Hex.toHex(hash).getBytes(UTF_8)
        :+ (' ': Byte)
        :++ "£3.50".getBytes(ISO_8859_1) // £ is \u00A3 and encodes as 0xA3 in ISO-8859-1 (1:1)
        :+ ('\n': Byte)                  // this will break the UTF-8 decoder which requires each high-bit
      )                                  // character to be preceded by another (i.e. 0xC2 0xA3)

      val hr = HashResults.apply(logger, algorithm, lines)
      hr.size() ==== 1

      val lossyDecode = new String("da39a3ee5e6b4b0d3255bfef95601890afd80709 £3.50".getBytes(ISO_8859_1), UTF_8)
      lossyDecode ==== "da39a3ee5e6b4b0d3255bfef95601890afd80709 �3.50"
      hr.toMap must throwA[ExportParsingException]("Could not decode export line #1 using UTF-8: " + lossyDecode)
    }
  }
}
