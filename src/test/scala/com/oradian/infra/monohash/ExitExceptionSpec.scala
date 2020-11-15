package com.oradian.infra.monohash

import java.lang.reflect.Modifier

import org.specs2.mutable.Specification

class ExitExceptionSpec extends Specification {
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
