package com.oradian.infra.monohash
package param

import java.util.Properties

class ConfigSpec extends Specification {
  private[this] val defaults = {
    val field = classOf[Config].getDeclaredField("defaults")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Properties]
  }

  private[this] def test(default: => Any, expectedError: String): MatchResult[_] =
    default must throwA[ExceptionInInitializerError].like {
      case e: Throwable =>
        e.getCause must beAnInstanceOf[RuntimeException]
        e.getCause.getCause must beAnInstanceOf[ParamParseException]
        e.getCause.getCause.getMessage ==== expectedError
    }

  "Illegal LogLevel.DEFAULT" >> {
    defaults.setProperty("LogLevel.DEFAULT", "SILENT")
    test(LogLevel.DEFAULT, "Could not parse LogLevel: SILENT")
  }

  "Illegal Algorithm.DEFAULT" >> {
    defaults.setProperty("Algorithm.DEFAULT", "N/A")
    test(Algorithm.DEFAULT, "Could not initialise Algorithm: N/A")
  }

  "Illegal Concurrency.DEFAULT" >> {
    defaults.setProperty("Concurrency.DEFAULT", "0")
    test(Concurrency.DEFAULT, "Fixed concurrency cannot be lower than 1, got: 0")
  }

  "Illegal Verification.DEFAULT" >> {
    defaults.setProperty("Verification.DEFAULT", "unknown")
    test(Verification.DEFAULT, "Could not parse Verification: unknown")
  }
}
