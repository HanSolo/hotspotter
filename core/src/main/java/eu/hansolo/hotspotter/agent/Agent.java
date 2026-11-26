package eu.hansolo.hotspotter.agent;

import java.lang.instrument.Instrumentation;


/**
 * Entry point. Works both at JVM start ({@code -javaagent:hotspotter-agent.jar=<args>}) and when attached to a running JVM.
 * Passing {@code stop} as argument removes the instrumentation again and writes a final snapshot. A stopped agent cannot be restarted in the same JVM.
 */
public final class Agent {
    private static Session session;
    private static boolean stopped;


    private Agent() {}


    public static void premain(final String args, final Instrumentation inst) { run(args, inst); }

    public static void agentmain(final String args, final Instrumentation inst) { run(args, inst); }

    private static synchronized void run(final String args, final Instrumentation inst) {
        try {
            Config config = Config.parse(args);
            if (config.isStop()) {
                if (session == null) {
                    log("not running, nothing to stop.");
                } else {
                    session.stop();
                    session = null;
                    stopped = true;
                }
                return;
            }
            if (session != null) {
                log("already active; send 'stop' first.");
                return;
            }
            if (stopped) {
                log("this agent was stopped and cannot be restarted in the same JVM.");
                return;
            }
            session = Session.start(config, inst);
        } catch (Throwable t) {
            log("failed: " + t);
        }
    }

    static void log(final String message) { System.err.println("[hotspotter] " + message); }
}
