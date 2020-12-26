package com.oradian.infra.monohash
package impl

import com.oradian.infra.monohash.param.LogLevel

class LoggingLoggerSpec extends Specification {
  sequential

  "Filtering and reporting works" >> {
    for (logLevel <- LogLevel.values.toSeq) yield {
      val logger = new LoggingLogger(logLevel)
      logger.logLevel must beTheSameAs(logLevel)
      spam(logger)

      logger.messages() must have size 5 // always everything, we're logging everything regardless of logLevel
      logger.messages(logLevel) must have size logLevel.ordinal
    }
  }

  "Unsafe logging check works" >> {
    val logger = new LoggingLogger(LogLevel.OFF)
    logger.error("Croak") must throwA[RuntimeException]("Non-safe logging in LoggingLoggerSpec.scala:") // 17
  }
}
