package eu.hansolo.hotspotter.analyzer;

import eu.hansolo.hotspotter.analyzer.core.Analyzer;
import eu.hansolo.hotspotter.analyzer.core.Grouping;
import eu.hansolo.hotspotter.analyzer.core.Query;
import eu.hansolo.hotspotter.analyzer.core.Result;
import eu.hansolo.hotspotter.analyzer.core.Row;
import eu.hansolo.hotspotter.analyzer.core.Snapshot;
import eu.hansolo.hotspotter.analyzer.core.SnapshotStore;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Command line front end of the analyzer.
 * <pre>
 * snapshots &lt;dir&gt;                      list snapshots with timestamp and total calls
 * top &lt;dir|file&gt; [options]             most called methods / classes / packages
 *
 * options:
 *   --n 25                      rows to show (0 = all)
 *   --by method|class|package   grouping (default method)
 *   --depth 2                   package depth when grouping by package
 *   --filter REGEX              only classes matching (find) REGEX
 *   --exclude REGEX             drop classes matching REGEX
 *   --from IDX                  diff against this snapshot (0-based, negative = from end); shows calls/s
 *   --to IDX                    snapshot to analyse (default -1 = last)
 *   --csv                       machine readable output
 * </pre>
 */
public final class Main {

    private Main() {}


    public static void main(final String[] args) throws IOException {
        if (args.length < 2) { usage(); }
        String              command = args[0];
        List<Path>          files   = SnapshotStore.findSnapshots(Paths.get(args[1]));
        Map<String, String> options = parseOptions(args, 2);
        if (files.isEmpty()) {
            System.err.println("no snapshot-*.csv found in " + args[1]);
            System.exit(1);
        }
        switch (command) {
            case "snapshots" -> listSnapshots(files);
            case "top"       -> top(files, options);
            default          -> usage();
        }
    }

    private static void listSnapshots(final List<Path> files) throws IOException {
        System.out.printf("%3s  %-24s %15s  %s%n", "idx", "time", "total calls", "file");
        for (int i = 0; i < files.size(); i++) {
            Snapshot snapshot = SnapshotStore.load(files.get(i));
            System.out.printf("%3d  %-24s %,15d  %s%n", i, Instant.ofEpochMilli(snapshot.timestamp()), snapshot.total(), snapshot.file().getFileName());
        }
    }

    private static void top(final List<Path> files, final Map<String, String> options) throws IOException {
        Grouping grouping = Grouping.parse(options.getOrDefault("by", "method"));
        Pattern  filter   = options.containsKey("filter") ? Pattern.compile(options.get("filter")) : null;
        Pattern  exclude  = options.containsKey("exclude") ? Pattern.compile(options.get("exclude")) : null;
        Query    query    = new Query(grouping, Integer.parseInt(options.getOrDefault("depth", "2")), filter, exclude, Integer.parseInt(options.getOrDefault("n", "25")));
        Snapshot end      = SnapshotStore.load(files.get(index(options.getOrDefault("to", "-1"), files.size())));
        Snapshot start    = options.containsKey("from") ? SnapshotStore.load(files.get(index(options.get("from"), files.size()))) : null;
        Result   result   = Analyzer.analyze(end, start, query);

        if (options.containsKey("csv")) {
            System.out.print(Analyzer.toCsv(result));
            return;
        }
        System.out.printf("%s (%s)  total calls: %,d%n%n", start == null ? "cumulative" : "delta over " + String.format("%.1fs", result.seconds()), options.getOrDefault("by", "method"), result.totalCalls());
        System.out.printf("%4s %16s %7s %14s  %s%n", "#", "calls", "%", start == null ? "" : "calls/s", "name");
        int rank = 1;
        for (Row row : result.rows()) {
            String rate = row.callsPerSecond() < 0 ? "" : String.format("%,.0f", row.callsPerSecond());
            System.out.printf("%4d %,16d %6.2f%% %14s  %s%n", rank++, row.calls(), row.percent(), rate, row.name());
        }
    }

    private static int index(final String value, final int size) {
        int parsed = Integer.parseInt(value);
        int index  = parsed < 0 ? size + parsed : parsed;
        if (index < 0 || index >= size) { throw new IllegalArgumentException("snapshot index " + value + " out of range (0.." + (size - 1) + ")"); }
        return index;
    }

    private static Map<String, String> parseOptions(final String[] args, final int from) {
        Map<String, String> options = new HashMap<>();
        for (int i = from; i < args.length; i++) {
            if (!args[i].startsWith("--")) { usage(); }
            String key = args[i].substring(2);
            if (key.equals("csv")) {
                options.put(key, "true");
            } else if (i + 1 < args.length) {
                options.put(key, args[++i]);
            } else {
                usage();
            }
        }
        return options;
    }

    private static void usage() {
        System.err.println("""
                usage: hotspotter-analyzer snapshots <dir>
                       hotspotter-analyzer top <dir|file> [--n 25] [--by method|class|package] [--depth 2] [--filter REGEX] [--exclude REGEX] [--from IDX] [--to IDX] [--csv]
                <dir> may be the agent output directory (newest run is used) or a run-* directory.
                --from IDX shows the calls made between two snapshots plus calls/s (negative IDX counts from the end).""");
        System.exit(2);
    }
}
