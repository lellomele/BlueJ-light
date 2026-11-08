/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2012,2014,2016,2018,2019  Michael Kolling and John Rosenberg 
 
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
package bluej.pkgmgr.actions;

import java.io.File;

import bluej.Config;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.utility.Debug;
import bluej.utility.FileUtility;
import bluej.utility.javafx.JavaFXUtil;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 * User chooses "save project as". This allows saving the project under a
 * different name, to make a backup etc.
 * 
 * @author Davin McCall
 */
final public class SaveProjectAsAction extends PkgMgrAction
{
    public SaveProjectAsAction(PkgMgrFrame pmf)
    {
        super(pmf, "menu.package.saveAs");
    }
    
    public void actionPerformed(PkgMgrFrame pmf)
    {
        pmf.menuCall();
        saveAs(pmf, pmf.getProject());
    }
    
    public void saveAs(PkgMgrFrame frame, Project project)
    {
        // get a file name to save under
        File newName = FileUtility.getSaveProjectFX(project, frame.getWindow(), Config.getString("pkgmgr.saveAs.title"));

        if (newName == null)
        {
            return;
        }
        
        frame.setStatus(Config.getString("light.project.copying"));
        project.copyProjectAsync(newName.toPath()).whenComplete((copy, failure) -> JavaFXUtil.runNowOrLater(() -> {
            if (failure != null)
            { Debug.reportError("Cannot copy project to " + newName,failure);showCopyWarning(frame,newName,"light.project.copyFailed");return; }
            if (!copy.isCurrent()) { showCopyWarning(frame,newName,"light.project.copyChanged");return; }
            PkgMgrFrame.closeProjectIfUnchangedAsync(project,copy.revision(),copy::isCurrent).whenComplete((closed, error) -> JavaFXUtil.runNowOrLater(() -> {
                if (error != null) Debug.reportError("Cannot close original after project copy",error);
                if (error != null || !Boolean.TRUE.equals(closed)) { showCopyWarning(frame,newName,"light.project.copyChanged");return; }
                openCopy(newName);
            }));
        }));
    }

    private void showCopyWarning(PkgMgrFrame frame, File destination, String message)
    {
        frame.setStatus(Config.getString(message));
        Alert warning = new Alert(Alert.AlertType.WARNING,Config.getString(message) + "\n\n" + destination,ButtonType.OK);
        warning.initOwner(frame.getWindow());warning.setTitle(Config.getString("pkgmgr.saveAs.title"));
        bluej.BlueJTheme.setWindowIconFX(warning);warning.show();
    }

    private void openCopy(File newName)
    {
        Project openProj = Project.openProject(newName.getAbsolutePath());

        if (openProj != null)
        {
            Package pkg = openProj.getPackage("");
            PkgMgrFrame pmf = PkgMgrFrame.createFrame(pkg, null);
            pmf.setVisible(true);
        }
        else
        {
            Debug.message("Save as: could not open package under new name");
        }
    }

}
