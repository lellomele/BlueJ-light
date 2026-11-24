/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.editor.base.BackgroundItem;
import bluej.editor.base.LineDisplay.LineDisplayListener;
import bluej.parser.InitConfig;
import bluej.parser.nodes.*;
import bluej.utility.javafx.FXPlatformRunnable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.property.*;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestParserCallbacks extends FXTest
{
    private static final String SOURCE = "class Example { int value() { return 42; } }\n";
    private Scene scene;
    private HoleDocument document;
    private JavaSyntaxView syntax;
    private TestDisplay display;

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitConfig.init();
        scene = new Scene(new Pane(), 800, 600);
        stage.setScene(scene); stage.show();
    }

    private void initialise(boolean withDisplay)
    {
        document = new HoleDocument(); document.replaceText(0, 0, SOURCE);
        display = new TestDisplay();
        syntax = new JavaSyntaxView(document, withDisplay ? display : null, ScopeColors.dummy(), null, new ReadOnlyBooleanWrapper(true));
        syntax.enableParser(true); syntax.flushReparseQueue();
    }

    @Test public void enclosingAndBridgingRequestsKeepBothTails()
    {
        fx_(() -> {
            initialise(true);
            syntax.scheduleReparse(20, 10);
            syntax.scheduleReparse(10, 30);
            syntax.markSectionParsed(10, 20);
            assertFalse("The enclosing request still requires [30,40)", syntax.isParsedForCompletion());
            syntax.markSectionParsed(30, 10);
            assertTrue(syntax.isParsedForCompletion());

            syntax.scheduleReparse(5, 8);    // [5,13)
            syntax.scheduleReparse(22, 17);  // [22,39)
            syntax.scheduleReparse(10, 15);  // [10,25) bridges both
            syntax.markSectionParsed(5, 29);
            assertFalse("The original right-hand tail [34,39) is still pending", syntax.isParsedForCompletion());
            syntax.markSectionParsed(34, 5);
            assertTrue(syntax.isParsedForCompletion());
        });
    }

    @Test public void boundaryContinuationsSurviveInEitherInsertionOrder()
    {
        fx_(() -> {
            initialise(true);
            for (boolean markerFirst : List.of(true, false)) {
                if (markerFirst) syntax.scheduleReparse(25, 0);
                syntax.scheduleReparse(5, 20);
                if (!markerFirst) syntax.scheduleReparse(25, 0);
                syntax.scheduleReparse(5, 0);
                syntax.scheduleReparse(9, 0);
                syntax.scheduleReparse(25, 0);
                syntax.markSectionParsed(5, 20);
                assertFalse("Parsing up to 25 must leave the continuation at 25", syntax.isParsedForCompletion());
                syntax.markSectionParsed(25, 0);
                assertTrue(syntax.isParsedForCompletion());
            }

            syntax.scheduleReparse(32, 0);
            syntax.scheduleReparse(4, 8);
            syntax.scheduleReparse(18, 6);
            syntax.markSectionParsed(4, 20);
            assertFalse("A disjoint continuation must survive", syntax.isParsedForCompletion());
            syntax.markSectionParsed(32, 0);
            assertTrue(syntax.isParsedForCompletion());

            syntax.scheduleReparse(12, 6);
            syntax.scheduleReparse(12, -8); // A rewound token ends before the requested offset.
            syntax.markSectionParsed(12, 6);
            assertTrue(syntax.isParsedForCompletion());
        });
    }

    @Test public void rangeUnionAndPartialAcknowledgementsMatchCharacterCoverage()
    {
        fx_(() -> {
            int[][] ranges = {{8, 8}, {26, 6}, {0, 3}, {12, 16}, {3, 5}, {36, 5}, {4, 27}, {5, 1}};
            int[][] parsed = {{10, 6}, {2, 3}, {30, 7}, {0, 41}};
            for (boolean reverse : List.of(false, true)) {
                initialise(true);
                BitSet expected = new BitSet();
                for (int i = 0; i < ranges.length; i++) {
                    int[] range = ranges[reverse ? ranges.length - 1 - i : i];
                    syntax.scheduleReparse(range[0], range[1]);
                    expected.set(range[0], range[0] + range[1]);
                    assertQueuedRanges(expected);
                }
                for (int[] range : parsed) {
                    syntax.markSectionParsed(range[0], range[1]);
                    expected.clear(range[0], range[0] + range[1]);
                    assertQueuedRanges(expected);
                }
            }
        });
    }

    private void assertQueuedRanges(BitSet expected)
    {
        try {
            Field field = JavaSyntaxView.class.getDeclaredField("reparseRecordTree");
            field.setAccessible(true);
            NodeTree<?> records = (NodeTree<?>)field.get(syntax);
            BitSet actual = new BitSet();
            int previousEnd = 0;
            for (var it = records.iterator(0); it.hasNext(); ) {
                var record = it.next();
                assertTrue(record.getPosition() >= previousEnd);
                assertTrue("Unexpected point in character coverage", record.getSize() > 0);
                actual.set(record.getPosition(), record.getEnd());
                previousEnd = record.getEnd();
            }
            assertEquals(expected, actual);
        }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Test public void trailingWhitespaceAndCommentsConcludeEndOfFileContinuations()
    {
        fx_(() -> {
            initialise(true);
            for (String input : List.of("class A {}\n", "class A {} // last", "class A {} // last\n")) {
                document.replaceText(0, document.getLength(), input);
                syntax.flushReparseQueue();
                assertTrue(syntax.isParsedForCompletion());
                syntax.scheduleReparse(input.length(), 0);
                syntax.flushReparseQueue();
                assertTrue(input, syntax.isParsedForCompletion());
            }
        });
    }

    @Test public void renewingActivePointBeforeMarkingItsLeftRangeMakesASecondPass()
    {
        fx_(() -> {
            initialise(true);
            AtomicInteger passes = new AtomicInteger();
            ParsedCUNode root = new ParsedCUNode(null) {
                @Override public void reparse(ReparseableDocument doc, int nodePos, int offset, int maxParse, NodeStructureListener listener) {
                    if (passes.incrementAndGet() == 1) doc.scheduleReparse(offset, 0);
                    doc.markSectionParsed(offset - 1, 1);
                }
            };
            root.setSize(document.getLength());
            try {
                Field field = JavaSyntaxView.class.getDeclaredField("rootNode");
                field.setAccessible(true);
                field.set(syntax, root);
            }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            syntax.scheduleReparse(document.getLength(), 0);
            syntax.flushReparseQueue();
            assertEquals(2, passes.get());
            assertTrue(syntax.isParsedForCompletion());
        });
    }

    private void stall()
    {
        ParsedCUNode stalled = new ParsedCUNode(null) {
            @Override public void reparse(ReparseableDocument document, int nodePos, int offset, int maxParse, NodeStructureListener listener) { }
        };
        stalled.setSize(document.getLength());
        try { Field root = JavaSyntaxView.class.getDeclaredField("rootNode"); root.setAccessible(true); root.set(syntax, stalled); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        syntax.scheduleReparse(0, document.getLength());
    }

    private FXPlatformRunnable runner()
    {
        try { Field field = JavaSyntaxView.class.getDeclaredField("reparseRunner"); field.setAccessible(true); return (FXPlatformRunnable)field.get(syntax); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    @Test public void suspensionConcludesAllWaitingRequestsExactlyOnce()
    {
        fx_(() -> {
            initialise(true); stall();
            AtomicInteger first = new AtomicInteger(), second = new AtomicInteger(), disabled = new AtomicInteger();
            syntax.whenParsed(() -> { first.incrementAndGet(); if (syntax.getParser() == null) disabled.incrementAndGet(); });
            syntax.whenParsed(second::incrementAndGet);
            assertEquals(0, first.get());
            FXPlatformRunnable oldRunner = runner();
            syntax.flushReparseQueue();
            assertNull(syntax.getParser()); assertEquals(1, first.get()); assertEquals(1, second.get()); assertEquals(1, disabled.get());
            if (oldRunner != null) oldRunner.run();
            syntax.flushReparseQueue();
            assertEquals(1, first.get()); assertEquals(1, second.get()); assertEquals(SOURCE, document.getFullContent());
        });
    }

    @Test public void failingListenerDoesNotLoseLaterRequestsOrDisableAHealthyParser()
    {
        fx_(() -> {
            initialise(true); syntax.scheduleReparse(0, document.getLength());
            AtomicInteger first = new AtomicInteger(), second = new AtomicInteger(), fresh = new AtomicInteger();
            syntax.whenParsed(() -> { first.incrementAndGet(); throw new IllegalStateException("Injected callback failure"); });
            syntax.whenParsed(second::incrementAndGet);
            syntax.flushReparseQueue(); syntax.whenParsed(fresh::incrementAndGet);
            assertEquals(1, first.get()); assertEquals(1, second.get()); assertEquals(1, fresh.get());
            assertNotNull(syntax.getParser()); assertTrue(syntax.isParsedForCompletion());
            FXPlatformRunnable pending = runner(); if (pending != null) pending.run();
            assertEquals(1, first.get()); assertEquals(1, second.get());
        });
    }

    @Test public void suspensionStillConcludesRequestsWhenAListenerAndDisplayFail()
    {
        fx_(() -> {
            initialise(true); stall();
            AtomicInteger delivered = new AtomicInteger();
            syntax.whenParsed(() -> { throw new IllegalStateException("Injected failed suspension listener"); });
            syntax.whenParsed(delivered::incrementAndGet);
            display.failRefresh = true;
            syntax.flushReparseQueue();
            assertEquals(1, delivered.get()); assertNull(syntax.getParser()); assertEquals(SOURCE, document.getFullContent());
        });
    }

    @Test public void oldRunnerCannotCancelTheParserStartedByASuspensionListener()
    {
        fx_(() -> {
            initialise(true); stall();
            AtomicInteger suspended = new AtomicInteger(), recovered = new AtomicInteger();
            syntax.whenParsed(() -> {
                suspended.incrementAndGet();
                document.replaceText(document.getLength(), document.getLength(), "// recovered\n");
                syntax.whenParsed(recovered::incrementAndGet);
            });
            FXPlatformRunnable old = runner(); assertNotNull(old);
            // The progress guard suspends the stalled parser within this actual runner.
            for (int i = 0; i < 100 && suspended.get() == 0; i++) old.run();
            assertEquals(1, suspended.get());
            assertNotNull(syntax.getParser()); assertNotNull(runner()); assertNotSame(old, runner());
            syntax.flushReparseQueue();
            assertEquals(1, recovered.get()); assertTrue(syntax.isParsedForCompletion());
            old.run(); assertEquals(1, suspended.get()); assertEquals(1, recovered.get());
            assertEquals(SOURCE + "// recovered\n", document.getFullContent());
        });
    }

    @Test public void synchronousParsingWithoutADisplayAlsoConcludesQueuedRequests()
    {
        fx_(() -> {
            initialise(false); syntax.scheduleReparse(0, document.getLength());
            AtomicInteger delivered = new AtomicInteger();
            syntax.whenParsed(delivered::incrementAndGet);
            assertEquals(1, delivered.get()); assertTrue(syntax.isParsedForCompletion());
            syntax.flushReparseQueue(); assertEquals(1, delivered.get());
        });
    }

    @Test public void displayFailureDoesNotDropCompletionOfAHealthyParse()
    {
        fx_(() -> {
            initialise(true); syntax.scheduleReparse(0, document.getLength());
            AtomicInteger delivered = new AtomicInteger(); syntax.whenParsed(delivered::incrementAndGet);
            display.failRefresh = true; syntax.flushReparseQueue();
            assertEquals(1, delivered.get()); assertNotNull(syntax.getParser());
            display.failRefresh = false; syntax.whenParsed(delivered::incrementAndGet);
            assertEquals(2, delivered.get()); assertEquals(SOURCE, document.getFullContent());
        });
    }

    @Test public void rapidUndoRedoAfterRecoveryPreservesTextAndRequestDelivery()
    {
        fx_(() -> {
            initialise(true); stall(); AtomicInteger delivered = new AtomicInteger();
            syntax.whenParsed(delivered::incrementAndGet); syntax.flushReparseQueue();
            DocumentUndoStack undo = new DocumentUndoStack(document);
            String changed = SOURCE + "// resumed\n";
            document.replaceText(document.getLength(), document.getLength(), "// resumed\n");
            for (int i = 0; i < 40; i++) {
                syntax.whenParsed(delivered::incrementAndGet); syntax.flushReparseQueue();
                undo.undo(); assertEquals(SOURCE, document.getFullContent());
                syntax.whenParsed(delivered::incrementAndGet); syntax.flushReparseQueue();
                undo.redo(); assertEquals(changed, document.getFullContent());
            }
            syntax.flushReparseQueue(); assertEquals(81, delivered.get()); assertTrue(syntax.isParsedForCompletion());
        });
    }

    @Test public void unavailableParserStillClassifiesCodeAndExcludesCommentsAndLiterals()
    {
        fx_(() -> {
            Map<String, Boolean> cases = new LinkedHashMap<>();
            cases.put("class Example { void work() { re| } }", true);
            cases.put("// comment|", false);
            cases.put("/* unfinished|", false);
            cases.put("/* comment| */ class Example {}", false);
            cases.put("// comment\nclass Example { re| }", true);
            cases.put("class Example { String value = \"re|\"; }", false);
            cases.put("class Example { String value = \"unfinished|", false);
            cases.put("class Example { char value = 'r|'; }", false);
            cases.put("class Example { int value = 123|; }", false);
            cases.put("class Example { String value = \"\"\"\nre|\n\"\"\"; }", false);
            cases.put("class Example { String value = \"\"\"\ntext\n\"\"\"; void work() { re| } }", true);
            cases.put("class Example { int α|; }", true);
            cases.put("", true);
            for (var test : cases.entrySet()) {
                int position = test.getKey().indexOf('|');
                document = new HoleDocument(); document.replaceText(0, 0, test.getKey().replace("|", ""));
                syntax = new JavaSyntaxView(document, null, ScopeColors.dummy(), null, new ReadOnlyBooleanWrapper(true));
                assertNull(syntax.getParser());
                assertEquals(test.getKey(), test.getValue(), syntax.isCodePosition(Math.max(0, position)));
            }
        });
    }

    @Test public void unavailableParserDeclinesOversizedUnknownContextWithoutReadingIt()
    {
        fx_(() -> {
            document = new HoleDocument() {
                @Override public java.io.Reader makeReader(int start, int end) {
                    throw new AssertionError("Unbounded fallback must not scan a large source prefix");
                }
            };
            document.replaceText(0, 0, " ".repeat(200000));
            syntax = new JavaSyntaxView(document, null, ScopeColors.dummy(), null, new ReadOnlyBooleanWrapper(true));
            assertFalse(syntax.isCodePosition(199999));
            assertFalse(syntax.isCodePosition(-1));
            assertFalse(syntax.isCodePosition(200001));
        });
    }

    private class TestDisplay implements JavaSyntaxView.Display
    {
        boolean failRefresh;
        public ReadOnlyObjectProperty<Scene> sceneProperty() { return scene.getRoot().sceneProperty(); }
        public ReadOnlyDoubleProperty widthProperty() { return scene.widthProperty(); }
        public ReadOnlyDoubleProperty heightProperty() { return scene.heightProperty(); }
        public void requestLayout() { scene.getRoot().requestLayout(); }
        public boolean isLineVisible(int line) { return true; }
        public int[] getLineRangeVisible() { return new int[]{0, document.getLineCount() - 1}; }
        public Optional<Double> getLeftEdgeX(int character) { return Optional.of(0.0); }
        public void addLineDisplayListener(LineDisplayListener listener) { }
        public void setLineStyler(FlowEditorPane.LineStyler styler) { }
        public double getTextDisplayWidth() { return 800; }
        public void applyScopeBackgrounds(Map<Integer, List<BackgroundItem>> scopes) { if (failRefresh) throw new IllegalStateException("Injected scope refresh failure"); }
        public void repaint() { if (failRefresh) throw new IllegalStateException("Injected repaint failure"); }
        public double getWidthOfText(String text) { return text.length() * 8; }
    }
}
