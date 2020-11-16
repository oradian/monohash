package com.oradian.infra.monohash
package diff

import org.specs2.matcher.MatchResult

class DiffSpec extends Specification {
  private[this] val logger: LoggingLogger = new LoggingLogger()
  private[this] val algorithm = new Algorithm("SHA-1")

  private[this] type LM = java.util.LinkedHashMap[String, Array[Byte]]

  private[this] def test(src: LM, dst: LM, expected: String): MatchResult[String] =
    Diff.apply(src, dst).toString ==== expected

  private[this] def toMapX(files: (String, Char)*): LM = {
    val results = files map { case (path, body) =>
      path -> Array.fill(algorithm.lengthInBytes) { Hex.fromHex((body.toString * 2).getBytes(UTF_8), 0, 2).head }
    }
    val elems = new java.util.LinkedHashMap[String, Array[Byte]]
    results foreach { case (k, v) =>
      elems.put(k, v)
    }
    elems.ensuring(_.size == files.size)
  }

  private[this] def toMap(files: (String, Char)*): LM = {
    val entries = toMapX(files: _*).entrySet()
    HashResults.apply(logger, algorithm, entries).toMap
  }

  "Files added" >> test(
    toMap("To stay the same" -> '2'),
    toMap("To stay the same" -> '2', "To be added" -> 'a'),
    """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa To be added

""")

  "Files added" >> test(
    toMap("To stay the same" -> '2'),
    toMap("To stay the same" -> '2', "To be added" -> 'a'),
    """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa To be added

""")

  "Files renamed" >> test(
    toMap("To stay the same" -> '2', "To be renamed" -> 'a', "To also be renamed" -> 'a'),
    toMap("To stay the same" -> '2', "Renamed 1" -> 'a', "Renamed 2" -> 'a', "Added" -> 'a'),
    """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa Added

Renamed files:
~ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa Renamed 1 (previously: To be renamed)
~ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa Renamed 2 (previously: To also be renamed)

""")

  "Files changed" >> test(
    toMap("To be changed" -> '1', "To also be changed" -> '2', "To stay the same" -> 'e'),
    toMap("To be changed" -> 'a', "To also be changed" -> 'b', "To stay the same" -> 'e'),
    """Modified files:
! aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa To be changed (previously: 1111111111111111111111111111111111111111)
! bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb To also be changed (previously: 2222222222222222222222222222222222222222)

""")

  "Files deleted" >> test(
    toMap("To stay the same" -> '2', "To be deleted" -> 'f'),
    toMap("To stay the same" -> '2'),
      """Deleted files:
- ffffffffffffffffffffffffffffffffffffffff To be deleted

""")

  "Mixed changes" >> test(
    toMap(
      "To be deleted" -> '1',
      "To be changed" -> '2',
      "To stay the same" -> '3',
      "To also be deleted" -> '4',
      "To be renamed" -> '5'
    ),
    toMap(
      "Added" -> 'a',
      "To be changed" -> 'b',
      "To stay the same" -> '3',
      "Renamed" -> '5'
    ),
    """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa Added

Renamed files:
~ 5555555555555555555555555555555555555555 Renamed (previously: To be renamed)

Modified files:
! bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb To be changed (previously: 2222222222222222222222222222222222222222)

Deleted files:
- 1111111111111111111111111111111111111111 To be deleted
- 4444444444444444444444444444444444444444 To also be deleted

""")

  "No changes" >> test(
    toMap(
      "To stay the same" -> 'a',
      "Keep status quo" -> 'b',
      "Don't rock the boat" -> 'c',
    ),
    toMap(
      "To stay the same" -> 'a',
      "Keep status quo" -> 'b',
      "Don't rock the boat" -> 'c',
    ),
    "")

  "Ordering changes" >> test(
    toMap(
      "Don't rock the boat" -> 'c',
      "To stay the same" -> 'a',
      "Keep status quo" -> 'b',
    ),
    toMap(
      "To stay the same" -> 'a',
      "Keep status quo" -> 'b',
      "Don't rock the boat" -> 'c',
    ),
    "")
}
