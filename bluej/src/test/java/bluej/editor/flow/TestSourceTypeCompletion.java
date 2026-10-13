/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.Config;
import bluej.parser.InitConfig;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TestSourceTypeCompletion extends FXTest
{
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private FlowEditor editor;
    private Path source;
    @Override public void start(Stage stage) throws Exception {
        super.start(stage); InitConfig.init(); Config.loadFXFonts();
        source = folder.getRoot().toPath().resolve("Example.java");
        Files.writeString(source,"class Example {}\n");
        editor = new FlowEditor(window -> null,"Example",null,null,null,null,new ReadOnlyBooleanWrapper(true),true);
        stage.setScene(new Scene(editor,800,600)); stage.show();
        assertTrue(editor.showFile(source.toString(),StandardCharsets.UTF_8,false,null));
    }
    private List<String> names(String text) {
        var document = editor.getSourcePane().getDocument();
        document.replaceText(0,document.getLength(),text);
        var parser = editor.getParsedNode();
        return SourceTypeCompletion.candidates(editor,parser).stream().map(c -> c.getName()).toList();
    }
    @Test public void currentFileTypesIncludeUnsavedAdditionalDeclarations() throws Exception {
        fx_(() -> {
            List<String> names = names("class Example {} interface Helper {} enum Colour { RED } record Point(int x) {}\n");
            assertTrue(names.toString(),names.containsAll(List.of("Example","Helper","Colour","Point")));
            assertTrue(editor.isModified());
        });
        assertEquals("class Example {}\n",Files.readString(source));
    }
    @Test public void typeNamesRefreshOnRenameAndUndoRedo() {
        fx_(() -> {
            assertTrue(names("class Before {}\n").contains("Before"));
            Object cached = editor.completionTypeSnapshot;
            SourceTypeCompletion.candidates(editor,editor.getParsedNode());
            assertSame(cached,editor.completionTypeSnapshot);
            List<String> after = names("class After {}\n");
            assertTrue(after.contains("After")); assertFalse(after.contains("Before"));
            editor.undoManager.undo();
            List<String> undo = SourceTypeCompletion.candidates(editor,editor.getParsedNode()).stream().map(c -> c.getName()).toList();
            assertTrue(undo.contains("Before")); assertFalse(undo.contains("After"));
            editor.undoManager.redo();
            assertTrue(SourceTypeCompletion.candidates(editor,editor.getParsedNode()).stream().anyMatch(c -> c.getName().equals("After")));
        });
    }
    @Test public void incompleteCodeKeepsKnownTypesWithoutCrashing() {
        fx_(() -> {
            for(String text : List.of("class Example {", "class Example { void m( ", "class Example { void m() { new ",
                "class Example { int value = ;", "class ", "package ", "/* class Fake {} */ class Example {")) {
                List<String> names = names(text);
                assertTrue(text+" "+names,names.contains("Example"));
                assertEquals(names.size(),names.stream().distinct().count());
                assertFalse(names.contains("Fake"));
            }
        });
    }
    @Test public void headerScanUsesTokensNotCommentsOrClassLiterals() throws Exception {
        Path file = folder.getRoot().toPath().resolve("Main.java");
        Files.writeString(file,"/* public class Main {} */ @Ann(type=Other.class) public final class Main {}\n");
        assertTrue(ProjectTypeIndex.publicHeader(file,"Main",StandardCharsets.UTF_8));
        Files.writeString(file,"/* public class Main {} */ class Main { String text=\"public class Main\"; }\n");
        assertFalse(ProjectTypeIndex.publicHeader(file,"Main",StandardCharsets.UTF_8));
        Files.writeString(file,"class Helper {} public record Main(int x) {}\n");
        assertTrue(ProjectTypeIndex.publicHeader(file,"Main",StandardCharsets.UTF_8));
    }
}
