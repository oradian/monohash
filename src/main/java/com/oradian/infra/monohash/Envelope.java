package com.oradian.infra.monohash;

public enum Envelope {
    RAW, // do not wrap bytes in an envelope
    GIT, // be compatible with the way Git hashes blobs
}
