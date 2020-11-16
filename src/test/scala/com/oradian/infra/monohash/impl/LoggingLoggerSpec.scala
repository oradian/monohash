package com.oradian.infra.monohash
package impl

class LoggingLoggerSpec extends Specification {
  "Filtering and reporting works" >> {
    val logger = new LoggingLogger
    spam(logger)

    logger.messages() must have size 5
    for (level <- Logger.Level.values.toSeq) yield {
      logger.messages(level) must have size level.ordinal
    }
  }

  "Unsafe logging check works" >> {
    val logger = new LoggingLogger
    logger.error("Croak") must throwA[RuntimeException]("Non-safe logging in LoggingLoggerSpec.scala:") // 17
  }
}
