package eu.hansolo.hotspotter.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;


/** One monitoring session: transformer + counters + flusher. */
final class Session implements ClassFileTransformer {
    private static final int    MAX_ERROR_REPORTS = 20;
    private static final String COUNTERS_CLASS    = "eu.hansolo.hotspotter.counters.Counters";
    private static final String IMPL_CLASS        = "eu.hansolo.hotspotter.agent.impl.InstrumenterImpl";

    private final Config             config;
    private final Instrumentation    inst;
    private final Registry           registry;
    private final MethodInstrumenter instrumenter;
    private final Flusher            flusher;
    private final Thread             shutdownHook;
    private       int                errorReports;


    private Session(final Config config, final Instrumentation inst, final long[] counts, final Path runDir) throws Exception {
        this.config       = config;
        this.inst         = inst;
        this.registry     = new Registry(config, counts.length);
        this.instrumenter = (MethodInstrumenter) Class.forName(IMPL_CLASS).getDeclaredConstructor().newInstance();
        this.flusher      = new Flusher(registry, counts, runDir, config.getIntervalSeconds());
        this.shutdownHook = new Thread("hotspotter-shutdown") {
            @Override public void run() { flusher.snapshot(); }
        };
    }


    static Session start(final Config config, final Instrumentation inst) throws Exception {
        long[] counts = installCounters(config, inst);
        Path   runDir = config.getOut().resolve("run-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-pid" + pid());
        Files.createDirectories(runDir);

        Session session = new Session(config, inst, counts, runDir);
        inst.addTransformer(session, true);
        int instrumented = session.retransformLoaded();
        session.flusher.start();
        Runtime.getRuntime().addShutdownHook(session.shutdownHook);
        Agent.log("active. " + instrumented + " already-loaded classes instrumented. Snapshots every " + config.getIntervalSeconds() + "s in " + runDir.toAbsolutePath());
        return session;
    }

    void stop() {
        inst.removeTransformer(this);
        retransformLoaded(); // reverts the instrumentation: the transformer is gone
        flusher.shutdown();
        flusher.snapshot();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM already shutting down
        }
        Agent.log("stopped; final snapshot written.");
    }

    @Override public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
        if (loader == null || className == null) { return null; }
        String dotted = className.replace('/', '.');
        if (!config.acceptClass(dotted)) { return null; }
        try {
            return instrumenter.instrument(classfileBuffer, loader, registry);
        } catch (Throwable t) {
            // Never break the application because of a class we could not instrument.
            synchronized (this) {
                if (errorReports++ < MAX_ERROR_REPORTS) { Agent.log("could not instrument " + dotted + ": " + t); }
            }
            return null;
        }
    }

    /** Retransforms already loaded, matching classes. Returns how many were submitted. */
    private int retransformLoaded() {
        if (!inst.isRetransformClassesSupported()) {
            Agent.log("retransformation not supported by this JVM; only newly loaded classes are counted.");
            return 0;
        }
        List<Class<?>> todo = new ArrayList<>();
        for (Class<?> cls : inst.getAllLoadedClasses()) {
            if (cls.getClassLoader() != null && !cls.isArray() && inst.isModifiableClass(cls) && config.acceptClass(cls.getName())) { todo.add(cls); }
        }
        int done = 0;
        for (int i = 0; i < todo.size(); i += 100) {
            List<Class<?>> batch = todo.subList(i, Math.min(todo.size(), i + 100));
            try {
                inst.retransformClasses(batch.toArray(new Class<?>[0]));
                done += batch.size();
            } catch (Throwable t) {
                for (Class<?> cls : batch) {
                    try {
                        inst.retransformClasses(cls);
                        done++;
                    } catch (Throwable inner) {
                        Agent.log("could not retransform " + cls.getName() + ": " + inner);
                    }
                }
            }
        }
        return done;
    }

    /**
     * Makes the {@code Counters} class available to every class loader by appending a tiny JAR (extracted from this agent JAR) to the bootstrap search path.
     */
    private static long[] installCounters(final Config config, final Instrumentation inst) throws Exception {
        System.setProperty("hotspotter.max", Integer.toString(config.getMaxMethods()));
        File jar = File.createTempFile("hotspotter-counters", ".jar");
        jar.deleteOnExit();
        String entry = COUNTERS_CLASS.replace('.', '/') + ".class";
        try (InputStream in = Session.class.getResourceAsStream("/" + entry); OutputStream fos = Files.newOutputStream(jar.toPath()); JarOutputStream out = new JarOutputStream(fos)) {
            if (in == null) { throw new IOException("Counters.class not found in agent jar"); }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[]                chunk  = new byte[4096];
            int                   read;
            while ((read = in.read(chunk)) != -1) { buffer.write(chunk, 0, read); }
            out.putNextEntry(new JarEntry(entry));
            out.write(buffer.toByteArray());
            out.closeEntry();
        }
        inst.appendToBootstrapClassLoaderSearch(new JarFile(jar));
        // Load via the bootstrap loader explicitly: this is the very same class the instrumented code uses.
        Class<?> counters = Class.forName(COUNTERS_CLASS, true, null);
        return (long[]) counters.getField("COUNTS").get(null);
    }

    private static String pid() { return ManagementFactory.getRuntimeMXBean().getName().split("@")[0]; }
}
