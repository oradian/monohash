package com.oradian.infra.monohash
package param

class ConcurrencySpec extends Specification {
  sequential

  "Default is CPU * 1" >> {
    Concurrency.DEFAULT ==== Concurrency.cpuRelative(1)
  }

  "Fixed concurrency" >> {
    "Concurrency calculation" >> {
      Concurrency.fixed(8).getConcurrency ==== 8
    }

    "Respects constraints from monohash.properties" >> {
      Concurrency.fixed(0) must throwA[IllegalArgumentException]("Fixed concurrency cannot be lower than 1, got: 0")
      Concurrency.fixed(Int.MaxValue) must throwA[IllegalArgumentException]("Fixed concurrency cannot be higher than 1000, got: 2147483647")
    }

    "Parsing tests" >> {
      Concurrency.parseString("1") ==== Concurrency.fixed(1)
      Concurrency.parseString("1.0") must throwA[ParamParseException]("Could not parse fixed concurrency: 1.0")
      Concurrency.parseString("0") must throwA[ParamParseException]("Fixed concurrency cannot be lower than 1, got: 0")
    }

    ".toString, .hashCode & .equals" >> {
      val c3 = Concurrency.fixed(3)
      c3.toString ==== "Concurrency.Fixed(3)"
      c3.## ==== 3

      val a4 = Concurrency.fixed(4)
      a4.equals(a4) ==== true
      a4 !=== c3
      (a4: AnyRef).equals("foo") ==== false

      val b4 = Concurrency.fixed(4)
      a4.equals(b4) ==== true
      a4 must not beTheSameAs b4
    }

    "Concurrency property" >> {
      val c8 = Concurrency.fixed(8)
      c8.concurrency ==== 8

      c8.withConcurrency(8) must beTheSameAs(c8)
      c8.withConcurrency(9) ==== Concurrency.fixed(9)
    }
  }

  "CPU-relative concurrency" >> {
    "Concurrency calculation" >> {
      val cpus = Runtime.getRuntime.availableProcessors()
      Concurrency.cpuRelative(2).getConcurrency ==== cpus * 2
    }

    "Respects constraints from monohash.properties" >> {
      Concurrency.cpuRelative(Double.NegativeInfinity) must throwA[IllegalArgumentException]("CPU-relative concurrency factor needs to be a finite number, got: -Infinity")
      Concurrency.cpuRelative(0.05) must throwA[IllegalArgumentException]("CPU-relative concurrency factor cannot be lower than 0\\.1, got: 0\\.05")
      Concurrency.cpuRelative(1E100) must throwA[IllegalArgumentException]("CPU-relative concurrency factor cannot be higher than 10\\.0, got: 1\\.0E100")
    }

    "Parsing tests" >> {
      Concurrency.parseString("cpu") ==== Concurrency.cpuRelative(1)
      Concurrency.parseString("CPU * 2") ==== Concurrency.cpuRelative(2)
      Concurrency.parseString("Cpu* 4.5") ==== Concurrency.cpuRelative(4.5)
      Concurrency.parseString("cpU * 0." + ("0" * 1000) + "1") must throwA[ParamParseException]("CPU-relative concurrency factor cannot be lower than 0\\.1, got: 0\\.0")
      Concurrency.parseString("Cpu * " + ("1" * 1000)) must throwA[ParamParseException]("CPU-relative concurrency factor needs to be a finite number, got: Infinity")
      Concurrency.parseString("cpu * 2E0") must throwA[ParamParseException]("Could not parse CPU-relative concurrency: cpu \\* 2E0")
      Concurrency.parseString("abcpuxyz") must throwA[ParamParseException]("Could not parse CPU-relative concurrency: abcpuxyz")
    }

    ".toString, .hashCode & .equals" >> {
      val c3 = Concurrency.cpuRelative(3)
      c3.toString ==== "Concurrency.CpuRelative(3.0)"
      c3.## ==== 3.0.hashCode

      val a4 = Concurrency.cpuRelative(4)
      a4.equals(a4) ==== true
      a4 !=== c3
      (a4: AnyRef).equals("foo") ==== false

      val b4 = Concurrency.cpuRelative(4)
      a4.equals(b4) ==== true
      a4 must not beTheSameAs b4

      a4.withFactor(4) must beTheSameAs(a4)
      a4.withFactor(3) ==== c3
    }

    "Factor property" >> {
      val f2 = Concurrency.cpuRelative(2)
      f2.factor ==== 2

      f2.withFactor(2) must beTheSameAs(f2)
      f2.withFactor(3) ==== Concurrency.cpuRelative(3)
    }
  }
}
