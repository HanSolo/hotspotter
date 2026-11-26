package eu.hansolo.hotspotter.analyzer.core;

import java.util.regex.Pattern;


/**
 * @param grouping how to aggregate
 * @param depth    package depth when grouping by package
 * @param filter   only classes matching (find) this pattern, may be null
 * @param exclude  drop classes matching (find) this pattern, may be null
 * @param limit    maximum number of rows, 0 or less means all
 */
public record Query(Grouping grouping, int depth, Pattern filter, Pattern exclude, int limit) {}
