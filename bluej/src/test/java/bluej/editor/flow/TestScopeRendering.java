/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
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
            view.renderedLines(1000, 1020);
            assertEquals(List.of("1000:1020"), view.ranges);
            view.ranges.clear();
            view.renderedLines(5, 25);
            assertEquals(List.of("5:25"), view.ranges);
            view.ranges.clear();
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
            super(longDocument(), display, ScopeColors.dummy(), null, new ReadOnlyBooleanWrapper(true));
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
        TestDisplay(boolean printing) { this.printing = printing; }
        public ReadOnlyObjectProperty<Scene> sceneProperty() { return scene.getRoot().sceneProperty(); }
        public ReadOnlyDoubleProperty widthProperty() { return scene.widthProperty(); }
        public ReadOnlyDoubleProperty heightProperty() { return scene.heightProperty(); }
        public boolean isPrinting() { return printing; }
        public boolean isLineVisible(int line) { return line >= from && line <= to; }
        public Optional<Double> getLeftEdgeX(int character) { return Optional.of(0.0); }
        public void requestLayout() { }
        public void addLineDisplayListener(LineDisplayListener listener) { }
        public void setLineStyler(LineStyler styler) { }
        public double getTextDisplayWidth() { return 800; }
        public void applyScopeBackgrounds(Map<Integer, List<BackgroundItem>> scopes) { }
        public void repaint() { }
        public double getWidthOfText(String text) { return text.length() * 8; }
    }
}
