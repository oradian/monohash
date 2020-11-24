package com.oradian.infra.monohash.diff;

public abstract class Change {
    protected abstract void appendTo(final StringBuilder sb);

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        return toString().equals(obj.toString());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendTo(sb);
        return sb.toString();
    }
}
