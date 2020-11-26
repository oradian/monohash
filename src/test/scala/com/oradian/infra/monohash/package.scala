package com.oradian.infra

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.util.UUID

import scala.collection.convert.{AsJavaExtensions, AsScalaExtensions, StreamExtensions}

package object monohash
    extends AsJavaExtensions
    with AsScalaExtensions
    with StreamExtensions {

  val isWin: Boolean = scala.util.Properties.isWin

  val projectRoot: String = {
    val path = getClass.getProtectionDomain.getCodeSource.getLocation
      .getPath.replaceFirst("(.*/)target/.*", "$1")
    if (isWin) path.tail else path // deal with leading '/' on Windows
  }
  val resources: String = projectRoot + "src/test/resources/"

  private[this] val workspace: String = projectRoot + "target/workspace/"
  def inWorkspace[T](f: String => T): T = {
    val newWorkspace = workspace + System.currentTimeMillis() + "-" + UUID.randomUUID() + "/"
    val wsPath = Paths.get(newWorkspace)
    Files.createDirectories(wsPath);
    try {
      f(newWorkspace)
    } finally {
      Files.walk(wsPath).toScala(IndexedSeq)
        .sorted.reverse.map(Files.delete)
    }
  }

  type LoggingLogger = impl.LoggingLogger
  type LogData = impl.LoggingLogger.LogData
  val LogData: impl.LoggingLogger.LogData.type = impl.LoggingLogger.LogData
  type LogMsg = impl.LoggingLogger.LogMsg
  val LogMsg: impl.LoggingLogger.LogMsg.type = impl.LoggingLogger.LogMsg

  val UTF_8: Charset = java.nio.charset.StandardCharsets.UTF_8
  val ISO_8859_1: Charset = java.nio.charset.StandardCharsets.ISO_8859_1

  type Specification = org.specs2.mutable.Specification
  type MatchResult[T] = org.specs2.matcher.MatchResult[T]

  type File = java.io.File

  val Random: scala.util.Random.type = scala.util.Random
}
