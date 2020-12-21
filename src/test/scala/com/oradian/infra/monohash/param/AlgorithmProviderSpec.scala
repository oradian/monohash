package com.oradian.infra.monohash
package param

import java.security.Security

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

  "Explicit Bouncy Castle provider check" >> {
    initialiseBouncyCastle()
    val bcProvider = Security.getProvider("BC")
    val testProvider = Algorithm.parseString("md5@BC").init(() => ???).getProvider
    testProvider must beTheSameAs(bcProvider)
  }
}
