/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.editor.flow;

import bluej.Config;
import bluej.prefmgr.PrefMgr;
import bluej.utility.javafx.JavaFXUtil;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@OnThread(Tag.FXPlatform)
public final class SnippetBrowserDialog extends Dialog<SnippetCompletion>
{
    private final List<SnippetCompletion> snippets;
    private final TextField search = new TextField();
    private final ComboBox<String> context = new ComboBox<>();
    private final ListView<SnippetCompletion> list = new ListView<>();
    private final TextArea preview = new TextArea();
    private final Label description = new Label();
    private final Label availability = new Label();
    private final Label count = new Label();
    private final Boolean inMethod;
    private final boolean insertionAllowed;
    private final Button insert;

    public SnippetBrowserDialog(Window owner, SnippetCatalog catalog, Boolean inMethod,
        boolean insertionAllowed, boolean help)
    {
        this.inMethod = inMethod;
        this.insertionAllowed = insertionAllowed;
        snippets = catalog.allCompletions().stream().sorted(Comparator
            .comparing((SnippetCompletion snippet) -> !compatible(snippet))
            .thenComparing(SnippetCompletion::getName)).toList();
        if (owner != null) initOwner(owner);
        setTitle(Config.getString("light.snippets.title"));
        setResizable(true);
        DialogPane pane = getDialogPane();
        pane.setId("light-snippet-browser");
        pane.getStyleClass().add("snippet-browser");
        pane.getStylesheets().add(Config.getBlueJLibDir().toPath().resolve("stylesheets/snippet-browser.css").toUri().toString());
        ButtonType insertType = new ButtonType(Config.getString("light.snippets.insert"), ButtonBar.ButtonData.OK_DONE);
        if (!help) pane.getButtonTypes().add(insertType);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        insert = help ? null : (Button)pane.lookupButton(insertType);
        if (insert != null) insert.setDisable(true);
        setResultConverter(button -> button == insertType ? list.getSelectionModel().getSelectedItem() : null);

        search.setId("snippet-search");
        search.setPromptText(Config.getString("light.snippets.search"));
        HBox.setHgrow(search, Priority.ALWAYS);
        context.getItems().addAll(Config.getString("light.snippets.all"), Config.getString("light.snippets.compatible"),
            Config.getString("light.snippets.method"), Config.getString("light.snippets.class"));
        context.getSelectionModel().selectFirst();
        if (inMethod == null) context.getItems().remove(1);
        HBox filters = new HBox(10, search, context);
        filters.getStyleClass().add("snippet-filters");

        list.setId("snippet-list");
        list.setFixedCellSize(58);
        list.setMinWidth(190);
        list.setCellFactory(view -> new ListCell<>() {
            @Override @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            protected void updateItem(SnippetCompletion snippet, boolean empty) {
                super.updateItem(snippet, empty);
                setText(null);
                if (empty || snippet == null) { setGraphic(null); return; }
                Label name = new Label(snippet.getName());
                name.getStyleClass().add("snippet-name");
                Label detail = new Label(snippet.getDescription());
                detail.setMaxWidth(Double.MAX_VALUE);
                detail.getStyleClass().add("snippet-description");
                VBox row = new VBox(3, name, detail);
                row.setMaxWidth(Double.MAX_VALUE);
                setGraphic(row);
                setOpacity(compatible(snippet) ? 1 : 0.65);
            }
        });
        list.setPlaceholder(new Label(Config.getString("light.snippets.empty")));
        preview.setId("snippet-preview");
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.getStyleClass().add("snippet-code");
        preview.setStyle(PrefMgr.getEditorFontCSS(PrefMgr.FontCSS.EDITOR_SIZE_AND_FAMILY).get());
        description.setWrapText(true);
        description.setMinHeight(Region.USE_PREF_SIZE);
        description.getStyleClass().add("snippet-preview-title");
        availability.setWrapText(true);
        availability.setMinHeight(Region.USE_PREF_SIZE);
        availability.getStyleClass().add("snippet-availability");
        VBox details = new VBox(10, description, availability, preview);
        details.setMinWidth(250);
        VBox.setVgrow(preview, Priority.ALWAYS);
        details.getStyleClass().add("snippet-details");
        SplitPane split = new SplitPane(list, details);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.4);
        VBox body = new VBox(10, filters, split, count);
        if (help) {
            Label usage = new Label(Config.getString("light.snippets.help"));
            usage.setWrapText(true);
            usage.setMinHeight(Region.USE_PREF_SIZE);
            usage.setMaxWidth(Double.MAX_VALUE);
            usage.getStyleClass().add("snippet-help");
            body.getChildren().add(0, usage);
        }
        VBox.setVgrow(split, Priority.ALWAYS);
        body.setPrefSize(800, 480);
        pane.setContent(body);
        JavaFXUtil.addChangeListenerPlatform(search.textProperty(), value -> refresh());
        JavaFXUtil.addChangeListenerPlatform(context.valueProperty(), value -> refresh());
        JavaFXUtil.addChangeListenerPlatform(list.getSelectionModel().selectedItemProperty(), this::showPreview);
        list.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && insert != null && !insert.isDisabled()) insert.fire(); });
        pane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && insert != null) {
                if (!insert.isDisabled()) insert.fire();
                event.consume();
            }
            else if (search.isFocused() && (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP
                    || event.getCode() == KeyCode.PAGE_DOWN || event.getCode() == KeyCode.PAGE_UP)) {
                int step = event.getCode() == KeyCode.PAGE_DOWN ? 8 : event.getCode() == KeyCode.PAGE_UP ? -8
                    : event.getCode() == KeyCode.DOWN ? 1 : -1;
                int index = Math.max(0, Math.min(list.getItems().size() - 1, list.getSelectionModel().getSelectedIndex() + step));
                list.getSelectionModel().select(index);
                list.scrollTo(index);
                event.consume();
            }
        });
        refresh();
        setOnShown(event -> {
            if (getDialogPane().getScene().getWindow() instanceof javafx.stage.Stage stage) {
                stage.setMinWidth(620);
                stage.setMinHeight(430);
            }
            Platform.runLater(search::requestFocus);
        });
    }

    private boolean compatible(SnippetCompletion snippet)
    {
        return inMethod == null || snippet.getContext().equals("any")
            || snippet.getContext().equals(inMethod ? "method" : "class");
    }

    private void refresh()
    {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        String filter = context.getValue();
        SnippetCompletion selected = list.getSelectionModel().getSelectedItem();
        list.getItems().setAll(snippets.stream().filter(snippet -> {
            boolean type = filter.equals(Config.getString("light.snippets.all"))
                || (filter.equals(Config.getString("light.snippets.compatible")) ? compatible(snippet)
                    : snippet.getContext().equals("any")
                        || snippet.getContext().equals(filter.equals(Config.getString("light.snippets.method")) ? "method" : "class"));
            String text = (snippet.getName() + " " + snippet.getDescription() + " "
                + snippet.getTemplate().expand("", "    ").text()).toLowerCase(Locale.ROOT);
            return type && java.util.Arrays.stream(query.split("\\s+")).allMatch(text::contains);
        }).toList());
        if (selected != null && list.getItems().contains(selected)) list.getSelectionModel().select(selected);
        else list.getSelectionModel().selectFirst();
        if (list.getItems().isEmpty()) showPreview(null);
        count.setText(list.getItems().size() + " / " + snippets.size());
    }

    private void showPreview(SnippetCompletion snippet)
    {
        if (insert != null) insert.setDisable(snippet == null || !insertionAllowed || !compatible(snippet));
        description.setText(snippet == null ? "" : snippet.getDescription());
        preview.setText(snippet == null ? "" : snippet.getTemplate().expand("", "    ").text());
        availability.setText(snippet == null ? "" : !insertionAllowed && inMethod != null
            ? Config.getString("light.snippets.noInsertion") : Config.getString("light.snippets." + snippet.getContext()));
    }
}
