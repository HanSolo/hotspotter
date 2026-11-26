package eu.hansolo.hotspotter.agent;

/**
 * Bytecode backend. Exactly one implementation named {@code eu.hansolo.hotspotter.agent.impl.InstrumenterImpl} is packaged into each agent JAR (ASM-based or Classfile-API-based).
 * Implementations must insert, at the start of every method with a body that the registry accepts: {@code eu.hansolo.hotspotter.counters.Counters.COUNTS[id]++}.
 */
public interface MethodInstrumenter {

    /**
     * @return the transformed class bytes, or {@code null} if nothing was instrumented
     */
    byte[] instrument(byte[] classBytes, ClassLoader loader, Registry registry) throws Exception;
}
