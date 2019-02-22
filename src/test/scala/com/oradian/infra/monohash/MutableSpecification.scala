package com.oradian.infra.monohash

abstract class MutableSpecification extends org.specs2.mutable.Specification {
  protected val resources: String = sourcecode.File()
    .replace('\\', '/')
    .replaceFirst("(/src/test/)scala/.*", "$1resources/")
}
