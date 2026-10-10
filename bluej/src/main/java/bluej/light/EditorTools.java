/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-09. GPLv2 with Classpath Exception. */
package bluej.light;

import bluej.Config;
import bluej.BlueJTheme;
import bluej.utility.Utility;
import bluej.utility.javafx.JavaFXUtil;
import bluej.editor.flow.FlowEditor;
import java.nio.file.Path;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public final class EditorTools
{
    private static final Map<FlowEditor, Dialog<Void>> errorDialogs = new java.util.WeakHashMap<>();
    public static void errors(FlowEditor editor)
    {
        Dialog<Void> previous = errorDialogs.get(editor);
        if (previous != null && previous.isShowing())
        { ((javafx.stage.Stage)previous.getDialogPane().getScene().getWindow()).toFront(); return; }
        Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(editor.getSourcePane().getScene().getWindow());
        dialog.initModality(javafx.stage.Modality.NONE);
        BlueJTheme.setWindowIconFX(dialog); dialog.setTitle(Config.getString("light.errors"));
        dialog.getDialogPane().setId("light-errors-dialog");
        ListView<bluej.compiler.Diagnostic> list = new ListView<>(editor.getLightDiagnosticsObservable());
        list.setId("light-errors-list");
        list.setPrefSize(640, 180); list.setPlaceholder(new Label(Config.getString("light.errors.empty")));
        list.setCellFactory(view -> new ListCell<>() {
            @Override @OnThread(value=Tag.FXPlatform, ignoreParent=true)
            protected void updateItem(bluej.compiler.Diagnostic diagnostic, boolean empty) {
                super.updateItem(diagnostic, empty);
                setText(empty || diagnostic == null ? null : diagnostic.getStartLine() + ": " + diagnostic.getMessage().localisedMessage());
                setWrapText(true);
            }
        });
        TextArea explanation = new TextArea(); explanation.setEditable(false); explanation.setWrapText(true); explanation.setPrefRowCount(9);
        explanation.setId("light-errors-explanation");
        Button next = new Button(Config.getString("light.hint.next")); next.setDisable(true);
        Button go = new Button(Config.getString("light.go")); go.setDisable(true);
        final ErrorExplanations.Explanation[] selected = {null}; final int[] step = {0};
        JavaFXUtil.addChangeListenerPlatform(list.getSelectionModel().selectedItemProperty(), diagnostic -> {
            if (diagnostic == null) { explanation.clear(); selected[0] = null; next.setDisable(true); go.setDisable(true); return; }
            selected[0] = ErrorExplanations.explain(diagnostic.getCompilerCode(), Config.getLocale()); step[0] = 1;
            explanation.setText(selected[0].concept() + "\n\n" + selected[0].hints().getFirst()
                + "\n\n" + diagnostic.getMessage().localisedMessage());
            next.setDisable(selected[0].hints().size() <= 1);
            long line = diagnostic.getStartLine();
            go.setDisable(line <= 0 || line > editor.getSourcePane().getDocument().getLineCount());
        });
        go.setOnAction(event -> {
            bluej.compiler.Diagnostic diagnostic = list.getSelectionModel().getSelectedItem();
            if (diagnostic == null) return;
            int line = (int)diagnostic.getStartLine();
            if (line > 0 && line <= editor.getSourcePane().getDocument().getLineCount())
                editor.navigateToOffset(editor.getSourcePane().getDocument().getLineStart(line - 1));
        });
        next.setOnAction(event -> {
            if (selected[0] == null || step[0] >= selected[0].hints().size()) return;
            explanation.appendText("\n\n" + selected[0].hints().get(step[0]++));
            next.setDisable(step[0] >= selected[0].hints().size());
        });
        dialog.getDialogPane().setContent(new VBox(8, list, explanation, new HBox(8, go, next)));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        javafx.collections.ListChangeListener<bluej.compiler.Diagnostic> selectDiagnostic = change -> {
            if (!list.getItems().isEmpty() && list.getSelectionModel().getSelectedItem() == null) list.getSelectionModel().selectFirst();
        };
        list.getItems().addListener(selectDiagnostic);
        if (!list.getItems().isEmpty()) list.getSelectionModel().selectFirst();
        dialog.setResizable(true);
        errorDialogs.put(editor, dialog);
        bluej.utility.javafx.FXPlatformRunnable stopWatching = JavaFXUtil.addChangeListenerPlatform(editor.getSourcePane().sceneProperty(), scene -> {
            if (scene == null) dialog.close();
        });
        dialog.setOnHidden(event -> {
            stopWatching.run(); errorDialogs.remove(editor);
            list.getItems().removeListener(selectDiagnostic); list.setItems(javafx.collections.FXCollections.observableArrayList());
        });
        dialog.show();
    }
    public static void methods(FlowEditor editor)
    {
        String source = editor.getSourcePane().getDocument().getFullContent();
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(editor.getSourcePane().getScene().getWindow());
        BlueJTheme.setWindowIconFX(dialog);
        dialog.setTitle(Config.getString("light.methods"));
        TextField search = new TextField(); search.setPromptText(Config.getString("light.search"));
        ListView<MethodOutline.Entry> list = new ListView<>(); list.setPrefSize(620, 370);
        list.setPlaceholder(new Label(Config.getString("light.loading")));
        ButtonType go = new ButtonType(Config.getString("light.go"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(go, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8, search, list));
        list.setId("light-method-list"); search.setId("light-method-search");
        List<MethodOutline.Entry> all = new ArrayList<>();
        JavaFXUtil.addChangeListenerPlatform(search.textProperty(), value -> {
            String query = value.toLowerCase(Locale.ROOT);
            list.getItems().setAll(all.stream().filter(entry -> entry.toString().toLowerCase(Locale.ROOT).contains(query)).toList());
            if (!list.getItems().isEmpty()) list.getSelectionModel().selectFirst();
        });
        dialog.setResultConverter(button -> {
            MethodOutline.Entry entry = list.getSelectionModel().getSelectedItem();
            if (button == go && entry != null) editor.navigateToOffset(entry.offset());
            return null;
        });
        list.setOnMouseClicked(event -> { if (event.getClickCount() == 2) ((Button)dialog.getDialogPane().lookupButton(go)).fire(); });
        Utility.runBackground(() -> {
            List<MethodOutline.Entry> entries = MethodOutline.parse(source);
            Platform.runLater(() -> {
                if (!dialog.isShowing()) return;
                all.addAll(entries); list.getItems().setAll(entries);
                list.setPlaceholder(new Label(Config.getString("light.methods.empty")));
                if (!entries.isEmpty()) list.getSelectionModel().selectFirst();
            });
        });
        dialog.showAndWait();
    }

    public static void history(FlowEditor editor, Path source, LocalHistory history)
    {
        Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(editor.getSourcePane().getScene().getWindow());
        BlueJTheme.setWindowIconFX(dialog); dialog.setTitle(Config.getString("light.history"));
        dialog.getDialogPane().setId("light-history-dialog");
        ListView<LocalHistory.Revision> list = new ListView<>(); list.setPrefWidth(220);
        list.setId("light-history-list");
        list.setCellFactory(view -> new ListCell<>() {
            @Override @OnThread(value=Tag.FXPlatform, ignoreParent=true)
            protected void updateItem(LocalHistory.Revision item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault()).format(item.time()) + "\n" + item.kind());
            }
        });
        TextArea selected = new TextArea(); selected.setEditable(false);
        String currentText = editor.getSourcePane().getDocument().getFullContent();
        TextArea current = new TextArea(currentText.substring(0, Math.min(currentText.length(), 200000))); current.setEditable(false);
        TabPane panes = new TabPane(new Tab(Config.getString("light.history.version"), selected), new Tab(Config.getString("light.history.current"), current));
        panes.getTabs().forEach(tab -> tab.setClosable(false));
        SplitPane content = new SplitPane(list, panes); content.setPrefSize(860, 520); content.setDividerPositions(.25);
        ButtonType restore = new ButtonType(Config.getString("light.restore"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(restore, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        Button restoreButton = (Button)dialog.getDialogPane().lookupButton(restore);
        restoreButton.setDisable(true);
        final String[] fullText = {null};
        final int[] generation = {0};
        JavaFXUtil.addChangeListenerPlatform(list.getSelectionModel().selectedItemProperty(), revision -> {
            fullText[0] = null; int request = ++generation[0];
            restoreButton.setDisable(true);
            if (revision == null) return;
            Utility.runBackground(() -> {
                try { String text = history.read(revision); Platform.runLater(() -> {
                    if (request != generation[0] || !dialog.isShowing()) return;
                    fullText[0] = text; selected.setText(text.length() > 200000 ? text.substring(0, 200000) + "\n..." : text);
                    restoreButton.setDisable(editor.isReadOnly());
                }); }
                catch (java.io.IOException ex) { Platform.runLater(() -> selected.setText(ex.getMessage())); }
            });
        });
        dialog.setResultConverter(button -> { if (button == restore && fullText[0] != null) editor.restoreHistoryText(fullText[0]); return null; });
        Utility.runBackground(() -> {
            try { List<LocalHistory.Revision> versions = history.list(source); Platform.runLater(() -> {
                if (!dialog.isShowing()) return; list.getItems().setAll(versions); list.setPlaceholder(new Label(Config.getString("light.history.empty")));
                if (!versions.isEmpty()) list.getSelectionModel().selectFirst();
            }); }
            catch (java.io.IOException ex) { Platform.runLater(() -> selected.setText(ex.getMessage())); }
        });
        dialog.setResizable(true); dialog.showAndWait();
    }
}
