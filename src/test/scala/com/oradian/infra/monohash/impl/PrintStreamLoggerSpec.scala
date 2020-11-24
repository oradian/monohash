package com.oradian.infra.monohash
package impl

import java.io.{ByteArrayOutputStream, PrintStream}
import java.util.Locale

class PrintStreamLoggerSpec extends Specification {
  "Cross product of level and logging works" >> {
    for (level <- Logger.Level.values.toSeq) yield {
      val baos = new ByteArrayOutputStream()
      val testStream = new PrintStream(baos)
      testStream.println("<init>")

      val logger = new PrintStreamLogger(testStream, level)
      spam(logger)

      val logLines = new String(baos.toByteArray, UTF_8).split(PrintStreamLogger.NL)
      logLines must have size level.ordinal + 1 // 1 is for "<init>", need it for the NL split
    }
  }

  private[this] def withLogger(f: PrintStreamLogger => Any): String = {
    val baos = new ByteArrayOutputStream()
    val testStream = new PrintStream(baos)
    val logger = new PrintStreamLogger(testStream, Logger.Level.TRACE)
    f(logger)
    new String(baos.toByteArray, UTF_8)
  }

  private[this] implicit class NewlineEqualiser(private val underlying: String) {
    def ==~==(expected: String): MatchResult[String] =
      underlying ==== expected.replace("\n", PrintStreamLogger.NL)
  }

  "Levels match the log header" >> {
    val logLines = withLogger { logger =>
      spam(logger)
    }.split('\n')
    val levelsThatOutputStuff = Logger.Level.values.tail
    (logLines zip levelsThatOutputStuff).toSeq map { case (logLine, level) =>
      logLine must startWith("[" + level.name.toLowerCase(Locale.ROOT) + "] ")
    }
  }

  "Newline handling" >> {
    "Empty message is a newline" >> {
      withLogger(_.trace(
        ""
      )) ==~== s"""[trace]${" "}
"""
    }

    "Multi-line messages have Logger.Level headers" >> {
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
}
