package eu.hansolo.hotspotter.counters;

/**
 * Holds the call counters. This class is loaded by the bootstrap class loader so that instrumented classes from any class loader can reach it.
 * Instrumented methods execute {@code COUNTS[id]++} on method entry (plain, non-atomic increment on purpose: it is the cheapest possible operation and may slightly undercount under heavy contention).
 */
public final class Counters {
    public static final long[] COUNTS = new long[Integer.getInteger("hotspotter.max", 1 << 20)];


    private Counters() {}
}
