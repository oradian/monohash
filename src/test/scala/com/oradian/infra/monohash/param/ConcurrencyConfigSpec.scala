package com.oradian.infra.monohash
package param

import java.util.Properties

class ConcurrencyConfigSpec extends Specification {
  "Illegal Concurrency.DEFAULT" >> {
    val defaults = {
      val field = classOf[Config].getDeclaredField("defaults")
      field.setAccessible(true)
      field.get(null).asInstanceOf[Properties]
    }
    defaults.setProperty("Concurrency.DEFAULT", "0")

    Concurrency.DEFAULT must throwA[ExceptionInInitializerError].like {
      case e: Throwable =>
        e.getCause must beAnInstanceOf[RuntimeException]
        e.getCause.getCause must beAnInstanceOf[ParamParseException]
        e.getCause.getCause.getMessage ==== "Fixed concurrency cannot be lower than 1, got: 0"
    }
  }
}
