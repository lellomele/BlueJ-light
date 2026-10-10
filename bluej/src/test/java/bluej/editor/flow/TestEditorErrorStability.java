/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.Config;
import bluej.compiler.*;
import bluej.editor.EditorWatcher;
import bluej.editor.base.TextLine;
import bluej.editor.fixes.EditorFixesManager;
import bluej.light.EditorTools;
import bluej.parser.InitConfig;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestEditorErrorStability extends FXTest
{
    private FlowEditor editor;
    private FlowErrorManager errors;
    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage); InitConfig.init(); Config.loadFXFonts();
        EditorWatcher watcher = (EditorWatcher)Proxy.newProxyInstance(EditorWatcher.class.getClassLoader(),
            new Class<?>[]{EditorWatcher.class}, (object, method, arguments) -> {
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
        editor = new FlowEditor(window -> null, "Example", watcher, null, null, null, new ReadOnlyBooleanWrapper(true), true);
        Field field = FlowEditor.class.getDeclaredField("errorManager"); field.setAccessible(true); errors = (FlowErrorManager)field.get(editor);
        stage.setScene(new Scene(editor, 900, 600)); stage.show();
    }
    private void code(String source)
    {
        editor.getSourcePane().getDocument().replaceText(0, editor.getTextLength(), source);
        editor.enableParser(true); editor.getSourceDocument().flushReparseQueue();
    }
    private Diagnostic diagnostic(String source)
    {
        int position = source.indexOf("answer"); var document = editor.getSourcePane().getDocument();
        int line = document.getLineFromPosition(position) + 1, column = document.getColumnFromPosition(position) + 1;
        Diagnostic diagnostic = new Diagnostic(Diagnostic.ERROR, DiagnosticMessage.fromEnglish("invalid method declaration; return type required"),
            "Example.java", line, column, line, column + 6, Diagnostic.DiagnosticOrigin.JAVAC, 1);
        diagnostic.setCompilerCode("compiler.err.invalid.meth.decl.ret.type.req"); return diagnostic;
    }
    @Test public void removingTextDoesNotPaintStaleErrorsOrRunStaleCorrections()
    {
        fx_(() -> {
            String source = "class Example {\n" + "\n".repeat(12) + "  public answer() { return 42; }\n}\n";
            code(source); editor.compileStarted(1); editor.displayDiagnostic(diagnostic(source), 0, CompileType.ERROR_CHECK_ONLY);
            FlowErrorManager.ErrorDetails old = errors.getObservableErrorList().getFirst();
            assertTrue(old.isValid()); editor.getSourcePane().getDocument().replaceText(0, editor.getTextLength(), "\n");
            assertFalse(old.isValid()); assertNull(errors.getErrorAtPosition(0)); assertTrue(errors.getErrorUnderlines(0, 20).isEmpty());
            editor.getSourcePane().updateRender(false);
        });
        sleep(250);
        fx_(() -> assertTrue(errors.getObservableErrorList().isEmpty()));
    }
    @Test public void emptyLinesAndOutdatedCompilerResultsAreIgnored()
    {
        fx_(() -> {
            String source = "class Example { public answer() {} }"; code(source); editor.compileStarted(2);
            Diagnostic old = diagnostic(source); editor.getSourcePane().getDocument().replaceText(0, editor.getTextLength(), "\n\n\n");
            assertFalse(editor.displayDiagnostic(old, 0, CompileType.ERROR_CHECK_ONLY));
            errors.addErrorHighlight(0, 0, DiagnosticMessage.fromEnglish("test"), 4);
            editor.getSourcePane().updateRender(false);
        });
        sleep(200);
    }
    @Test public void repeatedUnderlineRequestsRemainBoundedAndRangesAreClipped()
    {
        fx_(() -> {
            TextLine line = new TextLine(false);
            line.setText(List.of(new TextLine.StyledSegment(List.of(), "abc")), 0, false, new SimpleStringProperty("-fx-font-size: 12px;"));
            for (int i = 0; i < 2000; i++) line.showError(1, 3);
            line.showError(-1, 2); line.showError(5, 2); line.showError(2, 200);
            try
            {
                Field field = TextLine.class.getDeclaredField("errorLocations"); field.setAccessible(true);
                List<IndexRange> ranges = (List<IndexRange>)field.get(line); assertEquals(2, ranges.size());
                assertTrue(ranges.stream().allMatch(range -> range.getStart() >= 0 && range.getEnd() <= 3));
            }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }
    @Test public void diagnosticRefreshPreservesFreshErrorsDuringPendingEditCleanup()
    {
        fx_(() -> {
            String source = "class Example { public answer() { return 42; } }"; code(source);
            editor.compileStarted(3); editor.displayDiagnostic(diagnostic(source), 0, CompileType.ERROR_CHECK_ONLY);
        });
        sleep(200);
        fx_(() -> assertEquals(1, errors.getObservableErrorList().size()));
    }
    @Test public void explanationShowsItalianImmediatelyAndDoesNotDisableTheEditor()
    {
        fx_(() -> {
            String source = "class Example { public answer() { return 42; } }"; code(source);
            editor.compileStarted(4); editor.displayDiagnostic(diagnostic(source), 0, CompileType.ERROR_CHECK_ONLY);
            EditorTools.errors(editor);
            for (Window window : List.copyOf(Window.getWindows()))
            {
                if (window.getScene() == null) continue;
                var node = window.getScene().getRoot().lookup("#light-errors-dialog");
                if (node instanceof DialogPane pane)
                {
                    TextArea text = (TextArea)pane.lookup("#light-errors-explanation");
                    assertTrue(text.getText().contains("public int answer()"));
                    assertFalse(editor.getScene().getRoot().isDisabled());
                    ((Button)pane.lookupButton(ButtonType.CLOSE)).fire(); return;
                }
            }
            fail("Error explanation window not shown");
        });
    }
    @Test public void unresolvedImportMetadataNeverBlocksTheUi()
    {
        fx_(() -> {
            EditorFixesManager manager = new EditorFixesManager(new CompletableFuture<>());
            assertTrue(manager.getJavaLangImports().isEmpty());
        });
    }
    @Test public void invalidMethodEditsReparseRepeatedlyWithoutLosingEditorNotifications()
    {
        fx_(() -> {
            String valid = "class Example { public int answer() { return 42; } }";
            String invalid = valid.replace("int answer", "answer");
            code(valid);
            for (int i = 0; i < 80; i++)
            {
                code(i % 2 == 0 ? invalid : valid);
                editor.getSourcePane().positionCaret(Math.min(30, editor.getTextLength()));
                editor.getSourcePane().updateRender(false);
            }
            assertEquals(valid, editor.getSourcePane().getDocument().getFullContent());
            assertNotNull(editor.getSourceDocument().getParser());
        });
    }
    @Test public void aStalledParserIsDiscardedAndRecoversOnTheNextEdit()
    {
        fx_(() -> {
            String source = "class Example { int value; }"; code(source);
            JavaSyntaxView syntax = (JavaSyntaxView)editor.getSourceDocument();
            bluej.parser.nodes.ParsedCUNode stalled = new bluej.parser.nodes.ParsedCUNode(null) {
                @Override public void reparse(bluej.parser.nodes.ReparseableDocument document, int nodePos, int offset, int maxParse,
                    bluej.parser.nodes.NodeStructureListener listener) { }
            };
            stalled.setSize(source.length());
            try { Field field = JavaSyntaxView.class.getDeclaredField("rootNode"); field.setAccessible(true); field.set(syntax, stalled); }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            syntax.scheduleReparse(0, source.length()); syntax.flushReparseQueue();
            assertNull(syntax.getParser());
            editor.getSourcePane().getDocument().replaceText(editor.getTextLength(), editor.getTextLength(), "\n");
            syntax.flushReparseQueue(); assertNotNull(syntax.getParser());
            assertEquals(source + "\n", editor.getSourcePane().getDocument().getFullContent());
        });
    }
    @Test public void paragraphOffsetsRemainCorrectWithoutCopyingTheWholeLineTable()
    {
        fx_(() -> {
            code("class Example {\n int value;\n}\n");
            var root = editor.getSourceDocument().getDefaultRootElement();
            var document = editor.getSourcePane().getDocument();
            assertNull(root.getElement(-1)); assertNull(root.getElement(document.getLineCount()));
            for (int line = 0; line < document.getLineCount(); line++)
            {
                assertEquals(document.getLineStart(line), root.getElement(line).getStartOffset());
                assertEquals(line + 1 == document.getLineCount() ? document.getLength() : document.getLineStart(line + 1), root.getElement(line).getEndOffset());
            }
        });
    }
}
