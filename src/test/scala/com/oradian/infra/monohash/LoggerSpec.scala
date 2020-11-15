package com.oradian.infra.monohash

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

import com.oradian.infra.monohash.impl.PrintStreamLogger
import org.specs2.mutable.Specification

class LoggerSpec extends Specification {
  private[this] def spam(logger: Logger): Unit = {
    if (logger.isErrorEnabled) { logger.error("e") }
    if (logger.isWarnEnabled) { logger.warn("warn") }
    if (logger.isInfoEnabled) { logger.info("INFO") }
    if (logger.isDebugEnabled) { logger.debug("DEBUG DEBUG") }
    if (logger.isTraceEnabled) { logger.trace("TRACE TRACE TRACE TRACE TRACE TRACE") }
  }

  "LoggingLogger turtle test" >> {
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
      logger.error("Croak") must throwA[RuntimeException]("Non-safe logging in LoggerSpec.scala:") // 34
    }
  }

  "PrintStreamLogger test" >> {
    "Cross product of level and logging works" >> {
      for (level <- Logger.Level.values.toSeq) yield {
        val baos = new ByteArrayOutputStream()
        val testStream = new PrintStream(baos)
        testStream.println("<init>")

        val logger = new PrintStreamLogger(testStream, level)
        spam(logger)

        val logLines = new String(baos.toByteArray, UTF_8).split(Logger.NL)
        logLines must have size level.ordinal + 1 // 1 is for "<init>", need it for the NL split
      }
    }
    "Levels match the log header" >> {
      val baos = new ByteArrayOutputStream()
      val testStream = new PrintStream(baos)
      val logger = new PrintStreamLogger(testStream, Logger.Level.TRACE)
      spam(logger)
      val logLines = new String(baos.toByteArray, UTF_8).split(Logger.NL)
      val levelsThatOutputStuff = Logger.Level.values.tail
      (logLines zip levelsThatOutputStuff).toSeq map { case (logLine, level) =>
        logLine must startWith("[" + level.name.toLowerCase(Locale.ROOT) + "] ")
      }
    }
  }
}
