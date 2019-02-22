package com.oradian.infra.monohash

import java.util.concurrent.atomic.AtomicLong

import com.oradian.infra.monohash.Logger.Level
import com.oradian.infra.monohash.LoggingLogger.LogData

import scala.collection.mutable

object LoggingLogger {
  case class LogMsg(logLevel: Level, msg: String)
  case class LogData(checks: mutable.Map[Level, AtomicLong], messages: mutable.ArrayBuffer[LogMsg])
}
import com.oradian.infra.monohash.LoggingLogger._

class LoggingLogger extends Logger {
  private[this] val logs = new ThreadLocal[LogData] {
    override def initialValue(): LogData = LogData(
      checks = mutable.Map(Level.values.toSeq.filter(_ != Level.OFF).map(_ -> new AtomicLong): _*),
      messages = mutable.ArrayBuffer.empty,
    )
  }

  def messages: Seq[LogMsg] =
    logs.get().messages.toSeq

  def clear(): Unit = {
    val logData = logs.get()
    logData.messages.clear()
    logData.checks.values.foreach{_.set(0)}
  }

  private[this] def log(logLevel: Level, msg: String): Unit = {
    val logData = logs.get()
    val count = logData.checks(logLevel).decrementAndGet()
    require(count >= 0, s"Tried to log at $logLevel logLevel without checking log logLevel first")
    logData.messages += LogMsg(logLevel, msg)
//    println(s"[${logLevel.name.toLowerCase(java.util.Locale.ROOT)}] $msg")
  }

  private[this] def check(logLevel: Level): Boolean = {
    logs.get().checks(logLevel).incrementAndGet()
    true
  }

  override def error(msg: String): Unit = log(Level.ERROR, msg)
  override def warn (msg: String): Unit = log(Level.WARN,  msg)
  override def info (msg: String): Unit = log(Level.INFO,  msg)
  override def debug(msg: String): Unit = log(Level.DEBUG, msg)
  override def trace(msg: String): Unit = log(Level.TRACE, msg)

  override def isErrorEnabled: Boolean = check(Level.ERROR)
  override def isWarnEnabled:  Boolean = check(Level.WARN )
  override def isInfoEnabled:  Boolean = check(Level.INFO )
  override def isDebugEnabled: Boolean = check(Level.DEBUG)
  override def isTraceEnabled: Boolean = check(Level.TRACE)
}
