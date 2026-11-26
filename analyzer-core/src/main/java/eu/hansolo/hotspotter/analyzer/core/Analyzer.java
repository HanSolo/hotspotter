package eu.hansolo.hotspotter.analyzer.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/** Aggregates snapshots into rows. No UI, no I/O. */
public final class Analyzer {

    private Analyzer() {}


    /**
     * @param end   the snapshot to analyse
     * @param start optional earlier snapshot; when given, only the calls made between start and end are counted
     */
    public static Result analyze(final Snapshot end, final Snapshot start, final Query query) {
        double            seconds = start == null ? 0 : Math.max(0.001, (end.timestamp() - start.timestamp()) / 1000.0);
        Map<String, Long> grouped = new HashMap<>();
        long              total   = 0;
        for (Map.Entry<String, Long> entry : end.counts().entrySet()) {
            long value = entry.getValue() - (start == null ? 0 : start.counts().getOrDefault(entry.getKey(), 0L));
            if (value <= 0) { continue; }
            String[] key = entry.getKey().split(",", 3); // class, method, descriptor
            if ((query.filter() != null && !query.filter().matcher(key[0]).find()) || (query.exclude() != null && query.exclude().matcher(key[0]).find())) { continue; }
            grouped.merge(groupName(query, key), value, Long::sum);
            total += value;
        }

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(grouped.entrySet());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())));
        int       size = query.limit() <= 0 ? sorted.size() : Math.min(query.limit(), sorted.size());
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            rows.add(new Row(entry.getKey(), entry.getValue(), total == 0 ? 0 : 100.0 * entry.getValue() / total, start == null ? -1 : entry.getValue() / seconds));
        }
        return new Result(rows, total, start == null ? 0 : seconds);
    }

    /** Rows as CSV, same format as the command line tool. */
    public static String toCsv(final Result result) {
        StringBuilder csv  = new StringBuilder("rank,calls,percent,callsPerSec,name\n");
        int           rank = 1;
        for (Row row : result.rows()) {
            csv.append(rank++).append(',').append(row.calls()).append(',').append(String.format(Locale.ROOT, "%.2f", row.percent())).append(',');
            if (row.callsPerSecond() >= 0) { csv.append(String.format(Locale.ROOT, "%.0f", row.callsPerSecond())); }
            csv.append(",\"").append(row.name().replace("\"", "\"\"")).append("\"\n");
        }
        return csv.toString();
    }

    private static String groupName(final Query query, final String[] key) {
        String className = key[0];
        switch (query.grouping()) {
            case CLASS   : return className;
            case PACKAGE : {
                int      dot   = className.lastIndexOf('.');
                String   pkg   = dot < 0 ? "(default)" : className.substring(0, dot);
                String[] parts = pkg.split("\\.");
                return String.join(".", Arrays.copyOf(parts, Math.min(parts.length, Math.max(1, query.depth()))));
            }
            default      : return className + "#" + key[1] + prettyParams(key[2]);
        }
    }

    /** {@code (ILjava/lang/String;[J)V} to {@code (int, String, long[])}. */
    static String prettyParams(final String descriptor) {
        int close = descriptor.indexOf(')');
        if (!descriptor.startsWith("(") || close < 0) { return descriptor; }
        String       params = descriptor.substring(1, close);
        List<String> types  = new ArrayList<>();
        int          i      = 0;
        while (i < params.length()) {
            int dimensions = 0;
            while (params.charAt(i) == '[') {
                dimensions++;
                i++;
            }
            String type;
            if (params.charAt(i) == 'L') {
                int    semicolon = params.indexOf(';', i);
                String full      = params.substring(i + 1, semicolon);
                type = full.substring(full.lastIndexOf('/') + 1);
                i    = semicolon + 1;
            } else {
                type = switch (params.charAt(i)) {
                    case 'B' -> "byte";
                    case 'C' -> "char";
                    case 'D' -> "double";
                    case 'F' -> "float";
                    case 'I' -> "int";
                    case 'J' -> "long";
                    case 'S' -> "short";
                    case 'Z' -> "boolean";
                    default  -> String.valueOf(params.charAt(i));
                };
                i++;
            }
            types.add(type + "[]".repeat(dimensions));
        }
        return "(" + String.join(", ", types) + ")";
    }
}
