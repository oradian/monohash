package com.oradian.infra.monohash
package param

import java.util.Properties

class AlgorithmConfigSpec extends Specification {
  "Illegal Algorithm.DEFAULT" >> {
    val defaults = {
      val field = classOf[Config].getDeclaredField("defaults")
      field.setAccessible(true)
      field.get(null).asInstanceOf[Properties]
    }
    defaults.setProperty("Algorithm.DEFAULT", "N/A")

    Algorithm.DEFAULT must throwA[ExceptionInInitializerError].like {
      case e: Throwable =>
        e.getCause must beAnInstanceOf[RuntimeException]
        e.getCause.getCause must beAnInstanceOf[ParamParseException]
        e.getCause.getCause.getMessage ==== "Could not initialise Algorithm: N/A"
    }
  }
}
