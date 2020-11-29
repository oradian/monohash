package com.oradian.infra

import java.nio.file.{Files, Paths}
import java.util.Collections

package object monohash
    extends scala.collection.convert.AsJavaExtensions
    with scala.collection.convert.AsScalaExtensions
    with scala.collection.convert.StreamExtensions {

  val projectRoot: String =
    new File(getClass.getProtectionDomain.getCodeSource.getLocation.toURI)
      .getCanonicalPath
      .replace('\\', '/')
      .replaceFirst("(.*/)target/.*", "$1")

  val resources: String = projectRoot + "src/test/resources/"

  private[this] val workspace: String = projectRoot + "target/workspace/"
  def inWorkspace[T](f: String => T): T = {
    val newWorkspace = workspace + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID() + "/"
    val wsPath = Paths.get(newWorkspace)
    Files.createDirectories(wsPath);
    try {
      f(newWorkspace)
    } finally {
      Files.walk(wsPath).toScala(IndexedSeq)
        .sorted.reverse.map(Files.delete)
    }
  }

  def withPS[T](f: java.io.PrintStream => T): (T, String) = {
    val baos = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(baos, true, UTF_8.name)
    val res = f(ps)
    val str = new String(baos.toByteArray, UTF_8)
    (res, str)
  }

  def forbid[T](path: String)(f: => T): T = {
    val p = Paths.get(path)
    if (scala.util.Properties.isWin) {
      import java.nio.file.attribute.{AclEntry, AclEntryType, AclFileAttributeView}
      val acl = Files.getFileAttributeView(p, classOf[AclFileAttributeView])
      val old = acl.getAcl
      acl.setAcl(Collections.singletonList(AclEntry.newBuilder
        .setPrincipal(acl.getOwner)
        .setType(AclEntryType.ALLOW) // allow nothing
        .build()
      ))
      try {
        f
      } finally {
        acl.setAcl(old)
      }
    } else {
      val old = Files.getPosixFilePermissions(p)
      Files.setPosixFilePermissions(p, Collections.emptySet())
      try {
        f
      } finally {
        Files.setPosixFilePermissions(p, old)
      }
    }
  }

  type LoggingLogger = impl.LoggingLogger
  type LogData = impl.LoggingLogger.LogData
  val LogData: impl.LoggingLogger.LogData.type = impl.LoggingLogger.LogData
  type LogMsg = impl.LoggingLogger.LogMsg
  val LogMsg: impl.LoggingLogger.LogMsg.type = impl.LoggingLogger.LogMsg

  val UTF_8: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8
  val ISO_8859_1: java.nio.charset.Charset = java.nio.charset.StandardCharsets.ISO_8859_1

  type Specification = org.specs2.mutable.Specification
  type MatchResult[T] = org.specs2.matcher.MatchResult[T]

  type File = java.io.File

  val Random: scala.util.Random.type = scala.util.Random
  type Random = scala.util.Random
}
