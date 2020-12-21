package com.oradian.infra.monohash
package impl

class NoopLoggerSpec extends Specification {
  "Spamming is noop" >> {
    spam(NoopLogger.INSTANCE)
    success
  }

  "Passing anything is allowed" >> {
    NoopLogger.INSTANCE.error(null)
    NoopLogger.INSTANCE.warn(null)
    NoopLogger.INSTANCE.info(null)
    NoopLogger.INSTANCE.debug(null)
    NoopLogger.INSTANCE.trace(null)
    success
  }

  "Singleton name" >> {
    NoopLogger.INSTANCE.toString ==== classOf[NoopLogger].getSimpleName
  }
}
