package com.oradian.infra.monohash;

public enum Verification {
    OFF,     // don't perform verification against previous export file
    WARN,    // perform verification if previous export file exists, log differences as WARN
    REQUIRE, // require previous export file, on mismatch log as ERROR and explode
}
