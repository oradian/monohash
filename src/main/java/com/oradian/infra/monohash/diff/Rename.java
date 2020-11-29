package com.oradian.infra.monohash.diff;

import com.oradian.infra.monohash.util.Hex;

public final class Rename extends Change {
    public final String srcPath;
    public final String dstPath;
    public final byte[] hash;

    public Rename(final String srcPath, final String dstPath, final byte[] hash) {
        this.srcPath = srcPath;
        this.dstPath = dstPath;
        this.hash = hash;
    }

    @Override
    protected void appendTo(final StringBuilder sb) {
        sb.append("~ ")
                .append(Hex.toHex(hash)).append(' ')
                .append(dstPath)
                .append(" (previously: ").append(srcPath).append(')');
    }
}
