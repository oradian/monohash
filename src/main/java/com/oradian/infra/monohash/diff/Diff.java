package com.oradian.infra.monohash.diff;

import java.util.*;

public class Diff {
    public final List<Add> adds;
    public final List<Rename> renames;
    public final List<Modify> modifies;
    public final List<Delete> deletes;

    public final List<List<? extends Change>> changes;

    public Diff(
            final List<Add> adds,
            final List<Rename> renames,
            final List<Modify> modifies,
            final List<Delete> deletes) {
        this.adds = adds;
        this.renames = renames;
        this.modifies = modifies;
        this.deletes = deletes;

        changes = Arrays.asList(adds, renames, modifies, deletes);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return adds.size() + renames.size() + modifies.size() + deletes.size();
    }

    private static class AddRename {
        final String dstPath;
        final byte[] dstHash;
        String srcPath; // for upgrading Add to Rename

        public AddRename(final String dstPath, final byte[] dstHash) {
            this.dstPath = dstPath;
            this.dstHash = dstHash;
        }
    }

    private static class ArrKey {
        final byte[] arr;

        public ArrKey(final byte[] arr) {
            this.arr = arr;
        }

        @Override
        public int hashCode() {
            return (arr[0] << 24) + ((arr[1] & 0xff) << 16) + ((arr[2] & 0xff) << 8) + (arr[3] & 0xff);
        }

        @Override
        public boolean equals(final Object obj) {
            return Arrays.equals(arr, ((ArrKey) obj).arr);
        }
    }

    public static Diff apply(
            final Map<String, byte[]> src,
            final Map<String, byte[]> dst) {

        // defensive copy so that we don't exhaust the src collection
        final LinkedHashMap<String, byte[]> srcCopy = new LinkedHashMap<>(src);

        final Map<ArrKey, List<AddRename>> addRenames = new LinkedHashMap<>();
        final List<Modify> modifies = new ArrayList<>();
        for (final Map.Entry<String, byte[]> dstEntry : dst.entrySet()) {
            final String dstPath = dstEntry.getKey();
            final byte[] dstHash = dstEntry.getValue();

            final byte[] srcHash = srcCopy.remove(dstPath);
            if (srcHash == null) {
                final ArrKey reverseKey = new ArrKey(dstHash);
                addRenames.computeIfAbsent(reverseKey, unused -> new ArrayList<>()).add(new AddRename(dstPath, dstHash));
            } else if (!Arrays.equals(dstHash, srcHash)) {
                modifies.add(new Modify(dstPath, srcHash, dstHash));
            }
        }

        final List<Delete> deletes = new ArrayList<>();
        for (final Map.Entry<String, byte[]> srcEntry : srcCopy.entrySet()) {
            final String srcPath = srcEntry.getKey();
            final byte[] srcHash = srcEntry.getValue();

            final ArrKey reverseKey = new ArrKey(srcHash);
            final List<AddRename> adds = addRenames.get(reverseKey);
            if (adds == null) {
                deletes.add(new Delete(srcPath, srcHash));
            } else {
                adds.stream()
                        .filter(entry -> entry.srcPath == null)
                        .limit(1)
                        .forEach(entry -> entry.srcPath = srcPath);
            }
        }

        final List<Add> adds = new ArrayList<>();
        final List<Rename> renames = new ArrayList<>();
        for (final List<AddRename> ars : addRenames.values()) {
            for (final AddRename ar : ars) {
                if (ar.srcPath == null) {
                    adds.add(new Add(ar.dstPath, ar.dstHash));
                } else {
                    renames.add(new Rename(ar.srcPath, ar.dstPath, ar.dstHash));
                }
            }
        }

        return new Diff(adds, renames, modifies, deletes);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (!adds.isEmpty()) {
            sb.append("Added files:").append('\n');
            for (final Add add : adds) {
                add.appendTo(sb);
                sb.append('\n');
            }
            sb.append('\n');
        }
        if (!renames.isEmpty()) {
            sb.append("Renamed files:").append('\n');
            for (final Rename rename : renames) {
                rename.appendTo(sb);
                sb.append('\n');
            }
            sb.append('\n');
        }
        if (!modifies.isEmpty()) {
            sb.append("Modified files:").append('\n');
            for (final Modify modify : modifies) {
                modify.appendTo(sb);
                sb.append('\n');
            }
            sb.append('\n');
        }
        if (!deletes.isEmpty()) {
            sb.append("Deleted files:").append('\n');
            for (final Delete delete : deletes) {
                delete.appendTo(sb);
                sb.append('\n');
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}
