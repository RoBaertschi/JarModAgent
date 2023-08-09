package xyz.wagyourtail.unimined.jarmodagent.transformer.refmap;

import net.lenni0451.classtransform.mappings.annotation.AnnotationRemap;
import org.objectweb.asm.AnnotationVisitor;
import xyz.wagyourtail.unimined.jarmodagent.JarModder;
import xyz.wagyourtail.unimined.jarmodagent.PriorityClasspath;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RemappingAnnotationVisitor extends AnnotationVisitor {
    private static final Set<String> failed = new HashSet<>();

    Map<String, String> refmap;
    boolean allowNullNameRemap;
    Class<?> annotationType;
    PriorityClasspath classpath;


    protected RemappingAnnotationVisitor(int api, AnnotationVisitor annotationVisitor, Map<String, String> refmap, String descriptor, boolean allowNullNameRemap, PriorityClasspath classpath) {
        super(api, annotationVisitor);
        this.refmap = refmap;
        this.allowNullNameRemap = allowNullNameRemap;
        this.classpath = classpath;
        try {
            if (descriptor != null) {
                if (descriptor.startsWith("L")) descriptor = descriptor.substring(1);
                if (descriptor.endsWith(";")) descriptor = descriptor.substring(0, descriptor.length() - 1);
                this.annotationType = Class.forName(descriptor.replace('/', '.'), false, classpath);
            }
        } catch (ClassNotFoundException e) {
            if (failed.add(descriptor)) {
                System.err.println("[JarModAgent] Failed to find annotation class " + descriptor);
            }
        }
        JarModder.debug("remapping annotation " + descriptor);
    }

    public boolean testName(String name) {
        if (name == null) {
            if (allowNullNameRemap) {
                return true;
            } else {
                name = "value";
            }
        }
        if (annotationType != null) {
            try {
                Method m = annotationType.getDeclaredMethod(name);
                if (m.getAnnotation(AnnotationRemap.class) == null) {
                    JarModder.debug("not remapping " + name + " because it doesn't have @AnnotationRemap");
                    return false;
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void visit(String name, Object value) {
        if (!testName(name)) {
            super.visit(name, value);
            return;
        }
        if (value instanceof String) {
            JarModder.debug("remapping \"" + name + "\" \"" + value + "\" to \"" + refmap.getOrDefault(value, (String) value) + "\"");
            super.visit(name, refmap.getOrDefault(value, (String) value));
        } else {
            super.visit(name, value);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        AnnotationVisitor av = super.visitAnnotation(name, descriptor);
        return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, descriptor, false, classpath);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        AnnotationVisitor av = super.visitArray(name);
        return av == null ? null : new RemappingAnnotationVisitor(api, av, refmap, null, true, classpath);
    }
}