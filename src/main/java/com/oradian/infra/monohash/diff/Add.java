package com.oradian.infra.monohash.diff;

import com.oradian.infra.monohash.util.Hex;

public final class Add extends Change {
    public final String dstPath;
    public final byte[] dstHash;

    public Add(final String dstPath, final byte[] dstHash) {
        this.dstPath = dstPath;
        this.dstHash = dstHash;
    }

    @Override
    protected void appendTo(final StringBuilder sb) {
        sb.append("+ ")
                .append(Hex.toHex(dstHash)).append(' ')
                .append(dstPath);
    }
}
