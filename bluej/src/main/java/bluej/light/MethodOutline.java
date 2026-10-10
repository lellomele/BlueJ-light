/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-09. GPLv2 with Classpath Exception. */
package bluej.light;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.net.URI;
import java.util.*;
import javax.tools.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public final class MethodOutline
{
    public record Entry(String owner, String signature, int offset, long line) {
        @Override public String toString() { return owner + "  " + signature + "  :" + line; }
    }
    public static List<Entry> parse(String source)
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return List.of();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Outline.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        List<Entry> found = new ArrayList<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null))
        {
            JavacTask task = (JavacTask)compiler.getTask(null, manager, diagnostic -> {}, List.of("-proc:none"), null, List.of(file));
            for (CompilationUnitTree unit : task.parse())
            {
                SourcePositions positions = Trees.instance(task).getSourcePositions();
                new TreeScanner<Void, String>() {
                    @Override public Void visitClass(ClassTree tree, String owner) {
                        String name = tree.getSimpleName().toString();
                        return super.visitClass(tree, owner == null ? name : owner + "." + name);
                    }
                    @Override public Void visitMethod(MethodTree tree, String owner) {
                        long offset = positions.getStartPosition(unit, tree);
                        if (offset >= 0 && offset <= source.length()) {
                            String name = tree.getName().toString().equals("<init>") ? owner : tree.getName().toString();
                            String parameters = tree.getParameters().stream().map(parameter -> parameter.getType().toString()).collect(java.util.stream.Collectors.joining(", "));
                            found.add(new Entry(owner == null ? "" : owner, name + "(" + parameters + ")", (int)offset, unit.getLineMap().getLineNumber(offset)));
                        }
                        return null;
                    }
                }.scan(unit, null);
            }
        }
        catch (java.io.IOException | RuntimeException ex) { return List.copyOf(found); }
        return List.copyOf(found);
    }
}
