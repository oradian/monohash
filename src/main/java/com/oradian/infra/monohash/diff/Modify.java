package com.oradian.infra.monohash.diff;

import com.oradian.infra.monohash.Hex;

public final class Modify extends Change {
    public final String path;
    public final byte[] srcHash;
    public final byte[] dstHash;

    public Modify(final String path, final byte[] srcHash, final byte[] dstHash) {
        this.path = path;
        this.srcHash = srcHash;
        this.dstHash = dstHash;
    }

    @Override
    protected void appendTo(final StringBuilder sb) {
        sb.append("! ")
                .append(Hex.toHex(dstHash)).append(' ')
                .append(path)
                .append(" (previously: ").append(Hex.toHex(srcHash)).append(')');
    }
}
