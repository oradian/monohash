package com.oradian.infra.monohash.single;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class SingleJava {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String ALGORITHM = "MD5";

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.ISO_8859_1);
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.ISO_8859_1);
    }

    private static void read(final File file) throws Throwable {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read == -1) {
                    break;
                }
                buffer.flip();
            }
        }
        final long endAt = System.nanoTime();

        System.out.println("Read took: " + (endAt - startAt) / 1000000);
    }

    private static void baseline(final File file) throws Throwable {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        final MessageDigest md = MessageDigest.getInstance(ALGORITHM);

        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            md.reset();

            while (true) {
                buffer.clear();
                final int read = fc.read(buffer);
                if (read == -1) {
                    break;
                }
                buffer.flip();
                md.update(buffer);
            }
        }
        final byte[] result = md.digest();
        final long endAt = System.nanoTime();

        System.out.println("Baseline took: " + (endAt - startAt) / 1000000 + " (" + bytesToHex(result) + ")");
    }

    private static void parallelViaExecutor(final File file) throws Throwable {
        final ByteBuffer buffer1 = ByteBuffer.allocateDirect(BUFFER_SIZE);
        final ByteBuffer buffer2 = ByteBuffer.allocateDirect(BUFFER_SIZE);
        final MessageDigest md = MessageDigest.getInstance(ALGORITHM);

        final ExecutorService es = Executors.newSingleThreadExecutor();

        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            md.reset();

            Future<?> hasher1 = null;
            Future<?> hasher2 = CompletableFuture.completedFuture(null);

            final Runnable run1 = () -> md.update(buffer1);
            final Runnable run2 = () -> md.update(buffer2);

            while (true) {
                buffer1.clear();
                final int read1 = fc.read(buffer1);
                if (read1 == -1) {
                    hasher2.get();
                    break;
                }
                buffer1.flip();
                hasher2.get();
                hasher1 = es.submit(run1);

                buffer2.clear();
                final int read2 = fc.read(buffer2);
                if (read2 == -1) {
                    hasher1.get();
                    break;
                }
                buffer2.flip();

                hasher1.get();
                hasher2 = es.submit(run2);
            }
        }
        final byte[] result = md.digest();
        final long endAt = System.nanoTime();

        System.out.println("Parallel executor took: " + (endAt - startAt) / 1000000 + " (" + bytesToHex(result) + ")");

        es.shutdown();
    }

    private static void parallelViaAtomicSpinlock(final File file) throws Throwable {
        final ByteBuffer buffer1 = ByteBuffer.allocateDirect(BUFFER_SIZE);
        final ByteBuffer buffer2 = ByteBuffer.allocateDirect(BUFFER_SIZE);
        final MessageDigest md = MessageDigest.getInstance(ALGORITHM);

        final ExecutorService es = Executors.newSingleThreadExecutor();

        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            md.reset();

            final AtomicBoolean finished = new AtomicBoolean(false);
            final AtomicBoolean okToRead1 = new AtomicBoolean(true);
            final AtomicBoolean okToHash1 = new AtomicBoolean(false);
            final AtomicBoolean okToRead2 = new AtomicBoolean(true);
            final AtomicBoolean okToHash2 = new AtomicBoolean(false);

            final Thread thread = new Thread(() -> {
                while (true) {
                    while (!okToHash1.get()) {}
                    if (finished.get()) {
                        return;
                    }
                    md.update(buffer1);
                    okToRead1.set(true);
                    okToHash1.set(false);

                    while (!okToHash2.get()) {}
                    if (finished.get()) {
                        return;
                    }
                    md.update(buffer2);
                    okToRead2.set(true);
                    okToHash2.set(false);
                }
            });
            thread.start();

            while (true) {
                while (!okToRead1.get()) {}
                buffer1.clear();
                final int read1 = fc.read(buffer1);
                if (read1 == -1) {
                    finished.set(true);
                    okToHash1.set(true);
                    break;
                }
                buffer1.flip();
                okToRead1.set(false);
                okToHash1.set(true);

                while (!okToRead2.get()) {}
                buffer2.clear();
                final int read2 = fc.read(buffer2);
                if (read2 == -1) {
                    finished.set(true);
                    okToHash2.set(true);
                    break;
                }
                buffer2.flip();
                okToRead2.set(false);
                okToHash2.set(true);
            }

            thread.join();
        }
        final byte[] result = md.digest();
        final long endAt = System.nanoTime();

        System.out.println("Parallel atomic spinlock took: " + (endAt - startAt) / 1000000 + " (" + bytesToHex(result) + ")");

        es.shutdown();
    }

    private static void parallelViaSemaphores(final File file) throws Throwable {
        final ByteBuffer buffer1 = ByteBuffer.allocateDirect(BUFFER_SIZE);
        final ByteBuffer buffer2 = ByteBuffer.allocateDirect(BUFFER_SIZE);
        final MessageDigest md = MessageDigest.getInstance(ALGORITHM);

        final ExecutorService es = Executors.newSingleThreadExecutor();

        final long startAt = System.nanoTime();
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r");
             final FileChannel fc = raf.getChannel()) {
            md.reset();

            final AtomicBoolean finished1 = new AtomicBoolean(false);
            final AtomicBoolean finished2 = new AtomicBoolean(false);
            final Semaphore okToRead1 = new Semaphore(1);
            final Semaphore okToHash1 = new Semaphore(0);
            final Semaphore okToRead2 = new Semaphore(1);
            final Semaphore okToHash2 = new Semaphore(0);

            final Thread thread = new Thread(() -> {
                try {
                    while (true) {
                        okToHash1.acquire();
                        if (finished1.get()) {
                            System.out.println("Exiting in 1");
                            return;
                        }
                        System.out.println("UPDATING 1");
                        md.update(buffer1);
                        okToRead1.release();

                        okToHash2.acquire();
                        if (finished2.get()) {
                            System.out.println("Exiting in 2");
                            return;
                        }
                        System.out.println("UPDATING 2");
                        md.update(buffer2);
                        okToRead2.release();
                    }
                } catch (final InterruptedException e) {};
            });
            thread.start();

            while (true) {
                okToRead1.acquire();
                buffer1.clear();
                final int read1 = fc.read(buffer1);
                if (read1 == -1) {
                    System.out.println("Finished in 1");
                    finished1.set(true);
                    okToHash1.release();
                    break;
                }
                buffer1.flip();
                okToHash1.release();
//try { Thread.sleep(1000); } catch (InterruptedException e){};
                okToRead2.acquire();
                buffer2.clear();
                final int read2 = fc.read(buffer2);
                if (read2 == -1) {
                    System.out.println("Finished in 2");
                    finished2.set(true);
                    okToHash2.release();
                    break;
                }
                buffer2.flip();
                okToHash2.release();
            }

            thread.join();
        }
        final byte[] result = md.digest();
        final long endAt = System.nanoTime();

        System.out.println("Parallel via semaphores took: " + (endAt - startAt) / 1000000 + " (" + bytesToHex(result) + ")");

        es.shutdown();
    }

    private static final int TRIALS = 5;

    public static void main (final String[] args) throws Throwable {
//        final File file = new File(args[0]);
        final File file = new File("d:\\Code\\monohash\\.monohash");
//        final File file = new File("D:\\gc-1.tar");
//        final File file = new File("D:\\zi2u9rbc3pd61.jpg");

        for (int i = 0; i < TRIALS; i++) {
//            read(file);
            baseline(file);
//            parallelViaExecutor(file);
            parallelViaSemaphores(file);
//            parallelViaAtomicSpinlock(file);
        }
    }
}