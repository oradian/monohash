package com.oradian.infra.monohash
package param

import java.security.{MessageDigest, NoSuchAlgorithmException, Security}

import scala.util.Try

class AlgorithmMergeSpec extends Specification {
  private[this] def toJavaSortedSet[T](c: Iterable[T]): java.util.SortedSet[T] =
    new java.util.TreeSet(c.asJavaCollection)

  "Linkage tests" >> {
    def linkTest(a: String*)(p: Set[String]*)(e: (String, Seq[String])*): MatchResult[_] = {
      Algorithm.linkAlgorithms(
        toJavaSortedSet(a),
        p.map(toJavaSortedSet).asJava,
      ).asScala.view.mapValues(_.asScala.toSeq).toSeq ==== e
    }

    linkTest()(Set.empty)() must throwA[IllegalArgumentException]("Expected pairs of aliases, but got: \\[\\]")

    linkTest("A")(
      Set("A")
    )(
      "A" -> Nil,
    )

    linkTest("A")(
      Set("A", "B")
    )(
      "A" -> Seq("B"),
    )

    linkTest("A")(
      Set("A", "B", "C")
    )() must throwA[IllegalArgumentException]("Expected pairs of aliases, but got: \\[A, B, C\\]")
  }

  "Merge test" >> {
    def mergeTest(s: Set[String]*)(d: (String, Seq[String])*): MatchResult[_] = {
      val (algorithms, aliases) = s.partition(_.size == 1)

      Algorithm.linkAlgorithms(
        algorithms.map(_.head).toSet.asJava,
        aliases.map(toJavaSortedSet).asJava,
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

  "GIT is injected if SHA-1 is available" >> {
    Algorithm.getAlgorithms.get("GIT") !=== null
//    while ({
//      try {
//        val sha = MessageDigest.getInstance("SHA-1")
//        Security.removeProvider(sha.getProvider.getName)
//        true
//      } catch {
//        case _: NoSuchAlgorithmException =>
//          false
//      }
//    }) {}
//    Algorithm.getAlgorithms.get("GIT") ==== null
  }
}
