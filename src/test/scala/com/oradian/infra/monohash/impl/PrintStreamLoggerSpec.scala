package com.oradian.infra.monohash
package impl

import java.util.Locale

import com.oradian.infra.monohash.param.LogLevel

class PrintStreamLoggerSpec extends Specification {
  sequential

  "Cross product of logLevel and logging works" >> {
    for (logLevel <- LogLevel.values.toSeq) yield {
      val logLines = withPS { testStream =>
        testStream.println("<init>")
        val logger = new PrintStreamLogger(testStream, logLevel)
        spam(logger)
      }._2.split(PrintStreamLogger.NL)
      logLines must have size logLevel.ordinal + 1 // 1 is for "<init>", need it for the NL split
    }
  }

  private[this] def withLogger(f: PrintStreamLogger => Any): String =
    withPS { testStream =>
      val logger = new PrintStreamLogger(testStream, LogLevel.TRACE)
      f(logger)
    }._2

  private[this] implicit class NewlineEqualiser(private val underlying: String) {
    def ==~==(expected: String): MatchResult[String] =
      underlying ==== expected.replace("\n", PrintStreamLogger.NL)
  }

  "Levels match the log header" >> {
    val logLines = withLogger { logger =>
      spam(logger)
    }.split('\n')
    val logLevelsThatOutputStuff = LogLevel.values.tail
    (logLines zip logLevelsThatOutputStuff).toSeq map { case (logLine, logLevel) =>
      logLine must startWith("[" + logLevel.name.toLowerCase(Locale.ROOT) + "] ")
    }
  }

  "Newline handling" >> {
    "Empty message is a newline" >> {
      withLogger(_.trace(
        ""
      )) ==~== s"""[trace]${" "}
"""
    }

    "Multi-line messages have LogLevel headers" >> {
      withLogger(_.error(
        """This is a
multi-line message with
no newline at the end!"""
      )) ==~==
        """[error] This is a
[error] multi-line message with
[error] no newline at the end!
"""
    }

    "Multi-line messages retain trailing newlines" >> {
      withLogger(_.warn(
      """This is a
multi-line message with
two newlines at the end!

"""
      )) ==~== s"""[warn] This is a
[warn] multi-line message with
[warn] two newlines at the end!
[warn]${" "}
[warn]${" "}
"""
    }
  }

  ".toString exposes log level" >> {
    new PrintStreamLogger(null, LogLevel.WARN).toString ==== "PrintStreamLogger(logLevel=warn)"
  }
}
