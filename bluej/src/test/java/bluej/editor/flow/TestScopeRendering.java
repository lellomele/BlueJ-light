/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-11. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import bluej.editor.base.BackgroundItem;
import bluej.editor.base.LineDisplay.LineDisplayListener;
import bluej.editor.flow.FlowEditorPane.LineStyler;
import bluej.parser.InitConfig;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestScopeRendering extends FXTest
{
    private Scene scene;

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitConfig.init();
        scene = new Scene(new Pane(), 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    @Test public void disjointScrollJumpsOnlyRecalculateExposedLines()
    {
        fx_(() -> {
            TestDisplay display = new TestDisplay(false);
            RecordingView view = new RecordingView(display);
            view.renderedLines(0, 20);
            display.from = 1000; display.to = 1020;
            view.renderedLines(1000, 1020);
            assertEquals(List.of("1000:1020"), view.ranges);
            view.ranges.clear();
            display.from = 5; display.to = 25;
            view.renderedLines(5, 25);
            assertEquals(List.of("5:25"), view.ranges);
            view.ranges.clear();
            display.from = 10; display.to = 35;
            view.renderedLines(10, 35);
            assertEquals(List.of("26:35"), view.ranges);
        });
    }

    @Test public void screenCacheDropsHiddenLinesAndPrintCacheKeepsThem()
    {
        fx_(() -> {
            for (boolean printing : new boolean[] {false, true})
            {
                TestDisplay display = new TestDisplay(printing);
                RecordingView view = new RecordingView(display);
                Map<Integer, List<?>> pending = mapField(view, "pendingScopeBackgrounds");
                pending.put(2, List.of());
                pending.put(1000, List.of());
                applyPending(view);
                Object scopes = field(view, "scopeBackgrounds");
                Map<Integer, List<?>> source = mapField(scopes, "sourceInfo");
                assertTrue(source.containsKey(2));
                assertEquals(printing, source.containsKey(1000));
                display.from = 1000;
                display.to = 1020;
                view.renderedLines(1000, 1020);
                assertEquals(printing, source.containsKey(2));
                pending.put(1000, List.of());
                applyPending(view);
                assertTrue(source.containsKey(1000));
            }
        });
    }

    @Test public void replacementsAndUndoRedoBatchScopeRefreshUntilParsed()
    {
        fx_(() -> {
            TestDisplay display = new TestDisplay(false);
            HoleDocument document = javaDocument();
            RecordingView view = new RecordingView(document, display);
            view.enableParser(true);
            view.flushReparseQueue();
            view.ranges.clear();
            DocumentUndoStack undo = new DocumentUndoStack(document);
            String original = document.getFullContent();
            int position = original.indexOf("return 42");
            document.replaceText(position, position + "return 42".length(), "return 12345");
            undo.undo();
            undo.redo();
            undo.undo();
            assertEquals(original, document.getFullContent());
            assertTrue("Decorations must not block each edit", view.ranges.isEmpty());
            assertEquals(Boolean.TRUE, field(view, "scopeRefreshPending"));
            view.flushReparseQueue();
            assertEquals(List.of("0:20"), view.ranges);
            assertEquals(Boolean.FALSE, field(view, "scopeRefreshPending"));
            assertNotNull(view.getParser());
        });
    }

    @Test public void initialLoadBatchesScopesAndUsesTheLatestViewport()
    {
        fx_(() -> {
            TestDisplay display = new TestDisplay(false);
            RecordingView view = new RecordingView(javaDocument(), display);
            view.enableParser(true);
            assertEquals(Boolean.TRUE, field(view, "scopeRefreshPending"));
            display.from = 60; display.to = 80;
            view.renderedLines(60, 80);
            assertTrue(view.ranges.isEmpty());
            view.flushReparseQueue();
            assertEquals(List.of("60:80"), view.ranges);
            assertEquals(Boolean.FALSE, field(view, "scopeRefreshPending"));
            assertNotNull(view.getParser());
        });
    }

    @Test public void fontChangesInvalidateMetricsAndPendingDecorations()
    {
        fx_(() -> {
            RecordingView view = new RecordingView(new TestDisplay(false));
            @SuppressWarnings("unchecked") List<Double> spaces = (List<Double>)field(view, "cachedSpaceSizes");
            spaces.add(8.0);
            mapField(view, "pendingScopeBackgrounds").put(2, List.of());
            view.fontSizeChanged();
            assertTrue(spaces.isEmpty());
            assertTrue(mapField(view, "pendingScopeBackgrounds").isEmpty());
        });
    }

    @Test public void scrollingDuringEditRefreshesTheLatestViewport()
    {
        fx_(() -> {
            TestDisplay display = new TestDisplay(false);
            HoleDocument document = javaDocument();
            RecordingView view = new RecordingView(document, display);
            view.enableParser(true); view.flushReparseQueue(); view.ranges.clear();
            document.replaceText(document.getFullContent().indexOf("42"), document.getFullContent().indexOf("42") + 2, "123");
            display.from = 60; display.to = 80;
            view.renderedLines(60, 80);
            assertTrue(view.ranges.isEmpty());
            view.flushReparseQueue();
            assertEquals(List.of("60:80"), view.ranges);
        });
    }

    @Test public void printingRetainsSynchronousScopeUpdates()
    {
        fx_(() -> {
            RecordingView view = new RecordingView(javaDocument(), new TestDisplay(true));
            view.enableParser(true); view.flushReparseQueue(); view.ranges.clear();
            view.document.replaceText(0, 0, "// added\n");
            assertFalse(view.ranges.isEmpty());
            assertEquals(Boolean.FALSE, field(view, "scopeRefreshPending"));
        });
    }

    @Test(timeout=5000) public void unavailableFontMetricsDoNotSpinAndCanRecover()
    {
        fx_(() -> {
            TestDisplay display = new TestDisplay(false);
            display.measuredWidth = 0;
            RecordingView view = new RecordingView(display);
            try
            {
                var edge = JavaSyntaxView.class.getDeclaredMethod("getLeftEdge", int.class); edge.setAccessible(true);
                assertEquals(java.util.OptionalInt.empty(), edge.invoke(view, 2));
                display.measuredWidth = 8;
                assertEquals(java.util.OptionalInt.of(0), edge.invoke(view, 2));
            }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }

    private static HoleDocument javaDocument()
    {
        HoleDocument document = new HoleDocument();
        document.replaceText(0, 0, "class Example {\n" + "    int answer() { return 42; }\n".repeat(100) + "}\n");
        return document;
    }

    private static Object field(Object target, String name)
    {
        try
        {
            Class<?> type = target instanceof JavaSyntaxView ? JavaSyntaxView.class : target.getClass();
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<?>> mapField(Object target, String name)
    {
        return (Map<Integer, List<?>>)field(target, name);
    }

    private static void applyPending(JavaSyntaxView view)
    {
        try
        {
            var method = JavaSyntaxView.class.getDeclaredMethod("applyPendingScopeBackgrounds");
            method.setAccessible(true);
            method.invoke(view);
        }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private class RecordingView extends JavaSyntaxView
    {
        final List<String> ranges = new ArrayList<>();
        RecordingView(TestDisplay display)
        {
            this(longDocument(), display);
        }
        RecordingView(HoleDocument document, TestDisplay display)
        {
            super(document, display, ScopeColors.dummy(), null, new ReadOnlyBooleanWrapper(true));
        }
        @Override protected void recalcScopeMarkers(int width, int first, int last, int attempt)
        {
            ranges.add(first + ":" + last);
        }
    }

    private static HoleDocument longDocument()
    {
        HoleDocument document = new HoleDocument();
        document.replaceText(0, 0, "line\n".repeat(1500));
        return document;
    }

    private class TestDisplay implements JavaSyntaxView.Display
    {
        final boolean printing;
        int from = 0, to = 20;
        double measuredWidth = 8;
        TestDisplay(boolean printing) { this.printing = printing; }
        public ReadOnlyObjectProperty<Scene> sceneProperty() { return scene.getRoot().sceneProperty(); }
        public ReadOnlyDoubleProperty widthProperty() { return scene.widthProperty(); }
        public ReadOnlyDoubleProperty heightProperty() { return scene.heightProperty(); }
        public boolean isPrinting() { return printing; }
        public boolean isLineVisible(int line) { return line >= from && line <= to; }
        public int[] getLineRangeVisible() { return new int[] {from, to}; }
        public Optional<Double> getLeftEdgeX(int character) { return Optional.of(0.0); }
        public void requestLayout() { }
        public void addLineDisplayListener(LineDisplayListener listener) { }
        public void setLineStyler(LineStyler styler) { }
        public double getTextDisplayWidth() { return 800; }
        public void applyScopeBackgrounds(Map<Integer, List<BackgroundItem>> scopes) { }
        public void repaint() { }
        public double getWidthOfText(String text) { return text.length() * measuredWidth; }
    }
}
