import java.io.File
import java.nio.file.Files
import java.util.Collections

import com.oradian.infra.monohash._
import com.oradian.infra.monohash.diff.Diff
import com.oradian.infra.monohash.util.Hex

class RootHasher(logger: Logger, hashPlan: File, exportFile: File) {
  private[this] val monohash = new MonoHash(logger)
  private[this] val algorithm = new Algorithm(Algorithm.GIT)

  private[this] def run(): HashResults = monohash.run(hashPlan, null, algorithm, 2, Verification.OFF)

  def onDiff(previousHash: Option[String])(change: String => Boolean): Unit = {
    val results = run()
    val hexHash = Hex.toHex(results.hash())

    val previousResults = if (exportFile.isFile) {
      val previousExport = Files.readAllBytes(exportFile.toPath)
      Some(HashResults.apply(logger, algorithm, previousExport))
    } else {
      None
    }

    if (previousHash.contains(hexHash) || previousResults.contains(results)) {
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
        val newResults = run()
        if (newResults == results) {
          results.`export`(exportFile)
        } else {
          val newDiff = Diff.apply(results.toMap, newResults.toMap)
          logger.error("!!! MonoHash has MUTATED during the build !!!\n\n" + newDiff +
s"""This means that you either modified files while the build was running,
  * OR *
that you need to update the [hash plan] '$hashPlan' to blacklist these changes from tracking
"""
          )
        }
      }
    }
  }
}
