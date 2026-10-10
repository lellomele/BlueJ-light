/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public class TestJavaFormatter
{
    private Path formatter() { return Path.of("lib/formatter/astyle.exe").toAbsolutePath(); }
    @Test public void expandsMethodsAndNestedSingleLineConstructs() throws Exception
    {
        String source = "class Example { int add(int a,int b){if(a>0){return a+b;}else{return b;}} }";
        var result = JavaFormatter.format(formatter(), source, source.indexOf("return"));
        assertTrue(result.text().lines().count() > 8);
        assertTrue(result.text().contains("int add(int a, int b)"));
        assertTrue(result.text().contains("return a + b;"));
        assertTrue(result.text().substring(result.caret()).startsWith("return"));
        assertEquals(result.text(), JavaFormatter.format(formatter(), result.text(), 0).text());
    }
    @Test public void keepsForHeaderSemicolonsAndStringContent() throws Exception
    {
        String source = "class X { void f(){for(int i=0;i<3;i++){System.out.println(\"a;b{c}\");} if(true) f();} }";
        String result = JavaFormatter.format(formatter(), source, 0).text();
        assertTrue(result.contains("for (int i = 0; i < 3; i++)"));
        assertTrue(result.contains("\"a;b{c}\"")); assertTrue(result.contains("if (true)\n"));
    }
    @Test public void preservesTextBlocksExactly() throws Exception
    {
        String block = "\"\"\"\n  line one\n    line two\n  \"\"\"";
        String source = "class X { String value=" + block + "; int f(){return 1;} }";
        String result = JavaFormatter.format(formatter(), source, 0).text();
        assertTrue(result.contains(block)); assertTrue(result.contains("return 1;"));
    }
    @Test public void preservesCommentsUnicodeRecordsAndLambdas() throws Exception
    {
        String source = "// caff\u00e8: ; { }\nrecord X(int n) { /* keep me */ int f(){java.util.function.IntUnaryOperator op=x->x+1;return op.applyAsInt(n);} }\n";
        String result = JavaFormatter.format(formatter(), source, 0).text();
        assertTrue(result.contains("// caff\u00e8: ; { }")); assertTrue(result.contains("/* keep me */"));
        assertTrue(result.endsWith("\n"));
    }
    @Test public void rejectsChangedTokensAndLiteralValues() throws Exception
    {
        assertThrows(java.io.IOException.class, () -> JavaFormatter.verified("class X { int n=1; }", "class X { int n=2; }", 0));
        assertThrows(java.io.IOException.class, () -> JavaFormatter.verified("class X { String s=\" a \"; }", "class X { String s=\"a\"; }", 0));
        assertThrows(java.io.IOException.class, () -> JavaFormatter.verified("class X {} // keep", "class X {} // changed", 0));
    }
}
