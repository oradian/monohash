package com.oradian.infra.monohash.diff;

import com.oradian.infra.monohash.util.Hex;

public final class Delete extends Change {
    public final String srcPath;
    public final byte[] srcHash;

    public Delete(final String srcPath, final byte[] srcHash) {
        this.srcPath = srcPath;
        this.srcHash = srcHash;
    }

    @Override
    protected void appendTo(final StringBuilder sb) {
        sb.append("- ")
                .append(Hex.toHex(srcHash)).append(' ')
                .append(srcPath);
    }
}
