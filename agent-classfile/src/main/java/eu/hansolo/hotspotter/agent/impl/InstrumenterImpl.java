package eu.hansolo.hotspotter.agent.impl;

import eu.hansolo.hotspotter.agent.MethodInstrumenter;
import eu.hansolo.hotspotter.agent.Registry;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.AccessFlag;


/** Classfile API backend (JDK 24+, no dependencies). Injects {@code Counters.COUNTS[id]++} at method entry. */
public final class InstrumenterImpl implements MethodInstrumenter {
    private static final ClassDesc COUNTERS   = ClassDesc.of("eu.hansolo.hotspotter.counters.Counters");
    private static final ClassDesc LONG_ARRAY = ConstantDescs.CD_long.arrayType();


    public InstrumenterImpl() {}


    @Override public byte[] instrument(final byte[] classBytes, final ClassLoader loader, final Registry registry) {
        // Stack maps are regenerated for transformed code; give the generator access to the application's class loader so it can resolve class hierarchies of app classes.
        ClassHierarchyResolver resolver = ClassHierarchyResolver.defaultResolver().orElse(ClassHierarchyResolver.ofClassLoading(loader));
        ClassFile              cf       = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver));

        ClassModel model        = cf.parse(classBytes);
        String     internalName = model.thisClass().asInternalName();
        boolean[]  changed      = { false };

        ClassTransform transform = (classBuilder, element) -> {
            if (element instanceof MethodModel method && isInstrumentable(method)) {
                int id = registry.register(internalName, method.methodName().stringValue(), method.methodType().stringValue());
                if (id >= 0) {
                    changed[0] = true;
                    classBuilder.transformMethod(method, MethodTransform.transformingCode(counting(id)));
                    return;
                }
            }
            classBuilder.with(element);
        };

        byte[] result = cf.transformClass(model, transform);
        return changed[0] ? result : null;
    }

    private static boolean isInstrumentable(final MethodModel method) { return method.code().isPresent() && !method.flags().has(AccessFlag.SYNTHETIC) && !method.flags().has(AccessFlag.BRIDGE); }

    /** Emits the counter increment before the first code element, then passes everything through. */
    private static CodeTransform counting(final int id) {
        return new CodeTransform() {
            private boolean first = true;

            @Override public void accept(final CodeBuilder builder, final CodeElement element) {
                if (first) {
                    first = false;
                    // COUNTS[id] = COUNTS[id] + 1
                    builder.getstatic(COUNTERS, "COUNTS", LONG_ARRAY).loadConstant(Integer.valueOf(id)).dup2().laload().lconst_1().ladd().lastore();
                }
                builder.with(element);
            }
        };
    }
}
