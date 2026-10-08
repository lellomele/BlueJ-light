/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2011,2012,2013,2016,2018,2022,2023  Michael Kolling and John Rosenberg
 
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
package bluej.prefmgr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import bluej.pkgmgr.Project;
import bluej.debugger.RunOnThread;
import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.VBox;

import bluej.Config;
import bluej.utility.javafx.JavaFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A PrefPanel subclass to allow the user to interactively edit
 * various miscellaneous settings
 *
 * @author  Andrew Patterson
 */
@OnThread(Tag.FXPlatform)
public class MiscPrefPanel extends VBox 
                           implements PrefPanelListener
{
    private final int normalChildren;
    private CheckBox showUncheckedBox; // show "unchecked" compiler warning
    private TextField playerNameField;
    private ComboBox<RunOnThread> runOnThread;
    private Node threadRunSetting;
    private List<MiscPrefPanelItem> extraItems;

    /**
     * Setup the UI for the dialog and event handlers for the buttons.
     */
    public MiscPrefPanel()
    {
        JavaFXUtil.addStyleClass(this, "prefmgr-pref-panel");

        if (Config.isGreenfoot()) {
            getChildren().add(makePlayerNamePanel());
        }
        else {
            getChildren().add(makeVMPanel());
        }
        normalChildren = getChildren().size();
    }

    // Not called in Greenfoot
    private Node makeVMPanel()
    {
        showUncheckedBox = new CheckBox(Config.getString("prefmgr.misc.showUnchecked"));
        ObservableList<RunOnThread> runOnThreadPoss = FXCollections.observableArrayList(RunOnThread.DEFAULT, RunOnThread.FX, RunOnThread.SWING);
        runOnThread = new ComboBox<>(runOnThreadPoss);
        threadRunSetting = PrefMgrDialog.labelledItem("prefmgr.misc.runOnThread", runOnThread);
        return PrefMgrDialog.headedVBox("prefmgr.misc.vm.title", Arrays.asList(showUncheckedBox, threadRunSetting));
    }

    private Node makePlayerNamePanel()
    {
        List<Node> contents = new ArrayList<>();
        
        // get Accelerator text
        KeyCodeCombination accelerator = Config.GREENFOOT_SET_PLAYER_NAME_SHORTCUT;
        String shortcutText = " " + accelerator.getDisplayText();

        playerNameField = new TextField(PrefMgr.getPlayerName().get());
        playerNameField.setPrefColumnCount(20);
        contents.add(PrefMgrDialog.labelledItem("playername.dialog.help", playerNameField));
        
        contents.add(PrefMgrDialog.wrappedLabel(Config.getString("prefmgr.misc.playerNameNote") + shortcutText));
        
        return PrefMgrDialog.headedVBox("prefmgr.misc.playername.title", contents);
    }

    public void beginEditing(Project project)
    {
        if(!Config.isGreenfoot()) {
            showUncheckedBox.setSelected(PrefMgr.getFlag(PrefMgr.SHOW_UNCHECKED));
            if (project == null)
            {
                threadRunSetting.setVisible(false);
                threadRunSetting.setManaged(false);
            }
            else
            {
                runOnThread.getSelectionModel().select(project.getRunOnThread());
                threadRunSetting.setVisible(true);
                threadRunSetting.setManaged(true);
            }
        }
        else
        {
            playerNameField.setText(PrefMgr.getPlayerName().get());
        }
        extraItems.forEach(p -> p.beginEditing(project));
    }

    public void revertEditing(Project project)
    {
        extraItems.forEach(p -> p.revertEditing(project));
    }

    public void commitEditing(Project project)
    {
        if(!Config.isGreenfoot()) {
            PrefMgr.setFlag(PrefMgr.SHOW_UNCHECKED, showUncheckedBox.isSelected());
            if (project != null)
            {
                // Important to use .name() because we overrode toString() for localized display:
                project.setRunOnThread(runOnThread.getSelectionModel().getSelectedItem());
            }

        }
        
        if (Config.isGreenfoot())
        {
            PrefMgr.getPlayerName().set(playerNameField.getText());
        }
        extraItems.forEach(p -> p.commitEditing(project));
    }

    /**
     * Sets the extra items at the bottom of the panel (in Greenfoot, this is the sound devices).
     * Replaces the previous extra items
     * 
     * @param miscPrefPanelItems The extra items to set.  All existing items will be removed.  Pass the empty list to make it blank.
     */
    public void setExtraItems(List<MiscPrefPanelItem> miscPrefPanelItems)
    {
        // Get rid of any old extra items:
        getChildren().remove(normalChildren, getChildren().size());
        // Add the new ones:
        for (MiscPrefPanelItem miscPrefPanelItem : miscPrefPanelItems)
        {
            getChildren().add(PrefMgrDialog.headedVBoxTranslated(miscPrefPanelItem.getMiscPanelTitle(), miscPrefPanelItem.getMiscPanelContents()));
        }
        this.extraItems = ImmutableList.copyOf(miscPrefPanelItems);
    }
}
