package com.oradian.infra.monohash

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicLong

import com.oradian.infra.monohash.LoggingLogger._

import scala.collection.mutable

object LoggingLogger {
  case class LogMsg(logLevel: Logger.Level, msg: String)
  case class LogData(checks: mutable.Map[Logger.Level, AtomicLong], messages: mutable.ArrayBuffer[LogMsg])
}

/** Testing logger which buffers all log lines in memory and ensure we're not calling
 * logging lines without checking the log level beforehand */
class LoggingLogger extends Logger {
  private[this] val TeeToStdOut = false
  private[this] val logs = new ThreadLocal[LogData] {
    override def initialValue(): LogData = LogData(
      checks = mutable.Map(Logger.Level.values.toSeq.filter(_ != Logger.Level.OFF).map(_ -> new AtomicLong): _*),
      messages = mutable.ArrayBuffer.empty,
    )
  }

  def messages(minimumLogLevel: Logger.Level = Logger.Level.TRACE): Seq[LogMsg] =
    logs.get().messages.toSeq.filter(minimumLogLevel.ordinal >= _.logLevel.ordinal)

  def clear(): Unit = {
    val logData = logs.get()
    logData.messages.clear()
    logData.checks.values.foreach{_.set(0)}
  }

  private[this] def log(logLevel: Logger.Level, msg: String): Unit = {
    val logData = logs.get()
    val count = logData.checks(logLevel).decrementAndGet()
    if (count < 0) {
      val stackTrace = Thread.currentThread().getStackTrace()(3) // should always be three stacks away
      val sourcePath = projectRoot + "src/main/java/" + stackTrace.getClassName.replace('.', '/') + ".java"
      val sourceLine = Files.readAllLines(Paths.get(sourcePath), UTF_8).get(stackTrace.getLineNumber - 1).trim
      assert(sourceLine.startsWith("logger."), "Logging line start mismatch")
      if (!sourceLine.endsWith("// logging loop")) {
        sys.error(s"Tried to log at $logLevel log level without checking log level first")
      }
    }
    logData.messages += LogMsg(logLevel, msg)
    if (TeeToStdOut) {
      println(s"[${logLevel.name.toLowerCase(java.util.Locale.ROOT)}] $msg")
    }
  }

  private[this] def check(logLevel: Logger.Level): Boolean = {
    logs.get().checks(logLevel).incrementAndGet()
    true
  }

  override def error(msg: String): Unit = log(Logger.Level.ERROR, msg)
  override def warn(msg: String): Unit = log(Logger.Level.WARN, msg)
  override def info(msg: String): Unit = log(Logger.Level.INFO, msg)
  override def debug(msg: String): Unit = log(Logger.Level.DEBUG, msg)
  override def trace(msg: String): Unit = log(Logger.Level.TRACE, msg)

  override def isErrorEnabled: Boolean = check(Logger.Level.ERROR)
  override def isWarnEnabled: Boolean = check(Logger.Level.WARN)
  override def isInfoEnabled: Boolean = check(Logger.Level.INFO)
  override def isDebugEnabled: Boolean = check(Logger.Level.DEBUG)
  override def isTraceEnabled: Boolean = check(Logger.Level.TRACE)
}
