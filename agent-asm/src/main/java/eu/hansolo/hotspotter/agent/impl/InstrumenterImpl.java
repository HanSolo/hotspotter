package eu.hansolo.hotspotter.agent.impl;

import eu.hansolo.hotspotter.agent.MethodInstrumenter;
import eu.hansolo.hotspotter.agent.Registry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


/** ASM backend (runs on JDK 8+). Injects {@code Counters.COUNTS[id]++} at method entry. */
public final class InstrumenterImpl implements MethodInstrumenter {
    private static final String COUNTERS = "eu/hansolo/hotspotter/counters/Counters";


    public InstrumenterImpl() {}


    @Override public byte[] instrument(final byte[] classBytes, final ClassLoader loader, final Registry registry) {
        ClassReader reader = new ClassReader(classBytes);
        // COMPUTE_MAXS only: we insert at method start, so existing stack map frames stay valid and ASM never has to load classes (no getCommonSuperClass problems across class loaders).
        ClassWriter          writer  = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        CountingClassVisitor visitor = new CountingClassVisitor(writer, registry);
        reader.accept(visitor, 0);
        return visitor.changed ? writer.toByteArray() : null;
    }


    private static final class CountingClassVisitor extends ClassVisitor {
        private final Registry registry;
        private       String   className;
        private       boolean  changed;


        private CountingClassVisitor(final ClassVisitor delegate, final Registry registry) {
            super(Opcodes.ASM9, delegate);
            this.registry = registry;
        }


        @Override public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            int           skip    = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE;
            if (visitor == null || (access & skip) != 0) { return visitor; }
            final int id = registry.register(className, name, descriptor);
            if (id < 0) { return visitor; }
            changed = true;
            return new MethodVisitor(Opcodes.ASM9, visitor) {
                @Override public void visitCode() {
                    super.visitCode();
                    // COUNTS[id] = COUNTS[id] + 1
                    super.visitFieldInsn(Opcodes.GETSTATIC, COUNTERS, "COUNTS", "[J");
                    super.visitLdcInsn(id);
                    super.visitInsn(Opcodes.DUP2);
                    super.visitInsn(Opcodes.LALOAD);
                    super.visitInsn(Opcodes.LCONST_1);
                    super.visitInsn(Opcodes.LADD);
                    super.visitInsn(Opcodes.LASTORE);
                }
            };
        }
    }
}
