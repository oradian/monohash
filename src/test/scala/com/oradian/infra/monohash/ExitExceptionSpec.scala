package com.oradian.infra.monohash

import java.lang.reflect.Modifier

class ExitExceptionSpec extends Specification {
  sequential

  "No exit code collisions" >> {
    val exitCodes = for {
      field <- classOf[ExitException].getDeclaredFields
      if (field.getModifiers & Modifier.STATIC) != 0
      if field.getType == classOf[Int]
    } yield {
      field.getInt(null)
    }
    exitCodes.distinct ==== exitCodes
  }
}
