/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.light;
import java.net.URI;
import java.util.*;
import javax.tools.*;
import org.junit.Test;
import static org.junit.Assert.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public class TestErrorExplanations
{
    @Test public void javacMissingReturnTypeHasASpecificExplanationInBothLanguages() throws Exception
    {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return "class Example { public answer() { return 42; } }"; }
        };
        try (var files = ToolProvider.getSystemJavaCompiler().getStandardFileManager(diagnostics, null, null))
        {
            assertFalse(ToolProvider.getSystemJavaCompiler().getTask(null, files, diagnostics, List.of("-proc:none"), null, List.of(source)).call());
        }
        String code = diagnostics.getDiagnostics().stream().map(javax.tools.Diagnostic::getCode)
            .filter(value -> value.contains("invalid.meth.decl.ret.type.req")).findFirst().orElseThrow();
        var italian = ErrorExplanations.explain(code, Locale.ITALIAN); var english = ErrorExplanations.explain(code, Locale.ENGLISH);
        assertTrue(italian.concept().contains("mancante")); assertTrue(italian.hints().getFirst().contains("public int answer()"));
        assertTrue(italian.hints().get(1).contains("costruttore")); assertTrue(english.hints().get(1).contains("constructor"));
    }
    @Test public void parserNoProgressIsBoundedButGrowingNodesRemainAllowed()
    {
        ParserProgressGuard guard = new ParserProgressGuard(); Object node = new Object();
        for (int i = 0; i < 128; i++) assertTrue(guard.progress(10, 100, node, 200));
        assertFalse(guard.progress(10, 100, node, 200)); guard.reset();
        for (int i = 0; i < 500; i++) assertTrue(guard.progress(10, 100, node, 200 + i));
    }
    @Test public void parserBudgetExpiresAndRestoresPreviousContext()
    {
        try (var outer = ParserBudget.start(java.util.concurrent.TimeUnit.SECONDS.toNanos(10)))
        {
            try (var expired = ParserBudget.start(0))
            {
                try { for (int i = 0; i < 1024; i++) ParserBudget.checkpoint(); fail("Budget did not expire"); }
                catch (IllegalStateException expected) { }
            }
            for (int i = 0; i < 1024; i++) ParserBudget.checkpoint();
        }
        assertFalse(ParserBudget.active());
    }
}
