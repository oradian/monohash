package com.oradian.infra.monohash

import com.oradian.infra.monohash.impl.{NoopLogger, PrintStreamLogger}
import com.oradian.infra.monohash.param._

class MonoHashBuilderSpec extends Specification with BouncyCastleHelpers {
  "Test static MonoHash forwarders" >> {
    "Defaults return the DEFAULT singleton" >> {
      Seq(
        MonoHash.withLogger(NoopLogger.INSTANCE),
        MonoHash.withAlgorithm(Algorithm.DEFAULT),
        MonoHash.withConcurrency(Concurrency.DEFAULT),
        MonoHash.withVerification(Verification.DEFAULT),
        MonoHash.withExport(null),
      ).forall { mhb =>
        mhb must beTheSameAs(MonoHashBuilder.DEFAULT)
      }
    }

    "Non-defaults copy the singleton into a new builder" >> {
      val logger = new LoggingLogger(LogLevel.TRACE)
      MonoHash.withLogger(logger).logger must beTheSameAs(logger)

      val algorithm = new Algorithm("SHA-256")
      MonoHash.withAlgorithm(algorithm).algorithm must beTheSameAs(algorithm)

      val concurrency = Concurrency.fixed(3)
      MonoHash.withConcurrency(concurrency).concurrency must beTheSameAs(concurrency)

      val verification = Verification.WARN
      MonoHash.withVerification(verification).verification must beTheSameAs(verification)

      val hashPlan = new File("hashPlan.file")
      MonoHash.withHashPlan(hashPlan).hashPlan must beTheSameAs(hashPlan)

      val export = new File("export.file")
      MonoHash.withExport(export).export must beTheSameAs(export)
    }
  }

  ".toString" >> {
    val mhbDefault = MonoHashBuilder.DEFAULT
    mhbDefault.toString ====
      "MonoHashBuilder(" +
        "logger=NoopLogger, " +
        "algorithm=Algorithm(name=SHA-1, provider=" + mhbDefault.algorithm.provider.getName + "), " +
        "concurrency=Concurrency.CpuRelative(1.0), " +
        "verification=off, " +
        "export=null" +
      ")"

    val mhbCustom = MonoHash
      .withLogger(new PrintStreamLogger(null, LogLevel.TRACE))
      .withAlgorithm(new Algorithm("sHa-256"))
      .withConcurrency(Concurrency.fixed(5))
      .withVerification(Verification.REQUIRE)
      .withExport(new File("path/to/export.file"))

    val mhbCustomString = mhbCustom.toString
    mhbCustomString ====
      "MonoHashBuilder(" +
        "logger=PrintStreamLogger(logLevel=trace), " +
        "algorithm=Algorithm(name=SHA-256, provider=" + mhbCustom.algorithm.provider.getName +"), " +
        "concurrency=Concurrency.Fixed(5), " +
        "verification=require, " +
        "export='path/to/export.file'" +
      ")"

    val mhbReady = mhbCustom.withHashPlan(new File("path\\to\\hashPlan.file"))
    val mhbReadyString = mhbReady.toString
    mhbReadyString ====
      "MonoHashBuilder.Ready(" +
        "logger=PrintStreamLogger(logLevel=trace), " +
        "algorithm=Algorithm(name=SHA-256, provider=" + mhbReady.algorithm.provider.getName + "), " +
        "concurrency=Concurrency.Fixed(5), " +
        "verification=require, " +
        "hashPlan='path/to/hashPlan.file', " +
        "export='path/to/export.file'" +
      ")"

    // sanity check that formatting is the same
    mhbReadyString ==== mhbCustomString
      .replace("Builder", "Builder.Ready")
      .replace("export=", "hashPlan='path/to/hashPlan.file', export=")

    // it is allowed to remove export
    val mhbReadyNoExport = mhbReady.withExport(null)
    mhbReadyNoExport.toString ====
      mhbReadyString.replace("export='path/to/export.file'", "export=null")
  }

  "MonoHashBuilder .hashCode & .equals" >> {
    val lNoop = MonoHash.withLogger(NoopLogger.INSTANCE)
    lNoop must beTheSameAs(MonoHashBuilder.DEFAULT)
    lNoop.equals(lNoop) ==== true
    lNoop.equals(null) ==== false

    val lLL = lNoop.withLogger(new LoggingLogger(LogLevel.OFF))
    lLL !=== lNoop

    val c7a = lNoop.withConcurrency(Concurrency.fixed(7))
    val c7b = c7a.withConcurrency(Concurrency.fixed(7))
    c7a ==== c7b
    c7a.## ==== c7b.##

    val c8 = c7b.withConcurrency(Concurrency.fixed(8))
    c8 !=== c7b
    val aMD5 = c8.withAlgorithm(new Algorithm("Md5"))
    aMD5 !=== c8
    val vWarn = aMD5.withVerification(Verification.WARN)
    vWarn !=== aMD5
    val e1 = vWarn.withExport(new File("1"))
    e1 !=== vWarn
  }


  "MonoHashBuilder.Ready returns itself on a noop flow setter" >> {
    val hpZ = MonoHash.withHashPlan(new File("Z"))
    val hpX = hpZ.withHashPlan(new File("X"))
    hpX.equals(hpZ) ==== false

    hpX.withLogger(hpX.logger) must beTheSameAs(hpX)
    hpX.withAlgorithm(hpX.algorithm) must beTheSameAs(hpX)
    hpX.withConcurrency(hpX.concurrency) must beTheSameAs(hpX)
    hpX.withVerification(hpX.verification) must beTheSameAs(hpX)
    hpX.withHashPlan(hpX.hashPlan) must beTheSameAs(hpX)
    hpX.withExport(hpX.export) must beTheSameAs(hpX)
  }

  "MonoHashBuilder.Ready .hashCode & .equals" >> {
    val hashPlanX = new File("X")
    val hpXa = MonoHash.withHashPlan(hashPlanX)
    val hpXb = hpXa.withHashPlan(hashPlanX)
    hpXa must beTheSameAs(hpXb)

    val lNoop = hpXb.withLogger(NoopLogger.INSTANCE)
    lNoop must beTheSameAs(hpXb)
    lNoop.equals(lNoop) ==== true
    lNoop.equals(null) ==== false

    val lLL = lNoop.withLogger(new LoggingLogger(LogLevel.OFF))
    lLL !=== lNoop

    val c7a = lNoop.withConcurrency(Concurrency.fixed(7))
    val c7b = c7a.withConcurrency(Concurrency.fixed(7))
    c7a ==== c7b
    c7a.## ==== c7b.##

    val c8 = c7b.withConcurrency(Concurrency.fixed(8))
    c8 !=== c7b
    val aMD5 = c8.withAlgorithm(new Algorithm("Md5"))
    aMD5 !=== c8
    val vWarn = aMD5.withVerification(Verification.WARN)
    vWarn !=== aMD5
    val e1 = vWarn.withExport(new File("1"))
    e1 !=== vWarn
  }
}
