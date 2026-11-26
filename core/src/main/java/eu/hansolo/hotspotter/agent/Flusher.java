package eu.hansolo.hotspotter.agent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


/**
 * Periodically writes the cumulative counters to {@code snapshot-NNNNN.csv}. This is the only place where I/O happens, and it runs on its own daemon thread, never on application threads.
 * File format: first line {@code #ts=<epoch millis>}, then {@code class,method,descriptor,count} for every method with count &gt; 0.
 */
final class Flusher extends Thread {
    private final    Registry registry;
    private final    long[]   counts;
    private final    Path     dir;
    private final    long     intervalMillis;
    private volatile boolean  running = true;
    private          int      seq;


    Flusher(final Registry registry, final long[] counts, final Path dir, final int intervalSeconds) {
        super("hotspotter-flusher");
        setDaemon(true);
        this.registry       = registry;
        this.counts         = counts;
        this.dir            = dir;
        this.intervalMillis = intervalSeconds * 1000L;
    }


    @Override public void run() {
        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                return;
            }
            snapshot();
        }
    }

    void shutdown() {
        running = false;
        interrupt();
    }

    synchronized void snapshot() {
        try {
            String[][] entries = registry.entries();
            String     name    = String.format("snapshot-%05d.csv", seq);
            Path       tmp     = dir.resolve(name + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write("#ts=" + System.currentTimeMillis());
                writer.newLine();
                for (int i = 0; i < entries.length; i++) {
                    long count = counts[i];
                    if (count > 0) {
                        writer.write(entries[i][0] + "," + entries[i][1] + "," + entries[i][2] + "," + count);
                        writer.newLine();
                    }
                }
            }
            Files.move(tmp, dir.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            seq++;
        } catch (IOException | RuntimeException e) {
            Agent.log("snapshot failed: " + e);
        }
    }
}
