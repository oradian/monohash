package com.oradian.infra.monohash
package param

import java.lang.reflect.InvocationTargetException
import java.util.Properties

import scala.reflect.ClassTag

/** Destroy Config in various ways. This renders many param.* classes unoperable.
  * We're OK messing with these since we're running in our dedicated fork */
class ConfigSpec extends Specification {
  sequential

  private[this] val defaults = {
    val field = classOf[Config].getDeclaredField("defaults")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Properties]
  }

  private[this] def test3[Outter <: Throwable : ClassTag, Middle <: Throwable : ClassTag, Inner <: Throwable : ClassTag]
      (default: => Any, errors: String*): MatchResult[_] =
    default must throwA[Outter].like {
      case e: Throwable =>
        e.getMessage ==== errors(0)
        e.getCause must beAnInstanceOf[Middle]
        e.getCause.getMessage ==== errors(1)
        e.getCause.getCause must beAnInstanceOf[Inner]
        e.getCause.getCause.getMessage ==== errors(2)
    }


  private[this] def javaVersion: Int = {
    val str = scala.util.Properties.javaSpecVersion
    (if (str.startsWith("1.")) str.drop(2) else str).toInt
  }


  "Fails on missing properties" >> {
    val method = classOf[Config].getDeclaredMethod("loadProperties", classOf[String])
    method.setAccessible(true)

    test3[InvocationTargetException, RuntimeException, NullPointerException](
      method.invoke(null, "missing.properties"),
      null, // invoke produces no message, only nests actual exceptions
      "Cannot load resource properties: missing.properties",
      if (javaVersion == 8) null else "inStream parameter is null",
    )

    test3[InvocationTargetException, RuntimeException, IllegalArgumentException](
      method.invoke(null, "/config/invalid.properties"),
      null, // invoke produces no message, only nests actual exceptions
      "Cannot load resource properties: /config/invalid.properties",
      "Malformed \\uxxxx encoding.",
    )
  }

  "Test illegal parameter defaults" >> {
    /** Screws up the private static Config.defaults for this particular key,
      * so that any further usage of that parameter will explode */
    def testParse(key: String, illegalValue: String, invoke: => Any, innerError: String): MatchResult[_] = {
      defaults.setProperty(key, illegalValue)
      test3[ExceptionInInitializerError, RuntimeException, ParamParseException](
        invoke,
        null, // static init error produces no message
        "com.oradian.infra.monohash.param.ParamParseException: " + innerError,
        innerError,
      )
    }

    testParse("LogLevel.DEFAULT",     "silent",  LogLevel.DEFAULT,     "Could not parse LogLevel: silent")
    testParse("Verification.DEFAULT", "unknown", Verification.DEFAULT, "Could not parse Verification: unknown")
    testParse("Algorithm.DEFAULT",    "N/A",     Algorithm.DEFAULT,    "Could not initialise Algorithm: N/A")
    testParse("Concurrency.DEFAULT",  "0",       Concurrency.DEFAULT,  "Fixed concurrency cannot be lower than 1, got: 0")
  }
}
