package com.oradian.infra.monohash
package diff

import java.util.{Collections, Arrays => JArrays}

import com.oradian.infra.monohash.util.Hex

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

  "Mixed changes" >> {
    val src = toMap(
      "To be deleted" -> '1',
      "To be changed" -> '2',
      "To stay the same" -> '3',
      "To also be deleted" -> '4',
      "To be renamed" -> '5',
    )

    val dst = toMap(
      "Added" -> 'a',
      "To be changed" -> 'b',
      "To stay the same" -> '3',
      "Renamed" -> '5',
      "Copied from 'To be renamed'" -> '5',
    )

    "Stringly test" >> test(src, dst,
      """Added files:
+ aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa Added
+ 5555555555555555555555555555555555555555 Copied from 'To be renamed'

Renamed files:
~ 5555555555555555555555555555555555555555 Renamed (previously: To be renamed)

Modified files:
! bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb To be changed (previously: 2222222222222222222222222222222222222222)

Deleted files:
- 1111111111111111111111111111111111111111 To be deleted
- 4444444444444444444444444444444444444444 To also be deleted

""")

    "Object test" >> {
      val diff = Diff.apply(src, dst)

      def hh(ch: Char): Array[Byte] = Hex.fromHex((ch.toString * 40).getBytes(ISO_8859_1))

      diff.adds ==== JArrays.asList(
        new Add("Added", hh('a')),
        new Add("Copied from 'To be renamed'", hh('5')),
      )
      diff.renames ==== JArrays.asList(
        new Rename("To be renamed", "Renamed", hh('5')),
      )

      diff.changes.get(2) === JArrays.asList(
        new Modify("To be changed", hh('2'), hh('b')),
      )

      val del1 = new Delete("To be deleted", hh('1'))

      // self/null checks
      (del1 == del1) ==== true
      del1 !=== (null: Delete)

      // class check
      (diff.adds.get(0): Change) !=== (del1: Change)

      // hashCode/equals checks
      diff.deletes.get(0) ==== del1
      diff.deletes.get(0).## ==== del1.hashCode

      // .toString check
      diff.changes.get(3).toString ==== Seq(
        del1.toString,
        "- 4444444444444444444444444444444444444444 To also be deleted",
      ).mkString("[", ", ", "]")
    }
  }

  "No changes" >> {
    "Noop via maps" >> {
      test(
        toMap(
          "To stay the same" -> 'a',
          "Keep status quo" -> 'b',
          "Don't rock the boat" -> 'c',
        ),
        toMapX(
          "To stay the same" -> 'a',
          "Keep status quo" -> 'b',
          "Don't rock the boat" -> 'c',
        ),
        "")
    }

    Diff.apply(
      Collections.emptyMap(),
      Collections.emptyMap(),
    ).isEmpty ==== true
  }

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
