/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.Config;
import bluej.editor.fixes.SuggestionList;
import bluej.editor.fixes.SuggestionList.*;
import bluej.parser.InitConfig;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.BoundingBox;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestReusableCompletion extends FXTest
{
    private Pane reference;
    private final Parent parent = new Parent();

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage); InitConfig.init(); Config.loadFXFonts();
        reference = new Pane(); stage.setScene(new Scene(reference, 800, 600)); stage.show();
    }

    private static class Parent implements SuggestionListParent
    {
        double font = 12;
        public StringExpression getFontCSS() { return new SimpleStringProperty("-fx-font-size: " + font + "px;"); }
        public double getFontSize() { return font; }
        public void setupSuggestionWindow(Stage window) { }
    }

    private static class Listener implements SuggestionListListener
    {
        int hidden, selected;
        public void suggestionListChoiceClicked(SuggestionList list, int index) { selected++; }
        public Response suggestionListKeyTyped(SuggestionList list, KeyEvent event, int index) { return Response.CONTINUE; }
        public Response suggestionListKeyPressed(SuggestionList list, KeyEvent event, int index)
        {
            if (event.getCode() == KeyCode.ENTER) { selected++; return Response.DISMISS; }
            return event.getCode() == KeyCode.ESCAPE ? Response.DISMISS : Response.CONTINUE;
        }
        public void hidden() { hidden++; }
    }

    private SuggestionList list(List<SuggestionDetails> choices, Listener listener)
    {
        return new SuggestionList(parent, choices, null, SuggestionShown.RARE, null, listener);
    }

    private void show(SuggestionList list, String prefix)
    {
        list.calculateEligible(prefix, true, false); list.updateVisual(prefix); list.highlightFirstEligible();
        list.show(reference, new BoundingBox(30, 30, 0, 20), false);
    }

    private static void key(SuggestionList list, KeyCode key)
    {
        Event.fireEvent(((Stage)field(list, "window")).getScene().getRoot(),
            new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false));
    }

    private static Object field(Object object, String name)
    {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test public void windowReuseReplacesCallbacksAndResetsSessionState()
    {
        fx_(() -> {
            Listener first = new Listener(), second = new Listener();
            SuggestionList list = list(List.of(new SuggestionDetails("alpha"), new SuggestionDetails("alpine")), first);
            Stage window = (Stage)field(list, "window"); show(list, "al");
            Object oldItem = ((List<?>)field(list, "doubleSuggestions")).getFirst();
            assertFalse(list.canReuse(reference)); key(list, KeyCode.ESCAPE);
            assertEquals(1, first.hidden); assertTrue(list.canReuse(reference));
            int id = list.getRecordingId();
            list.reset(List.of(new SuggestionDetails("beta"), new SuggestionDetails("better")), null, SuggestionShown.RARE, null, second);
            assertNotEquals("Cells must rebind even when candidate indices match", oldItem,
                ((List<?>)field(list, "doubleSuggestions")).getFirst());
            assertSame(window, field(list, "window")); assertNotEquals(id, list.getRecordingId());
            assertFalse(list.isInMiddleOfHiding()); show(list, "be"); key(list, KeyCode.ENTER);
            assertEquals(0, first.selected); assertEquals(1, first.hidden);
            assertEquals(1, second.selected); assertEquals(1, second.hidden);
        });
    }

    @Test public void unchangedEligibleItemsDoNotRebuildTheObservableList()
    {
        fx_(() -> {
            SuggestionList list = list(List.of(new SuggestionDetails("sampleOne"), new SuggestionDetails("sampleTwo")), new Listener());
            list.calculateEligible("sam", true, false); list.updateVisual("sam");
            @SuppressWarnings("unchecked") ObservableList<Object> items = (ObservableList<Object>)field(list, "showingItems");
            AtomicInteger changes = new AtomicInteger(); items.addListener((ListChangeListener<Object>)c -> changes.incrementAndGet());
            list.calculateEligible("sample", true, false); list.updateVisual("sample");
            assertEquals(0, changes.get()); assertEquals(2, list.eligibleCount());
        });
    }

    @Test public void identifierCaseAndRelatedMatchesRemainCorrect()
    {
        fx_(() -> {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                SuggestionList list = list(List.of(new SuggestionDetails("Integer"), new SuggestionDetails("inspect"),
                    new SuggestionDetails("System.out"), new SuggestionDetails("this.field")), new Listener());
                list.calculateEligible("I", false, false); assertEquals(2, list.eligibleCount());
                list.calculateEligible("field", false, false); assertEquals(1, list.eligibleCount());
                list.calculateEligible("out", true, false); list.updateVisual("out"); assertEquals(1, list.eligibleCount());
                List<?> items = (List<?>)field(list, "showingItems"); assertEquals(2, items.size());
                try { Field index = items.getFirst().getClass().getField("index"); index.setAccessible(true); assertEquals(-1, index.getInt(items.getFirst())); }
                catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            } finally { Locale.setDefault(original); }
        });
    }

    @Test public void resetRefreshesFontMetricsAndTypeColumn()
    {
        fx_(() -> {
            SuggestionList list = list(List.of(new SuggestionDetails("snippet")), new Listener());
            assertEquals(0, list.typeWidthProperty().get(), .001);
            parent.font = 24;
            list.reset(List.of(new SuggestionDetails("method", "()", "int", SuggestionShown.COMMON)), null, SuggestionShown.RARE, null, new Listener());
            assertEquals(48.0, (Double)field(list, "rowHeight"), .001);
            assertTrue(((javafx.scene.control.ListView<?>)field(list, "listBox")).getStyle().contains("24.0"));
        });
    }

    @Test public void delayedDocumentationCannotLeakIntoTheNextRequest() throws Exception
    {
        AtomicInteger oldDocs = new AtomicInteger(), newDocs = new AtomicInteger();
        SuggestionList list = fx(() -> {
            SuggestionList result = list(List.of(new SuggestionDetailsWithDeferredDoc("alpha", "()", "int", SuggestionShown.COMMON,
                () -> { oldDocs.incrementAndGet(); return new Pane(); })), new Listener());
            show(result, "a"); key(result, KeyCode.ESCAPE);
            result.reset(List.of(new SuggestionDetailsWithDeferredDoc("beta", "()", "int", SuggestionShown.COMMON,
                () -> { newDocs.incrementAndGet(); return new Pane(); })), null, SuggestionShown.RARE, null, new Listener());
            show(result, "b"); return result;
        });
        for (int i = 0; i < 40 && newDocs.get() == 0; i++) Thread.sleep(50);
        fx_(() -> { assertEquals(0, oldDocs.get()); assertEquals(1, newDocs.get()); key(list, KeyCode.ESCAPE); });
    }
}
