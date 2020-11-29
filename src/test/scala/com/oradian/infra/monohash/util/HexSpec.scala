package com.oradian.infra.monohash
package util

import java.util.{Arrays => JArrays}

class HexSpec extends Specification {
  private[this] final val ToLcHex = "0123456789abcdef".toCharArray
  private[this] def turtleHex(bytes: Array[Byte]): String = {
    val hex = new Array[Char](bytes.length << 1)
    var i = 0
    for (b <- bytes) {
      hex(i) = ToLcHex((b >> 4) & 0xf)
      hex(i + 1) = ToLcHex(b & 0xf)
      i += 2
    }
    new String(hex)
  }

  private[this] def roundTripTest(bytes: Array[Byte]): MatchResult[_] = {
    val actualTo = Hex.toHex(bytes)
    val expectedTo = turtleHex(bytes)

    val roundtrip = Hex.fromHex(actualTo.getBytes(ISO_8859_1))
    actualTo ==== expectedTo and
    JArrays.equals(bytes, roundtrip) ==== true
  }

  "Test hardcoded" >> {
    val bytes = (1 to 255).map(_.toByte).toArray
    roundTripTest(bytes)
  }

  "Test random" >> {
    val bytes = Random.nextBytes(1024 * 1024)
    roundTripTest(bytes)
  }

  "Test odd length" >> {
    val letters = ('a' to 'c').map(_.toByte).toArray
    Hex.fromHex(letters) must throwA[IllegalArgumentException]("Length must be an even number, got: 3")
  }

  "Test invalid lowercase chars" >> {
    val letters = ('a' to 'z').map(_.toByte).toArray
    Hex.fromHex(letters) must throwA[NumberFormatException]("""Cannot parse hex digit at index 6 - expected a lowercase hexadecimal digit \[0-9, a-f\] but got: 'g'""")
  }

  "Uppercase characters are not supported" >> {
    val letters = "aA".getBytes(ISO_8859_1)
    Hex.fromHex(letters) must throwA[NumberFormatException]("""Cannot parse hex digit at index 1 - expected a lowercase hexadecimal digit \[0-9, a-f\] but got: 'A'""")
  }
}
