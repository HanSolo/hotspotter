package eu.hansolo.hotspotter.analyzer.core;

/** How call counts are aggregated. */
public enum Grouping {
    METHOD, CLASS, PACKAGE;


    public static Grouping parse(final String text) {
        switch (text.toLowerCase()) {
            case "method"  : return METHOD;
            case "class"   : return CLASS;
            case "package" : return PACKAGE;
            default        : throw new IllegalArgumentException("unknown grouping '" + text + "', use method, class or package");
        }
    }

    @Override public String toString() { return name().substring(0, 1) + name().substring(1).toLowerCase(); }
}
