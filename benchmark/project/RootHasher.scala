import java.io.File
import java.nio.file.Files
import java.util.Collections

import com.oradian.infra.monohash.diff.Diff
import com.oradian.infra.monohash.util.Hex
import com.oradian.infra.monohash.{HashResults, Logger, MonoHash}

class RootHasher(logger: Logger, hashPlan: File, export: File) {
  private[this] val monohash = MonoHash
    .withLogger(logger)
    .withHashPlan(hashPlan)
    .withExport(export)

  def onDiff(change: String => Boolean): Unit = {
    val results = monohash.run()
    val hexHash = Hex.toHex(results.hash())

    val previousResults = if (export.isFile) {
      val previousExport = Files.readAllBytes(export.toPath)
      Some(HashResults.apply(logger, monohash.algorithm, previousExport))
    } else {
      None
    }

    if (previousResults.contains(results)) {
      logger.error("previousResults.contains(results):" + previousResults.contains(results))
      logger.warn("Build up to date")
    } else {
      val previousMap = previousResults.map(_.toMap).getOrElse(Collections.emptyMap[String, Array[Byte]])
      val diff = Diff.apply(previousMap, results.toMap)

      if (previousResults.isEmpty) {
        logger.warn("Making new build ...");
      } else {
        logger.warn(diff.toString);
      }

      if (change(hexHash)) {
        val newResults = monohash.run()
        if (newResults == results) {
          results.export(export)
        } else {
          val newDiff = Diff.apply(results.toMap, newResults.toMap)
          logger.error("!!! MonoHash has MUTATED during the build !!!\n\n" + newDiff +
s"""This means that you either modified files while the build was running,
  * OR *
that you need to update the [hash plan] '$hashPlan' and blacklist these changes from tracking
"""
          )
        }
      }
    }
  }
}
