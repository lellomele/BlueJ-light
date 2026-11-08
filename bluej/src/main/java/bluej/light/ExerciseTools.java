/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.light;

import bluej.BlueJTheme;
import bluej.Config;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.utility.Utility;
import bluej.utility.javafx.JavaFXUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public final class ExerciseTools
{
    private static final Map<PkgMgrFrame, CompletableFuture<?>> opening = new IdentityHashMap<>();
    @OnThread(Tag.Any) private record ViewData(ExercisePack pack, String libraries) { }

    /** Coalesce repeated opening while storage is pending or the same dialog remains open. */
    private static <T> void prepare(PkgMgrFrame frame, Project sourceProject, SourceSaveService.IOOperation<T> read,
        bluej.utility.javafx.FXPlatformConsumer<T> show)
    {
        if (opening.containsKey(frame)) return;
        CompletableFuture<T> request = sourceProject.getSourceSaveService().submit(read);opening.put(frame,request);
        request.whenComplete((prepared, failure) -> JavaFXUtil.runNowOrLater(() -> {
            try
            {
                if (frame.getProject() != sourceProject || sourceProject.isClosing()) return;
                if (failure != null) error(frame.getWindow(),new IOException(failure.getLocalizedMessage(),failure));
                else show.accept(prepared);
            }
            catch (RuntimeException | LinkageError | AssertionError unavailable) { bluej.utility.Debug.reportError("Exercise dialog failed",unavailable); }
            finally { opening.remove(frame,request); }
        }));
    }
    public static Menu menu(PkgMgrFrame frame)
    {
        Menu menu = new Menu(Config.getString("light.exercises"));
        MenuItem open = new MenuItem(Config.getString("light.exercise.open")); open.setOnAction(e -> importPack(frame));
        MenuItem view = new MenuItem(Config.getString("light.exercise.view")); view.setOnAction(e -> view(frame));
        MenuItem create = new MenuItem(Config.getString("light.exercise.create")); create.setOnAction(e -> create(frame));
        menu.getItems().setAll(open, view, create);
        menu.setOnShowing(e -> { view.setDisable(frame.getProject() == null); create.setDisable(frame.getProject() == null); });
        return menu;
    }
    private static FileChooser zipChooser(String title)
    {
        FileChooser chooser = new FileChooser(); chooser.setTitle(Config.getString(title));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip")); return chooser;
    }
    private static void error(Window owner, Exception ex)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK); alert.initOwner(owner);
        BlueJTheme.setWindowIconFX(alert); alert.initModality(Modality.WINDOW_MODAL);alert.setHeaderText(Config.getString("light.operation.failed")); alert.show();
    }
    private static void importPack(PkgMgrFrame frame)
    {
        File archive = zipChooser("light.exercise.open").showOpenDialog(frame.getWindow()); if (archive == null) return;
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle(Config.getString("light.exercise.destination"));
        File parent = chooser.showDialog(frame.getWindow()); if (parent == null) return;
        TextInputDialog name = new TextInputDialog("Exercise"); name.initOwner(frame.getWindow());
        name.setTitle(Config.getString("light.exercise.folder")); name.setHeaderText(Config.getString("light.exercise.folder"));
        String folder = name.showAndWait().orElse(""); if (folder.isBlank()) return;
        Utility.runBackground(() -> {
            try { Path project = ExercisePack.importPack(archive.toPath(), parent.toPath(), folder); JavaFXUtil.runNowOrLater(() -> frame.openExerciseProject(project.toString())); }
            catch (IOException ex) { JavaFXUtil.runNowOrLater(() -> error(frame.getWindow(), ex)); }
        });
    }
    private static TextArea area(String value, int rows)
    { TextArea area = new TextArea(value); area.setWrapText(true); area.setPrefRowCount(rows); return area; }
    private static void create(PkgMgrFrame frame)
    {
        if (frame.getProject() == null) return;
        Project sourceProject = frame.getProject();Path path = sourceProject.getProjectDir().toPath();
        prepare(frame,sourceProject,() -> Files.isRegularFile(path.resolve(ExercisePack.MANIFEST)) ? ExercisePack.load(path) : null,
            pack -> showCreate(frame,sourceProject,path,pack));
    }

    private static void showCreate(PkgMgrFrame frame, Project sourceProject, Path project, ExercisePack pack)
    {
        TextField titleEn = new TextField(), titleIt = new TextField(), tests = new TextField();
        TextArea instructionsEn = area("", 5), instructionsIt = area("", 5), hintsEn = area("", 3), hintsIt = area("", 3);
        if (pack != null)
        {
                titleEn.setText(pack.text("title", Locale.ENGLISH)); titleIt.setText(pack.text("title", Locale.ITALIAN));
                instructionsEn.setText(pack.text("instructions", Locale.ENGLISH)); instructionsIt.setText(pack.text("instructions", Locale.ITALIAN));
                hintsEn.setText(String.join("\n", pack.hints(Locale.ENGLISH))); hintsIt.setText(String.join("\n", pack.hints(Locale.ITALIAN)));
                tests.setText(String.join(", ", pack.tests()));
        }
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.initOwner(frame.getWindow());dialog.initModality(Modality.WINDOW_MODAL); BlueJTheme.setWindowIconFX(dialog);
        dialog.setTitle(Config.getString("light.exercise.create")); dialog.setResizable(true);
        TabPane languages = new TabPane(new Tab("English", new VBox(8, new Label(Config.getString("light.exercise.title")), titleEn,
            new Label(Config.getString("light.exercise.instructions")), instructionsEn, new Label(Config.getString("light.exercise.hints")), hintsEn)),
            new Tab("Italiano", new VBox(8, new Label(Config.getString("light.exercise.title")), titleIt,
            new Label(Config.getString("light.exercise.instructions")), instructionsIt, new Label(Config.getString("light.exercise.hints")), hintsIt)));
        languages.getTabs().forEach(tab -> tab.setClosable(false));
        VBox content = new VBox(10, languages, new Label(Config.getString("light.exercise.tests")), tests); content.setPrefWidth(680);
        dialog.getDialogPane().setContent(content);
        ButtonType export = new ButtonType(Config.getString("light.exercise.export"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(export, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != export) return;
        File archive = zipChooser("light.exercise.export").showSaveDialog(frame.getWindow()); if (archive == null) return;
        if (frame.getProject() != sourceProject || sourceProject.isClosing()) return;
        Properties values = new Properties(); values.setProperty("format", "1"); values.setProperty("tests", tests.getText());
        values.setProperty("title.en", titleEn.getText()); values.setProperty("title.it", titleIt.getText());
        values.setProperty("instructions.en", instructionsEn.getText()); values.setProperty("instructions.it", instructionsIt.getText());
        putHints(values, "en", hintsEn.getText()); putHints(values, "it", hintsIt.getText());
        Path destination = archive.toPath();
        sourceProject.saveAllAsync().whenComplete((saved, failure) -> JavaFXUtil.runNowOrLater(() -> {
            if (frame.getProject() != sourceProject || sourceProject.isClosing()) return;
            if (failure != null) { error(frame.getWindow(),new IOException(failure.getLocalizedMessage(),failure));return; }
            sourceProject.getSourceSaveService().submit(() -> {
                ExercisePack exported = new ExercisePack(values);exported.export(project,destination);exported.save(project);return null;
            }).whenComplete((exported, exportFailure) -> JavaFXUtil.runNowOrLater(() -> {
                if (exportFailure != null && frame.getProject() == sourceProject)
                    error(frame.getWindow(),new IOException(exportFailure.getLocalizedMessage(),exportFailure));
            }));
        }));
    }
    private static void putHints(Properties values, String language, String text)
    {
        List<String> hints = text.lines().filter(line -> !line.isBlank()).limit(20).toList();
        for (int i = 0; i < hints.size(); i++) values.setProperty("hint." + (i + 1) + "." + language, hints.get(i));
    }
    @OnThread(Tag.Any)
    private static String classpath(Path libraryDirectory, List<File> dependencies) throws IOException
    {
        List<String> entries = new ArrayList<>();
        // The worker also needs BlueJ's existing JUnit runtime, not just the project dependencies.
        try (var jars = Files.list(libraryDirectory))
        { jars.filter(path -> path.toString().endsWith(".jar")).sorted().forEach(path -> entries.add(path.toAbsolutePath().toString())); }
        dependencies.forEach(file -> entries.add(file.getAbsolutePath()));
        return String.join(File.pathSeparator, entries);
    }
    private static void view(PkgMgrFrame frame)
    {
        if (frame.getProject() == null) return;
        Project sourceProject = frame.getProject();Path path = sourceProject.getProjectDir().toPath(),libraryDirectory = Config.getBlueJLibDir().toPath();
        List<File> dependencies = List.copyOf(sourceProject.getClassLoader().getClassPathAsFiles());
        prepare(frame,sourceProject,() -> new ViewData(ExercisePack.load(path),classpath(libraryDirectory,dependencies)),
            prepared -> showView(frame,sourceProject,path,prepared.pack(),prepared.libraries()));
    }

    private static void showView(PkgMgrFrame frame, Project sourceProject, Path project, ExercisePack pack, String libraries)
    {
        Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(frame.getWindow());dialog.initModality(Modality.WINDOW_MODAL); BlueJTheme.setWindowIconFX(dialog);
        dialog.setTitle(pack.text("title", Config.getLocale())); dialog.setResizable(true);
        dialog.getDialogPane().setId("light-exercise-dialog");
        TextArea instructions = area(pack.text("instructions", Config.getLocale()), 10); instructions.setEditable(false);
        TextArea hints = area("", 4); hints.setEditable(false); List<String> available = pack.hints(Config.getLocale()); int[] hintIndex = {0};
        Button hint = new Button(Config.getString("light.hint.next")); hint.setDisable(available.isEmpty());
        hint.setOnAction(e -> { hints.appendText((hintIndex[0] == 0 ? "" : "\n\n") + available.get(hintIndex[0]++)); hint.setDisable(hintIndex[0] == available.size()); });
        ListView<ExerciseRunner.Result> results = new ListView<>(); results.setPrefHeight(180);
        results.setId("light-exercise-results");
        results.setCellFactory(view -> new ListCell<>() {
            @Override @OnThread(value=Tag.FXPlatform, ignoreParent=true)
            protected void updateItem(ExerciseRunner.Result item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Config.getString("light.test." + item.status()) + "  " + item.name());
            }
        });
        TextArea details = area("", 6); details.setEditable(false);
        JavaFXUtil.addChangeListenerPlatform(results.getSelectionModel().selectedItemProperty(), item -> {
            if (item == null) return;
            String comparison = item.expected().isEmpty() && item.actual().isEmpty() ? "" : "\n\n" + Config.getString("light.test.expected")
                + ": " + item.expected() + "\n" + Config.getString("light.test.actual") + ": " + item.actual();
            details.setText(item.message() + comparison + "\n\n" + item.location());
        });
        Button run = new Button(Config.getString("light.exercise.run")), stop = new Button(Config.getString("light.exercise.stop")), export = new Button(Config.getString("light.exercise.report"));
        run.setDisable(pack.tests().isEmpty()); stop.setDisable(true); export.setDisable(true);
        run.setId("light-exercise-run"); stop.setId("light-exercise-stop"); export.setId("light-exercise-export");
        Label status = new Label(); status.setWrapText(true);
        final ExerciseRunner[] runner = {null}; final ExerciseRunner.Report[] report = {null};
        run.setOnAction(e -> {
            Alert consent = new Alert(Alert.AlertType.CONFIRMATION, Config.getString("light.exercise.trust"), ButtonType.OK, ButtonType.CANCEL);
            consent.initOwner(dialog.getDialogPane().getScene().getWindow());
            consent.initModality(Modality.WINDOW_MODAL);
            consent.getDialogPane().setId("light-exercise-trust");
            if (consent.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            if (runner[0] != null || frame.getProject() != sourceProject || sourceProject.isClosing()) return;
            ExerciseRunner job = new ExerciseRunner(); runner[0] = job;
            run.setDisable(true); stop.setDisable(false); export.setDisable(true); results.getItems().clear(); details.clear();
            status.setText(Config.getString("light.exercise.preparing"));
            sourceProject.saveAllEditorsAsync().whenComplete((saved, saveFailure) -> JavaFXUtil.runNowOrLater(() -> {
                if (runner[0] != job) return;
                if (!dialog.isShowing() || frame.getProject() != sourceProject || sourceProject.isClosing() || job.isCancelled() || saveFailure != null)
                {
                    job.cancel();runner[0] = null;
                    if (dialog.isShowing())
                    {
                        run.setDisable(false);stop.setDisable(true);
                        status.setText(Config.getString(saveFailure == null ? "light.exercise.cancelled" : "light.operation.failed"));
                        if (saveFailure != null) details.setText(saveFailure.getLocalizedMessage());
                    }
                    return;
                }
                status.setText(Config.getString("light.exercise.running"));
                Utility.runBackground(() -> {
                try { ExerciseRunner.Report feedback = job.run(project, pack.tests(), libraries); JavaFXUtil.runNowOrLater(() -> {
                    if (!dialog.isShowing() || runner[0] != job || job.isCancelled() || sourceProject.isClosing()) return;
                    report[0] = feedback; results.getItems().setAll(feedback.results()); export.setDisable(false);
                    long passed = feedback.results().stream().filter(result -> result.status().equals("SUCCESSFUL")).count();
                    status.setText(passed + " / " + feedback.results().size() + " " + Config.getString("light.test.passed"));
                    if (feedback.results().isEmpty()) details.setText(Config.getString("light.exercise.noTests") + "\n" + feedback.log());
                    else results.getSelectionModel().selectFirst();
                }); }
                catch (IOException | InterruptedException | RuntimeException | LinkageError | AssertionError ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    JavaFXUtil.runNowOrLater(() -> { if (dialog.isShowing() && runner[0] == job) { status.setText(Config.getString(job.isCancelled() ? "light.exercise.cancelled" : "light.operation.failed")); details.setText(ex.getMessage()); } });
                }
                finally { JavaFXUtil.runNowOrLater(() -> { if (runner[0] == job) { runner[0] = null;if (dialog.isShowing()) { run.setDisable(false); stop.setDisable(true); } } }); }
                });
            }));
        });
        stop.setOnAction(e -> { if (runner[0] != null) { runner[0].cancel(); stop.setDisable(true);status.setText(Config.getString("light.exercise.cancelled")); } });
        export.setOnAction(e -> {
            FileChooser chooser = new FileChooser(); chooser.setTitle(Config.getString("light.exercise.report"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv")); File file = chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if (file == null) return; ExerciseRunner.Report chosen = report[0];
            Utility.runBackground(() -> { try { ExerciseRunner.exportCsv(chosen, file.toPath()); } catch (IOException ex) { JavaFXUtil.runNowOrLater(() -> error(frame.getWindow(), ex)); } });
        });
        TabPane tabs = new TabPane(new Tab(Config.getString("light.exercise.instructions"), new VBox(8, instructions, hint, hints)),
            new Tab(Config.getString("light.exercise.feedback"), new VBox(8, new HBox(8, run, stop, export), status, results, details)));
        tabs.getTabs().forEach(tab -> tab.setClosable(false)); tabs.setPrefSize(780, 560);
        dialog.getDialogPane().setContent(tabs); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.setOnHidden(e -> { if (runner[0] != null) runner[0].cancel(); }); dialog.showAndWait();
    }
}
