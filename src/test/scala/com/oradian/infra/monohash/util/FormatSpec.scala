package com.oradian.infra.monohash
package util

import java.util.{Arrays => JArrays}

import com.topdesk.timetransformer.{DefaultTime, TimeTransformer}

class FormatSpec extends Specification {
  sequential

  "Path formatting" >> {
    "Files format by having backslashes converted to slashes" >> {
      Format.file(new File("""C:\Games\tetris.exe""")) ==== "'C:/Games/tetris.exe'"
    }

    "Files format as <none> when null is provided" >> {
      Format.file(null) ==== "<none>"
    }

    "Directories format by having a trailing slash appended" >> {
      Format.dir(new File("/usr/bin")) ==== "'/usr/bin/'"
    }

    "Directories format as <none> when null is provided" >> {
      Format.dir(null) ==== "<none>"
    }
  }

  "Multi-line formatting" >> {
    "Empty lines format by having an explicit <none> appended" >> {
      Format.lines("Foo", JArrays.asList()) ==== "Foo: <none>"
    }

    "Lines format by indenting with two spaces after newlines" >> {
      Format.lines("Foo", JArrays.asList("bar", "baz")) ==== "Foo:\n  bar\n  baz"
    }
  }

  "Hex formats in square brackets" >> {
    Format.hex("Hi!".getBytes(ISO_8859_1)) ==== "[486921]"
  }

  "Numeric formatting" >> {
    "Integers format with commas as thousand separators (Locale insensitive)" >> {
      Format.i(0) ==== "0"
      Format.i(1000) ==== "1,000"
      Format.i(1234567890) ==== "1,234,567,890"
    }

    "Floats format by having 3 digits of precision (Locale insensitive)" >> {
      Format.f(0) ==== "0.000"
      Format.f(1000) ==== "1,000.000"
      Format.f(12345.678f) ==== "12,345.678"
    }

    "Floats format by rounding half-up" >> {
      Format.f(0.123456789f) ==== "0.123"
      Format.f(0.987654321f) ==== "0.988"
    }
  }

  def timeTravel[T](absoluteMs: Long)(f: => T): T = {
    try {
      TimeTransformer.setTime(() => absoluteMs)
      f
    } finally {
      TimeTransformer.setTime(DefaultTime.INSTANCE)
    }
  }

  "Timing formatting" >> {
    "Millisecond format uses seconds" >> {
      timeTravel(2) {
        Format.timeMillis(1) ==== " (in 0.001 sec)"
      }
      timeTravel(12345678) {
        Format.timeMillis(0) ==== " (in 12,345.678 sec)"
      }
    }

    "Nanoseconds format uses milliseconds" >> {
      timeTravel(2) {
        Format.timeNanos(1999 * 1000) ==== " (in 0.001 ms)"
      }
      timeTravel(12345678) {
        Format.timeNanos(0) ==== " (in 12,345,678.000 ms)"
      }
    }
  }
}
