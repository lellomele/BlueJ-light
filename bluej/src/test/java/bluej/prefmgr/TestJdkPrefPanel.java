/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GPLv2 with Classpath Exception. */
package bluej.prefmgr;

import bluej.editor.flow.FXTest;
import bluej.parser.InitConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestJdkPrefPanel extends FXTest
{
    private Path preferences, application, jdk;
    private JdkPrefPanel panel;

    @Override public void start(Stage stage) throws Exception
    {
        super.start(stage);
        InitConfig.init();
        application = Files.createTempDirectory("bluej-jdk-panel-");
        preferences = application.resolve("prefs");
        jdk = TestJdkSelection.fakeJdk(application.resolve("runtime"), "21.0.9", "amd64");
        panel = new JdkPrefPanel(preferences, application);
        stage.setScene(new Scene(panel, 580, 480));
        stage.show();
        panel.beginEditing(null);
    }

    @SuppressWarnings("unchecked")
    @Test public void bundledAndExternalModesPersistWhileCancelDoesNot() throws Exception
    {
        sleep(400);
        assertEquals(3, fx(() -> ((ComboBox<?>)panel.lookup("#light-jdk-mode")).getItems().size()).intValue());
        fx_(() -> ((ComboBox<?>)panel.lookup("#light-jdk-mode")).getSelectionModel().select(2));
        assertTrue(fx(() -> panel.saveSelection()));
        assertEquals(JdkSelection.bundled(), JdkSelection.read(preferences));
        fx_(() -> {
            ComboBox<?> modes = (ComboBox<?>)panel.lookup("#light-jdk-mode");
            ListView<JdkInfo> list = (ListView<JdkInfo>)panel.lookup("#light-jdk-list");
            list.getItems().setAll(JdkInfo.inspect(jdk).orElseThrow());
            list.getSelectionModel().selectFirst();
            modes.getSelectionModel().select(1);
        });
        assertTrue(fx(() -> panel.saveSelection()));
        assertEquals(JdkSelection.external(jdk.toRealPath()), JdkSelection.read(preferences));
        fx_(() -> {
            ((ComboBox<?>)panel.lookup("#light-jdk-mode")).getSelectionModel().select(0);
            panel.revertEditing(null);
        });
        assertEquals(JdkSelection.external(jdk.toRealPath()), JdkSelection.read(preferences));
    }

    @Test public void cannotSaveAnEmptyExternalChoice() throws Exception
    {
        sleep(400);
        fx_(() -> {
            ((ComboBox<?>)panel.lookup("#light-jdk-mode")).getSelectionModel().select(1);
            ((ListView<?>)panel.lookup("#light-jdk-list")).getItems().clear();
        });
        assertFalse(fx(() -> panel.saveSelection()));
        assertFalse(Files.exists(preferences.resolve(JdkSelection.FILE_NAME)));
    }
}
