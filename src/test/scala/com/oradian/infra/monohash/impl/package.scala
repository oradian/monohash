package com.oradian.infra.monohash

package object impl {
  def spam(logger: Logger): Unit = {
    if (logger.isErrorEnabled) { logger.error("e") }
    if (logger.isWarnEnabled) { logger.warn("warn") }
    if (logger.isInfoEnabled) { logger.info("INFO") }
    if (logger.isDebugEnabled) { logger.debug("DEBUG DEBUG") }
    if (logger.isTraceEnabled) { logger.trace("TRACE TRACE TRACE TRACE TRACE TRACE") }
  }
}
