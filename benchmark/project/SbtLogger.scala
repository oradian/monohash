import com.oradian.infra.monohash.Logger

import sbt.util.Level

class SbtLogger(underlying: sbt.Logger, level: Level.Value) extends Logger {
  override def isErrorEnabled: Boolean = level <= Level.Error
  override def isWarnEnabled: Boolean = level <= Level.Warn
  override def isInfoEnabled: Boolean = level <= Level.Info
  override def isDebugEnabled: Boolean = level <= Level.Debug
  override def isTraceEnabled: Boolean = false

  override def error(msg: String): Unit = underlying.error(msg)
  override def warn(msg: String): Unit = underlying.warn(msg)
  override def info(msg: String): Unit = underlying.info(msg)
  override def debug(msg: String): Unit = underlying.debug(msg)
  override def trace(msg: String): Unit = underlying.verbose(msg) // alias for debug, not a real Level in SBT
}
