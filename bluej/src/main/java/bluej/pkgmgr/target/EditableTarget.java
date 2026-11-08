/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2011,2013,2014,2016,2017,2018,2020,2021  Michael Kolling and John Rosenberg
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.pkgmgr.target;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import bluej.Config;
import bluej.editor.Editor;
import bluej.editor.EditorWatcher;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.target.actions.EditableTargetOperation;
import bluej.prefmgr.PrefMgrDialog;
import bluej.utility.javafx.ResizableCanvas;
import javafx.scene.canvas.GraphicsContext;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A target in a package that can be edited as text
 *
 * @author  Michael Cahill
 */
public abstract class  EditableTarget extends Target
    implements EditorWatcher
{
    public static final String MENU_STYLE_INBUILT = "class-action-inbuilt";

    public final static String editStr = Config.getString("pkgmgr.classmenu.edit");
    public final static String removeStr = Config.getString("pkgmgr.classmenu.remove");
    protected Editor editor;
    protected boolean fileRemovalPending;
    private CompletableFuture<Boolean> pendingFileRemoval;
    private bluej.editor.flow.FlowEditor fileRemovalEditor;
    private boolean reopenFileAfterFailure;

    protected EditableTarget(Package pkg, String name, String accessibleTargetType)
    {
        super(pkg, name, accessibleTargetType);
    }

    /**
     * @return the name of the (text) file this target corresponds to.
     */
    protected abstract File getSourceFile();

    /**
     * @return the editor object associated with this target
     */
    public abstract Editor getEditor();

    /** Inspect ownership without opening an editor or reading a source file. */
    public Editor getEditorIfOpen() { return editor; }

    /** Text resources obey the same save/retirement boundary as Java sources. */
    protected CompletableFuture<Boolean> removeTextFileAsync(File file)
    {
        if (pendingFileRemoval != null) return pendingFileRemoval.copy();
        Package sourcePackage = getPackage();
        if (sourcePackage == null) return CompletableFuture.completedFuture(true);
        CompletableFuture<Boolean> result = new CompletableFuture<>();pendingFileRemoval = result;
        CompletableFuture<Void> operation = new CompletableFuture<>();
        result.whenComplete((removed, failure) -> bluej.utility.javafx.JavaFXUtil.runNowOrLater(() -> {
            try
            {
                if (!Boolean.TRUE.equals(removed))
                {
                    fileRemovalPending = false;
                    if (fileRemovalEditor != null) fileRemovalEditor.sourceRemovalFailed(reopenFileAfterFailure);
                }
            }
            catch (RuntimeException | LinkageError | AssertionError unavailable) { bluej.utility.Debug.reportError("Cannot reopen retained text after removal failure",unavailable); }
            finally { fileRemovalEditor = null;reopenFileAfterFailure = false;pendingFileRemoval = null;operation.complete(null); }
        }));
        try
        {
            sourcePackage.prepareForSourceFileChange(operation).whenComplete((idle, failure) -> bluej.utility.javafx.FXFuture.run(result, () -> {
                if (failure != null) { result.completeExceptionally(failure);return; }
                bluej.editor.EditorCloseBatch.prepare(() -> editor == null ? List.of() : List.of(editor))
                    .whenComplete((ready, prepareFailure) -> bluej.utility.javafx.FXFuture.run(result, () -> {
                        if (prepareFailure != null) { result.completeExceptionally(prepareFailure);return; }
                        if (!Boolean.TRUE.equals(ready) || getPackage() != sourcePackage) { result.complete(false);return; }
                        bluej.editor.flow.FlowEditor flow = editor instanceof bluej.editor.flow.FlowEditor textEditor ? textEditor : null;
                        var buffer = flow == null ? null : flow.captureSourceSnapshot();
                        fileRemovalEditor = flow;reopenFileAfterFailure = editor != null && editor.isOpen();fileRemovalPending = true;
                        if (flow != null && !flow.beginSourceRemoval()) { result.complete(false);return; }
                        if (editor != null && flow == null) { editor.closePrepared();if (editor.isOpen()) { result.complete(false);return; } }
                        var preferences = Config.getUserConfigDir().toPath();
                        var history = flow == null ? new bluej.light.LocalHistory(preferences) : flow.getLocalHistory();
                        sourcePackage.getProject().getSourceSaveService().removeTextFile(file.toPath(),buffer,preferences.resolve("recovered-sources"),history)
                            .whenComplete((removed, removeFailure) -> bluej.utility.javafx.JavaFXUtil.runNowOrLater(() -> {
                                if (removeFailure != null) { result.completeExceptionally(removeFailure);return; }
                                try
                                {
                                    if (getPackage() == sourcePackage && sourcePackage.getTarget(getIdentifierName()) == this) sourcePackage.removeTarget(this);
                                    if (removed.historyWarning() != null) bluej.utility.Debug.reportError("Text removed with history identity warning",removed.historyWarning());
                                    removed.recoveryCopies().forEach(copy -> bluej.utility.Debug.message("Removed text preserved in: " + copy));
                                    result.complete(true);
                                }
                                catch (Throwable unavailable)
                                {
                                    result.completeExceptionally(unavailable);
                                    if (unavailable instanceof Error fatal) throw fatal;
                                }
                            }));
                    }));
            }));
        }
        catch (RuntimeException | LinkageError | AssertionError unavailable) { result.completeExceptionally(unavailable); }
        return result.copy();
    }

    /**
     * Ensure that the source file of this target is up-to-date (i.e.
     * that any possible unsaved changes in an open editor window are 
     * saved).
     */
    public void ensureSaved() throws IOException
    {
        if (editor != null && editor.isModified())
        {
            editor.save();
        }
    }
    
    /**
     * Called to open the editor for this target
     */
    @OnThread(Tag.FXPlatform)
    public void open()
    {
        Editor editor = getEditor();

        if(editor == null)
            getPackage().showError("error-open-source");
        else
            editor.setEditorVisible(true, false);
    }

    /**
     * Close the editor for this target.
     */
    protected void close()
    {
        getEditor().close();
    }

    /**
     * Return true if this editor has been opened at some point since this project was opened.
     */
    public boolean editorOpen()
    {
        return (editor!=null);
    }
    
    // --- EditorWatcher interface ---
    // (The EditorWatcher methods are typically redefined in subclasses)

    /*
     * Called by Editor when a file is changed
     */
    public void modificationEvent(Editor editor) {}

    /*
     * Called by Editor when a file is saved
     */
    public void saveEvent(Editor editor) {}

    /*
     * Called by Editor when a file is closed
     */
    public void closeEvent(Editor editor) {}

    /*
     * Called by Editor when a breakpoint is been set/cleared
     */
    public boolean breakpointToggleEvent(int lineNo, boolean set)
    { return false; }

    public void clearAllBreakpoints() { }

    @Override
    public void showPreferences(int paneIndex)
    {
        PrefMgrDialog.showDialog(getPackage().getProject(), paneIndex);
    }

    // --- end of EditorWatcher interface ---

}
