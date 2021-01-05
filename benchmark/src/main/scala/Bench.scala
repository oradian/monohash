package com.oradian.infra.monohash

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Thread)
class Bench {

  @Setup(Level.Iteration)
  def setup(): Unit = {
  }

  @TearDown(Level.Iteration)
  def teardown(): Unit = {
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def test(bh: Blackhole): Unit = {
    bh.consume(com.oradian.infra.monohash.param.Verification.values())
  }
}
