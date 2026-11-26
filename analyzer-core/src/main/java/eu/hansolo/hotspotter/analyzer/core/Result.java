package eu.hansolo.hotspotter.analyzer.core;

import java.util.List;


/**
 * @param rows       the rows, most called first
 * @param totalCalls total over all rows that passed the filters (not only the returned rows)
 * @param seconds    time between the two compared snapshots, 0 when only one snapshot was analysed
 */
public record Result(List<Row> rows, long totalCalls, double seconds) {

    public boolean hasRate() { return seconds > 0; }
}
