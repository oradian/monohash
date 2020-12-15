import java.io._
import java.nio.file.Files
import java.util.{Arrays => JArrays}

import nu.studer.java.util.OrderedProperties

object PropertiesVersion {
  /** Update the .properties file in the resources with latest version
   * and also do a roundtrip through java.util.Properties compatible
   * implementation to avoid encoding / escaping gotchas - for there are plenty */
  def update(logger: sbt.Logger, propertiesFile: File, version: String): Unit = {
    val currentPropsBytes = Files.readAllBytes(propertiesFile.toPath)
    val props = {
      val tmp = new OrderedProperties.OrderedPropertiesBuilder()
        .withSuppressDateInComment(true)
        .build()
      tmp.load(new ByteArrayInputStream(currentPropsBytes))
      tmp
    }

    props.setProperty("Version", version)
    val updatedPropsBytes = {
      val baos = new ByteArrayOutputStream
      props.store(baos, null)
      baos.toByteArray
    }

    val needsUpdate = !JArrays.equals(currentPropsBytes, updatedPropsBytes)
    if (needsUpdate) {
      logger.info("Updated: " + propertiesFile)
      Files.write(propertiesFile.toPath, updatedPropsBytes)
    }
  }
}
