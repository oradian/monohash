package com.oradian.infra.monohash

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest
import java.util.AbstractMap.SimpleEntry
import java.util.Locale

import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

import scala.jdk.CollectionConverters._
import scala.util.Random

class HashResultsSpec extends Specification {
  private[this] val logger: LoggingLogger = new LoggingLogger()
  private[this] val Algorithm = "SHA-1"
  private[this] val LengthInBytes = MessageDigest.getInstance("SHA-1").getDigestLength

  private[this] def genRandomHashResults(): HashResults = new HashResults(
    logger,
    Algorithm,
    new java.util.TreeMap[String, Array[Byte]](Seq.fill(Random.nextInt(100) + 1) {
      val name = new String(Array.fill(Random.nextInt(100) + 1) {
        Random.nextPrintableChar()
      })
      name -> Random.nextBytes(LengthInBytes)
    }.toMap.asJava).entrySet()
  )

  "Save / load roundtrip test" >> {
    val hashResults = genRandomHashResults()
    val roundtripFile = File.createTempFile("hashResults-", "." + Algorithm.toLowerCase(Locale.ROOT))

    hashResults.export(roundtripFile)
    val hashResultsFromFile = new HashResults(logger, Algorithm, roundtripFile)

    hashResults.size ==== hashResultsFromFile.size
    val ldf = hashResults.asScala.toSeq.map(kv => (kv.getKey, kv.getValue)).toMap
    val rdf = hashResultsFromFile.asScala.toSeq.map(kv => (kv.getKey, kv.getValue)).toMap
    val res = ldf forall { case (lf, ld) =>
      rdf(lf) ==== ld
    }

    val bytes = Files.readAllBytes(roundtripFile.toPath)
    val digest = MessageDigest.getInstance(Algorithm)
    digest.digest(bytes) ==== hashResults.totalHash()

    roundtripFile.delete()
    res
  }

  private[this] def test(src: HashResults, dst: HashResults, expected: String): MatchResult[String] =
    HashResults.diff(src, dst).toString ==== expected.replace("\n", Logger.NL)

  private[this] def toHR(files: (String, Char)*): HashResults = {
    val results = files map { case (path, body) =>
      path -> Array.fill(LengthInBytes) { Hex.fromHex((body.toString * 2).getBytes(UTF_8), 2).head }
    }
    new HashResults(logger, Algorithm, (results map { case (k, v) =>
      new SimpleEntry[String, Array[Byte]](k, v): java.util.Map.Entry[String, Array[Byte]]
    }).asJavaCollection)
  }

  "Diff test" >> {
    "Files added" >> test(
      toHR("To stay the same" -> '2'),
      toHR("To stay the same" -> '2', "To be added" -> 'A'),
      """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa: To be added

""")

    "files renamed" >> test(
      toHR("To stay the same" -> '2', "To be renamed" -> 'A', "To also be renamed" -> 'A'),
      toHR("To stay the same" -> '2', "Renamed 1" -> 'A', "Renamed 2" -> 'A', "Added" -> 'A'),
      """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa: Renamed 1 (renamed from: To be renamed)
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa: Renamed 2 (renamed from: To also be renamed)
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa: Added

""")

    "Files changed" >> test(
      toHR("To be changed" -> '1', "To also be changed" -> '2', "To stay the same" -> 'E'),
      toHR("To be changed" -> 'A', "To also be changed" -> 'B', "To stay the same" -> 'E'),
      """Changed files:
! aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa: To be changed (was: 1111111111111111111111111111111111111111)
! bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb: To also be changed (was: 2222222222222222222222222222222222222222)

""")

    "Files deleted" >> test(
      toHR("To stay the same" -> '2', "To be deleted" -> 'F'),
      toHR("To stay the same" -> '2'),
      """Deleted files:
- ffffffffffffffffffffffffffffffffffffffff: To be deleted

""")

    "Mixed changes" >> test(
      toHR(
        "To be deleted" -> '1',
        "To be changed" -> '2',
        "To stay the same" -> '3',
        "To be deleted" -> '4',
        "To be renamed" -> '5'
      ),
      toHR(
        "Added" -> 'A',
        "To be changed" -> 'B',
        "To stay the same" -> '3',
        "Renamed" -> '5'
      ),
      """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa: Added
+ 5555555555555555555555555555555555555555: Renamed (renamed from: To be renamed)

Changed files:
! bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb: To be changed (was: 2222222222222222222222222222222222222222)

Deleted files:
- 4444444444444444444444444444444444444444: To be deleted

""")
  }

  "HashResults are iterable" >> {
    val results = genRandomHashResults()
    val i = results.iterator()
    while (i.hasNext) {
      val entry = i.next()
      entry.getKey must not be empty
      entry.getValue must have size LengthInBytes
    }
    i.next() must throwA[NoSuchElementException]("Seeking past last entry")
  }
}
