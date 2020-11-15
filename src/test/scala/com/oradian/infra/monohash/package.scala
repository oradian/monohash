package com.oradian.infra
import java.nio.file.{Files, Paths}
import java.util.UUID

import scala.jdk.StreamConverters._
import scala.util.Properties.isWin

package object monohash {
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
}
