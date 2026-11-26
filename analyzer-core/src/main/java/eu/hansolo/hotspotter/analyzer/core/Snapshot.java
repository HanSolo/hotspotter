package eu.hansolo.hotspotter.analyzer.core;

import java.nio.file.Path;
import java.util.Map;


/**
 * One snapshot file written by the agent.
 * @param file      the snapshot file
 * @param timestamp epoch millis when the snapshot was taken
 * @param counts    cumulative call counts, key is {@code class,method,descriptor}
 */
public record Snapshot(Path file, long timestamp, Map<String, Long> counts) {

    public long total() {
        long total = 0;
        for (long value : counts.values()) { total += value; }
        return total;
    }
}
