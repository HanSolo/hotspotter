package eu.hansolo.hotspotter.agent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


/**
 * Agent configuration, parsed from the agent argument string, e.g.
 * {@code include=com.acme.*,include=org.foo.*,exclude=*Test,excludeMethods=get*,out=hotspotter-out,interval=10}
 * or just {@code stop}.
 */
public final class Config {
    static final String USAGE = "usage: include=<glob>[,include=<glob>...][,exclude=<glob>...][,excludeMethods=<glob>...][,out=<dir>][,interval=<seconds>][,max=<methods>]  |  stop";

    private final List<Pattern> include         = new ArrayList<>();
    private final List<Pattern> exclude         = new ArrayList<>();
    private final List<Pattern> excludeMethods  = new ArrayList<>();
    private       Path          out             = Paths.get("hotspotter-out");
    private       int           intervalSeconds = 10;
    private       int           maxMethods      = 1 << 20;
    private       boolean       stop;
    private       String        raw             = "";


    private Config() {}


    static Config parse(final String args) {
        if (args == null || args.trim().isEmpty()) { throw new IllegalArgumentException(USAGE); }
        Config config = new Config();
        config.raw = args.trim();
        if (config.raw.equals("stop")) {
            config.stop = true;
            return config;
        }
        for (String part : config.raw.split(",")) {
            String option = part.trim();
            if (option.isEmpty()) { continue; }
            int eq = option.indexOf('=');
            if (eq <= 0) { throw new IllegalArgumentException("bad option '" + option + "'. " + USAGE); }
            String key   = option.substring(0, eq).trim();
            String value = option.substring(eq + 1).trim();
            switch (key) {
                case "include"        : config.include.add(glob(value)); break;
                case "exclude"        : config.exclude.add(glob(value)); break;
                case "excludeMethods" : config.excludeMethods.add(glob(value)); break;
                case "out"            : config.out = Paths.get(value); break;
                case "interval"       : config.intervalSeconds = Math.max(1, Integer.parseInt(value)); break;
                case "max"            : config.maxMethods = Math.max(1, Integer.parseInt(value)); break;
                default               : throw new IllegalArgumentException("unknown option '" + key + "'. " + USAGE);
            }
        }
        if (config.include.isEmpty()) { throw new IllegalArgumentException("at least one include=<glob> is required. " + USAGE); }
        return config;
    }

    Path getOut() { return out; }

    int getIntervalSeconds() { return intervalSeconds; }

    int getMaxMethods() { return maxMethods; }

    boolean isStop() { return stop; }

    /** Class names are dotted, e.g. {@code com.acme.Foo$Bar}. JDK and agent classes are never accepted. */
    boolean acceptClass(final String name) {
        if (name.startsWith("eu.hansolo.hotspotter.") || name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.") || name.contains("$$Lambda") || name.contains("$Proxy")) { return false; }
        boolean included = false;
        for (Pattern pattern : include) {
            if (pattern.matcher(name).matches()) {
                included = true;
                break;
            }
        }
        if (!included) { return false; }
        for (Pattern pattern : exclude) {
            if (pattern.matcher(name).matches()) { return false; }
        }
        return true;
    }

    boolean acceptMethod(final String name) {
        for (Pattern pattern : excludeMethods) {
            if (pattern.matcher(name).matches()) { return false; }
        }
        return true;
    }

    /** Simple glob: '*' matches any characters (including dots), everything else is literal. */
    private static Pattern glob(final String glob) {
        StringBuilder regex   = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (char ch : glob.toCharArray()) {
            if (ch == '*') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(".*");
            } else {
                literal.append(ch);
            }
        }
        if (literal.length() > 0) { regex.append(Pattern.quote(literal.toString())); }
        return Pattern.compile(regex.toString());
    }

    @Override public String toString() { return raw; }
}
