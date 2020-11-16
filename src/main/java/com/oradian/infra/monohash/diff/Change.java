package com.oradian.infra.monohash.diff;

public abstract class Change {
    protected abstract void appendTo(final StringBuilder sb);

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendTo(sb);
        return sb.toString();
    }
}
