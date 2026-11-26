package eu.hansolo.hotspotter.attach;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.File;


/**
 * Tiny CLI to load the agent into a running JVM (run it with JDK 11+).
 * <pre>
 * java -jar hotspotter-attach.jar list
 * java -jar hotspotter-attach.jar &lt;pid&gt; &lt;agent-jar&gt; "include=com.acme.*,interval=10"
 * java -jar hotspotter-attach.jar &lt;pid&gt; &lt;agent-jar&gt; stop
 * </pre>
 */
public final class Attach {

    private Attach() {}


    public static void main(final String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("list")) {
            for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) { System.out.println(descriptor.id() + "\t" + descriptor.displayName()); }
            return;
        }
        if (args.length != 3) {
            System.err.println("usage: hotspotter-attach list | <pid> <agent-jar> <agent-args|stop>");
            System.exit(2);
        }
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            vm.loadAgent(new File(args[1]).getAbsolutePath(), args[2]);
            System.out.println("agent command sent to JVM " + args[0] + " (see its stderr for status)");
        } finally {
            vm.detach();
        }
    }
}
