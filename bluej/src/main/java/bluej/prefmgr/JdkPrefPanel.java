/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GPLv2 with Classpath Exception; see LICENSE.txt. */
package bluej.prefmgr;

import bluej.Config;
import bluej.pkgmgr.Project;
import bluej.utility.Utility;
import bluej.utility.javafx.JavaFXUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public class JdkPrefPanel extends VBox implements PrefPanelListener
{
    private final Path preferences;
    private final Path applicationRoot;
    private final ComboBox<String> mode = new ComboBox<>();
    private final ListView<JdkInfo> choices = new ListView<>();
    private final Label status = new Label();
    private final Button browse = new Button(Config.getString("jdk.browse"));
    private final Button refresh = new Button(Config.getString("jdk.refresh"));
    private final Label current = new Label();
    private int scanGeneration;
    private boolean bundledAvailable;
    private JdkSelection original = JdkSelection.automatic();

    public JdkPrefPanel()
    {
        this(Config.getUserConfigDir().toPath(), Config.getBlueJLibDir().getParentFile().toPath());
    }

    public JdkPrefPanel(Path preferences, Path applicationRoot)
    {
        this.preferences = preferences;
        this.applicationRoot = applicationRoot;
        setId("light-jdk-panel");
        setSpacing(10);
        setPadding(new Insets(16));
        mode.setId("light-jdk-mode");
        mode.setMaxWidth(Double.MAX_VALUE);
        choices.setId("light-jdk-list");
        choices.setPrefHeight(260);
        choices.setCellFactory(list -> new ListCell<>() {
            @Override @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            protected void updateItem(JdkInfo item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else
                {
                    Label heading = new Label(item.vendor() + " " + item.version() + " (x64)");
                    heading.setStyle("-fx-font-weight: bold;");
                    Label path = new Label(item.home().toString());
                    path.setWrapText(true);
                    path.setMaxWidth(480);
                    setGraphic(new VBox(3, heading, path));
                }
            }
        });
        status.setWrapText(true);
        current.setWrapText(true);
        current.setMaxWidth(520);
        browse.setId("light-jdk-browse");
        refresh.setId("light-jdk-refresh");
        browse.setOnAction(event -> browse());
        refresh.setOnAction(event -> discover());
        JavaFXUtil.addChangeListenerPlatform(mode.getSelectionModel().selectedIndexProperty(), value -> updateStatus());
        JavaFXUtil.addChangeListenerPlatform(choices.getSelectionModel().selectedItemProperty(), value -> updateStatus());
        getChildren().setAll(new Label(Config.getString("jdk.current")), current,
            new Label(Config.getString("jdk.next")), mode, choices, new HBox(10, refresh, browse), status);
        VBox.setVgrow(choices, Priority.ALWAYS);
    }

    @Override public void beginEditing(Project project)
    {
        scanGeneration++;
        bundledAvailable = JdkInfo.inspect(applicationRoot.resolve("runtime")).isPresent();
        mode.getItems().setAll(Config.getString("jdk.automatic"), Config.getString("jdk.external"));
        if (bundledAvailable) mode.getItems().add(Config.getString("jdk.bundled"));
        try { original = JdkSelection.read(preferences); }
        catch (IOException | RuntimeException ex) { original = JdkSelection.automatic(); }
        mode.getSelectionModel().select(original.mode().equals("external") ? 1 : original.mode().equals("bundled") && bundledAvailable ? 2 : 0);
        current.setText(System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n" + System.getProperty("java.home"));
        choices.getItems().clear();
        discover();
    }

    private void discover()
    {
        int generation = ++scanGeneration;
        Path selected = choices.getSelectionModel().getSelectedItem() == null ? original.home() : choices.getSelectionModel().getSelectedItem().home();
        refresh.setDisable(true);
        status.setText(Config.getString("jdk.scanning"));
        Path root = applicationRoot;
        Utility.runBackground(() -> {
            List<JdkInfo> found = JdkInfo.discover(root, selected);
            Platform.runLater(() -> {
                if (generation != scanGeneration) return;
                choices.getItems().setAll(found);
                if (selected != null) found.stream().filter(info -> info.home().equals(selected)).findFirst().ifPresent(choices.getSelectionModel()::select);
                if (choices.getSelectionModel().getSelectedItem() == null && !found.isEmpty()) choices.getSelectionModel().selectFirst();
                refresh.setDisable(false);
                updateStatus();
            });
        });
    }

    private void browse()
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Config.getString("jdk.browse"));
        java.io.File folder = chooser.showDialog(getScene().getWindow());
        if (folder == null) return;
        var info = JdkInfo.inspect(folder.toPath());
        if (info.isEmpty()) { status.setText(Config.getString("jdk.invalid")); return; }
        scanGeneration++;
        refresh.setDisable(false);
        if (!choices.getItems().contains(info.get())) choices.getItems().add(info.get());
        choices.getSelectionModel().select(info.get());
        mode.getSelectionModel().select(1);
        updateStatus();
    }

    private void updateStatus()
    {
        boolean external = mode.getSelectionModel().getSelectedIndex() == 1;
        choices.setDisable(!external);
        browse.setDisable(!external);
        status.setText(external && choices.getSelectionModel().getSelectedItem() == null
            ? Config.getString("jdk.missing") : Config.getString("jdk.restart"));
    }

    public boolean saveSelection()
    {
        int selectedMode = mode.getSelectionModel().getSelectedIndex();
        JdkInfo selected = choices.getSelectionModel().getSelectedItem();
        if (selectedMode == 1 && selected == null) { status.setText(Config.getString("jdk.missing")); return false; }
        JdkSelection selection = selectedMode == 1 ? JdkSelection.external(selected.home())
            : selectedMode == 2 ? JdkSelection.bundled() : JdkSelection.automatic();
        try { selection.save(preferences); original = selection; return true; }
        catch (IOException | RuntimeException ex) { status.setText(Config.getString("jdk.saveError") + "\n" + ex.getMessage()); return false; }
    }

    @Override public void revertEditing(Project project) { scanGeneration++; }
    @Override public void commitEditing(Project project) { scanGeneration++; }
}
