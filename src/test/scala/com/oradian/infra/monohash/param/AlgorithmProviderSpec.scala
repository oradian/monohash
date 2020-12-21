package com.oradian.infra.monohash
package param

import java.security.{NoSuchAlgorithmException, Security}
import java.util.{Arrays => JArrays}

import com.oradian.infra.monohash.impl.NoopLogger
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.specs2.matcher.MustMatchers

trait BouncyCastleHelpers extends MustMatchers {
  def initialiseBouncyCastle(): MatchResult[_] = {
    Security.getProvider("BC") ==== null
    val bcProvider = new BouncyCastleProvider
    Security.addProvider(bcProvider)
    Security.getProvider("BC") must beTheSameAs(bcProvider)
  }
}

class AlgorithmProviderSpec extends Specification with BouncyCastleHelpers {
  sequential

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

  "GIT is injected if SHA-1 is available" >> {
    val git = new Algorithm(Algorithm.GIT)
    CmdLineParser.parse(JArrays.asList("-agit", "plan"), _ => NoopLogger.INSTANCE).algorithm ==== git

    // we're OK messing with these since we're running in our dedicated fork
    Security.removeProvider(git.provider.getName)
    try {
      new Algorithm(Algorithm.GIT) must
        throwA[NoSuchAlgorithmException]("SHA-1 MessageDigest not available")

      CmdLineParser.parse(JArrays.asList("-agit", "plan"), _ => NoopLogger.INSTANCE) must
        throwAn[ExitException]("Algorithm 'git' is not supported. Supported algorithms: \\<none\\>")
    } finally {
      // unless we bring this back, SBT will drop connection with the JVM :D
      Security.addProvider(git.provider)
    }
  }

  // ---------------------------- on -----------------------------------------------------------------------------------

  "Explicit Bouncy Castle provider check" >> {
    initialiseBouncyCastle()
    val bcProvider = Security.getProvider("BC")
    val testProvider = Algorithm.parseString("md5@BC").init(() => ???).getProvider
    testProvider must beTheSameAs(bcProvider)
  }

  // -------------------------------------------------------------------------------------------------------------------

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
}
