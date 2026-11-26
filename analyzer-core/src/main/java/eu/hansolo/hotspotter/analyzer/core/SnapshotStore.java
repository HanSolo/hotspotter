package eu.hansolo.hotspotter.analyzer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/** Finds and loads the snapshot files written by the agent. */
public final class SnapshotStore {

    private SnapshotStore() {}


    /** Accepts a snapshot file, a run directory, or the base output directory (the newest run is used). */
    public static List<Path> findSnapshots(final Path path) throws IOException {
        if (Files.isRegularFile(path)) { return List.of(path); }
        List<Path> files = listSnapshotFiles(path);
        if (!files.isEmpty()) { return files; }
        try (Stream<Path> children = Files.list(path)) {
            Path newestRun = children.filter(child -> Files.isDirectory(child) && child.getFileName().toString().startsWith("run-")).sorted().reduce((first, second) -> second).orElse(null);
            return newestRun == null ? List.of() : listSnapshotFiles(newestRun);
        }
    }

    public static Snapshot load(final Path file) throws IOException {
        long              timestamp = 0;
        Map<String, Long> counts    = new HashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("#ts=")) {
                timestamp = Long.parseLong(line.substring(4).trim());
            } else if (!line.isEmpty()) {
                int last = line.lastIndexOf(',');
                counts.put(line.substring(0, last), Long.parseLong(line.substring(last + 1)));
            }
        }
        return new Snapshot(file, timestamp, counts);
    }

    private static List<Path> listSnapshotFiles(final Path dir) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(child -> {
                String name = child.getFileName().toString();
                return name.startsWith("snapshot-") && name.endsWith(".csv");
            }).sorted().collect(Collectors.toList());
        }
    }
}
