package eu.hansolo.hotspotter.analyzer.core;

/**
 * @param name           method, class or package name
 * @param calls          number of calls (or delta when two snapshots were compared)
 * @param percent        share of all calls
 * @param callsPerSecond calls per second between two snapshots, or -1 when only one snapshot was analysed
 */
public record Row(String name, long calls, double percent, double callsPerSecond) {}
