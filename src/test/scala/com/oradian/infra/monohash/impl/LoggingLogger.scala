package com.oradian.infra.monohash
package impl

import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicLong

import com.oradian.infra.monohash.param.LogLevel

import scala.collection.mutable

object LoggingLogger {
  case class LogMsg(logLevel: LogLevel, msg: String)
  case class LogData(checks: mutable.Map[LogLevel, AtomicLong], messages: mutable.ArrayBuffer[LogMsg])
}

/** Testing logger which buffers all log lines in memory and ensure we're not calling
  * logging lines without checking the log logLevel beforehand.
  *
  * @param logLevel param is only used for Teeing to StdOut and for introspecting on the provided logLevel,
  * the LoggingLogger will actually log every message regardless of this provided LogLevel */
class LoggingLogger(val logLevel: LogLevel) extends Logger {
  private[this] val TeeToStdOut = false
  private[this] val logs = new ThreadLocal[LogData] {
    override def initialValue(): LogData = LogData(
      checks = mutable.Map(LogLevel.values.toSeq.filter(_ != LogLevel.OFF).map(_ -> new AtomicLong): _*),
      messages = mutable.ArrayBuffer.empty,
    )
  }

  def messages(minimumLogLevel: LogLevel = LogLevel.TRACE): Seq[LogMsg] =
    logs.get().messages.toSeq.filter(minimumLogLevel.ordinal >= _.logLevel.ordinal)

  def clear(): Unit = {
    val logData = logs.get()
    logData.messages.clear()
    logData.checks.values.foreach{_.set(0)}
  }

  private[this] def log(logLevel: LogLevel, msg: String): Unit = {
    val logData = logs.get()
    val count = logData.checks(logLevel).decrementAndGet()
    if (count < 0) {
      val stackTrace = Thread.currentThread().getStackTrace()(3) // should always be three stacks away
      if (stackTrace.getFileName.endsWith(".java")) {
        val sourcePath = projectRoot + "src/main/java/" + stackTrace.getClassName.replace('.', '/') + ".java"
        val sourceLine = Files.readAllLines(Paths.get(sourcePath), UTF_8).get(stackTrace.getLineNumber - 1).trim
        assert(sourceLine.startsWith("logger."), "Logging line start mismatch")
        if (!sourceLine.endsWith("// logging loop")) {
          sys.error(s"Tried to log at $logLevel log level without checking log level first")
        }
      } else {
        sys.error("Non-safe logging in " + stackTrace.getFileName + ":" + stackTrace.getLineNumber)
      }
    }
    logData.messages += LogMsg(logLevel, msg)
    if (TeeToStdOut) {
      if (this.logLevel.ordinal() >= logLevel.ordinal()) {
        println(s"[${logLevel.name.toLowerCase(java.util.Locale.ROOT)}] $msg")
      }
    }
  }

  private[this] def check(logLevel: LogLevel): Boolean = {
    logs.get().checks(logLevel).incrementAndGet()
    true
  }

  override def error(msg: String): Unit = log(LogLevel.ERROR, msg)
  override def warn(msg: String): Unit = log(LogLevel.WARN, msg)
  override def info(msg: String): Unit = log(LogLevel.INFO, msg)
  override def debug(msg: String): Unit = log(LogLevel.DEBUG, msg)
  override def trace(msg: String): Unit = log(LogLevel.TRACE, msg)

  override def isErrorEnabled: Boolean = check(LogLevel.ERROR)
  override def isWarnEnabled: Boolean = check(LogLevel.WARN)
  override def isInfoEnabled: Boolean = check(LogLevel.INFO)
  override def isDebugEnabled: Boolean = check(LogLevel.DEBUG)
  override def isTraceEnabled: Boolean = check(LogLevel.TRACE)
}
