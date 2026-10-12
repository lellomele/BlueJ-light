/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2014,2015,2016,2017,2018,2019,2020,2021  Michael Kolling and John Rosenberg
 
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
package bluej.editor.tabbed;

import bluej.BlueJTheme;
import bluej.Config;
import bluej.Main;
import bluej.pkgmgr.Project;
import bluej.utility.Debug;
import bluej.utility.Utility;
import bluej.utility.javafx.JavaFXUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * FXTabbedEditor is the editor window that contains all the editors in JavaFX tabs
 *
 * Because you can drag between tabs, it is responsible for handling dragging, rather than the individual editor tabs doing it.
 *
 * It is also responsible for changing the menus on the window when the tab changes.
 */
public @OnThread(Tag.FX) class FXTabbedEditor
{
    private final Project project;
    private Rectangle startSize;
    private Stage stage;
    private Scene scene;
    private TabPane tabPane;
    private MenuBar menuBar;
    @OnThread(Tag.Any) private String projectTitle;
    private final StringProperty titleStatus = new SimpleStringProperty("");
    private BorderPane menuAndTabPane;

    // Neither the constructor nor any initialisers should do any JavaFX work until
    // initialise is called.
    @OnThread(Tag.Any)
    public FXTabbedEditor(Project project, Rectangle startSize)
    {
        this.project = project;
        this.startSize = startSize;
    }

    /**
     * Initialises the FXTabbedEditor.
     */
    @OnThread(Tag.FXPlatform)
    public void initialise()
    {
        projectTitle = project.getProjectName();
        stage = new Stage();
        BlueJTheme.setWindowIconFX(stage);

        initialiseFX();
    }

    /**
     * The actual initialisation, on the FX thread
     */
    @OnThread(Tag.FXPlatform)
    private void initialiseFX()
    {
        // See comment in Main class:
        if (Thread.currentThread().getContextClassLoader() == null)
        {
            Thread.currentThread().setContextClassLoader(Main.getStoredContextClassLoader());
        }

        Config.loadFXFonts();

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        menuBar = new MenuBar();
        JavaFXUtil.addStyleClass(menuBar, "editor-menubar");
        menuBar.setUseSystemMenuBar(true);
        menuAndTabPane = new BorderPane();
        menuAndTabPane.setTop(menuBar);
        menuAndTabPane.setCenter(new StackPane(tabPane));

        scene = new Scene(menuAndTabPane, 800, 700);
        stage.setScene(scene);
        Config.addEditorStylesheets(scene);
        JavaFXUtil.addMacMinimiseShortcutHandler(stage);

        tabPane.getStyleClass().add("tabbed-editor");

        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends Tab> a, Tab prevSel, Tab selTab)
            {
                if (selTab != null)
                    FXTabbedEditor.this.updateMenusForTab((FXTab) selTab);

                if (prevSel != null && prevSel != selTab)
                    ((FXTab) prevSel).notifyUnselected();

                if (selTab != null && stage.isFocused())
                    ((FXTab) selTab).notifySelected();

            }
        });
        
        tabPane.getTabs().addListener((ListChangeListener<? super Tab>) e -> {
            bluej.light.SessionRecovery.changed();
            if (tabPane.getTabs().isEmpty())
            {
                startSize = new Rectangle((int)stage.getX(), (int)stage.getY(), (int)stage.getWidth(), (int)stage.getHeight());
                stage.close();
                project.removeFXTabbedEditor(this);
            }
        });

        stage.setOnCloseRequest(e -> {
            for (FXTab tab : getFXTabs())
            {
                if (!tab.canClose())
                {
                    e.consume();
                    break;
                }
            }
        });

        stage.setOnHidden(e -> {
            // Close all tabs, which also trigger above code to call removeFXTabbedEditor on us
            // Must take a copy to avoid concurrent modification:
            List<Tab> tabs = new ArrayList<>(tabPane.getTabs());
            tabs.forEach(t -> close((FXTab)t));
            if (!tabPane.getTabs().isEmpty())
                JavaFXUtil.runAfterCurrent(stage::show);
        });

        JavaFXUtil.addChangeListenerPlatform(stage.focusedProperty(), focused -> {
            Tab selectedItem = tabPane.getSelectionModel().getSelectedItem();
            // It is possible during shutdown that the window becomes focused while no tabs are present, so guard against that:
            if (selectedItem != null && selectedItem instanceof FXTab)
            {
                if (focused)
                {
                    ((FXTab) selectedItem).notifySelected();
                }
                else
                {
                    // if 'selectedItem' is null, that mean it has been already notified unselected
                    // by the selectedItemProperty Listener added above.
                    ((FXTab) selectedItem).notifyUnselected();
                }
            }
        });

        JavaFXUtil.addChangeListenerPlatform(stage.iconifiedProperty(), minimised -> {
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            if (minimised && selected instanceof FXTab)
                ((FXTab)selected).notifyUnselected();
        });

        // Add shortcuts for Ctrl-1, Ctrl-2 etc and Ctrl-Tab and Ctrl-Shift-Tab to move between tabs
        // On Mac, it should still be Ctrl-Tab (not Cmd-Tab), but should it be Cmd-1?
        tabPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            // Consume presses of AltGr key on Windows to stop it focusing the menu
            // (the user may just be inserting a foreign character, in which case
            // they don't want to trigger the menu)
            if (Config.isWinOS() && (e.getCode() == KeyCode.ALT_GRAPH || (e.getCode() == KeyCode.ALT && e.isControlDown())))
            {
                e.consume();
                return;
            }

            // Also consume any keypress involving Ctrl+Alt on Windows, as this is likely
            // an AltGr shortcut (which maps to Ctrl+Alt on Windows).  This will break
            // any menu accelerators involving Ctrl+Alt, but we shouldn't have any anyway,
            // because they will conflict with AltGr behaviour on Windows.
            if (Config.isWinOS() && e.isAltDown() && e.isControlDown())
            {
                e.consume();
                return;
            }

            // We only listen for Ctrl/Cmd shortcuts, but we avoid the case when
            // Alt is held down, because AltGr on Windows maps to Ctrl-Alt, and we
            // don't want AltGr-6 to move to tab 6 (only Ctrl-6)
            if (!e.isShortcutDown() || e.isAltDown() || tabPane.getTabs().isEmpty())
                return;
            
            int tab = tabPane.getSelectionModel().getSelectedIndex();
            switch (e.getCode())
            {
            // First tab is index 0, but selected with Ctrl+1
            case DIGIT1: tab = 0; break;
            case DIGIT2: tab = 1; break;
            case DIGIT3: tab = 2; break;
            case DIGIT4: tab = 3; break;
            case DIGIT5: tab = 4; break;
            case DIGIT6: tab = 5; break;
            case DIGIT7: tab = 6; break;
            case DIGIT8: tab = 7; break;
            case DIGIT9: tab = 8; break;
            // We don't handle 0
            // Ctrl-Tab is done by default by TabPane, but only when the tab has focus.
            // So we must do it here:
            case TAB:
                if (e.isShiftDown())
                {
                    tab = tab - 1;
                    if (tab < 0)
                        tab = tabPane.getTabs().size() - 1;
                }
                else
                {
                    tab = (tab + 1) % tabPane.getTabs().size();
                }
                break;
            // For all other keys, return and do not process event:
            default: return;
            }
            
            if (tab >= 0 && tab < tabPane.getTabs().size())
            {
                tabPane.getSelectionModel().select(tabPane.getTabs().get(tab));
            }
            e.consume();
        });

        stage.titleProperty().bind(Bindings.concat(
            JavaFXUtil.applyPlatform(tabPane.getSelectionModel().selectedItemProperty(), t -> ((FXTab)t).windowTitleProperty(), "Unknown")
                ," - ", projectTitle, titleStatus));
    }

    @OnThread(Tag.FXPlatform)
    private void updateMenusForTab(FXTab selTab)
    {
        menuBar.getMenus().setAll(selTab.getMenus());
    }

    /**
     * Adds the given FXTab to this FXTabbedEditor window
     * @param panel The FXTab to add
     * @param visible Whether to make the FXTabbedEditor window visible 
     * @param toFront Whether to bring the tab to the front (i.e. select the tab)
     * @return True if the tab was added, false if it was already present
     */
    @OnThread(Tag.FXPlatform)
    public boolean addTab(final FXTab panel, boolean visible, boolean toFront)
    {
        return addTab(panel, visible, toFront, false);
    }

    @OnThread(Tag.FXPlatform)
    private boolean addTab(final FXTab panel, boolean visible, boolean toFront, boolean partOfMove)
    {
        panel.setParent(this, partOfMove);
        // This is ok to call multiple times:
        panel.initialiseFX();
        //Debug.time("initialisedFX");
        if (!tabPane.getTabs().contains(panel))
        {
            tabPane.getTabs().add(panel);
            if (toFront)
            {
                setWindowVisible(visible, panel);
                bringToFront(panel);
                Platform.runLater(panel::focusWhenShown);
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    /** 
     * Opens a Javadoc tab for a core Java class (i.e. comes with the JRE)
     * with given qualified name.
     * 
     * If there already exists a tab viewing that page, that page is selected rather than
     * a new tab being opened.
     */
    @OnThread(Tag.FXPlatform)
    public void openJavaCoreDocTab(String qualifiedClassName)
    {
        openJavaCoreDocTab(qualifiedClassName, "");
    }

    /** Opens a Javadoc tab for a core Java class (i.e. comes with the JRE) with given qualified name,
     *  and the given URL suffix (typically, "#method_anchor")
     *  
     *  If there already exists a tab viewing that page, that page is selected rather than
     * a new tab being opened.
     */
    @OnThread(Tag.FXPlatform)
    public void openJavaCoreDocTab(String qualifiedClassName, String suffix)
    {
        Class<?> theClass = project.loadClass(qualifiedClassName);
        // Guess java.base if we don't know the module:
        String moduleName = theClass == null ? "java.base" : theClass.getModule().getName();
        
        String target = Utility.getDocURL(moduleName, qualifiedClassName, suffix);
        openWebViewTab(target);
    }

    /**
     * Sets the window visible, and adds/removes the given tab
     * @param visible Whether to add the tab and make window visible (true), or remove the tab (false).
     *                Window is only hidden if no tabs remain (handled elsewhere in code)
     * @param tab     The tab in question
     * @return        True if the visible state needed to be changed, false if there was nothing
     *                that needed to be done.
     */
    @OnThread(Tag.FXPlatform)
    public boolean setWindowVisible(boolean visible, Tab tab)
    {
        if (visible)
        {
            boolean wasAlreadyShowing = stage.isShowing();
            if (!wasAlreadyShowing)
            {
                if (startSize != null)
                {
                    Point2D topLeft = Config.ensureOnScreen((int)startSize.getX(), (int)startSize.getY());
                    stage.setX(topLeft.getX());
                    stage.setY(topLeft.getY());
                    stage.setWidth(startSize.getWidth());
                    stage.setHeight(startSize.getHeight());
                }
                //Debug.time("Showing");
                stage.show();
                if (startSize == null)
                {
                    List<Point2D> otherWindowPositions = project.getAllFXTabbedEditorWindows().stream().filter(w -> w != this).map(w -> new Point2D(w.getX(), w.getY())).collect(Collectors.toList());
                    // Check we're not exactly at the same place as another window:
                    Point2D us = new Point2D(stage.getX(), stage.getY());
                    // Avoid an infinite loop, e.g. if we're near the screen edge and aren't allowed to move off it by the OS:
                    int attempts = 0;
                    while (otherWindowPositions.contains(us) && attempts++ < 5)
                    {
                        stage.setX(32 + stage.getX());
                        stage.setY(32 + stage.getY());
                        us = new Point2D(stage.getX(), stage.getY());
                    }
                }
                //Debug.time("Shown");
                //org.scenicview.ScenicView.show(stage.getScene());
            }
            if (!tabPane.getTabs().contains(tab))
            {
                tabPane.getTabs().add(tab);
                return true;
            }
            else
            {
                return !wasAlreadyShowing;
            }
        }
        else
        {
            if (tab instanceof FXTab editorTab && !editorTab.canClose())
                return false;
            return tabPane.getTabs().remove(tab);
        }
    }
    
    /** Returns whether the window is currently shown */
    public boolean isWindowVisible()
    {
        return stage.isShowing();
    }

    /**
     * Brings the tab to the front: unminimises window, brings window to the front, and selects the tab
     */
    @OnThread(Tag.FXPlatform)
    public void bringToFront(Tab tab)
    {
        stage.setIconified(false);
        Utility.bringToFrontFX(stage);
        tabPane.getSelectionModel().select(tab);
    }

    /**
     * Gets the project this window is associated with
     */
    @OnThread(Tag.Any)
    public Project getProject()
    {
        return project;
    }

    /**
     * Removes the given tab from this tabbed editor window
     */
    @OnThread(Tag.FXPlatform)
    public void close(FXTab tab)
    {
        close(tab, false);
    }

    @OnThread(Tag.FXPlatform)
    private void close(FXTab tab, boolean partOfMove)
    {
        if (!partOfMove && !tab.canClose())
            return;
        tabPane.getTabs().remove(tab);
        tab.setParent(null, partOfMove);
    }


    /**
     * Opens a web view tab to display the given URL.
     * 
     * If a web view tab already exists which is displaying that URL (sans anchors),
     * that tab is displayed and a new tab is not opened.
     */
    @OnThread(Tag.FXPlatform)
    public void openWebViewTab(String url)
    {
        openWebViewTab(url, false);
    }

    /**
     * Opens a web view tab to display the given URL.
     *
     * If a web view tab already exists which is displaying that URL (sans anchors),
     * that tab is displayed and a new tab is not opened.
     *
     * @param isTutorial True if this is a special web tab containing the interactive tutorial
     */
    @OnThread(Tag.FXPlatform)
    public void openWebViewTab(String url, boolean isTutorial)
    {
        // First, check if any tab is already showing that URL:
        try
        {
            URI target = new URI(url);
            for (FXTab tab : getFXTabs())
            {
                if (tab.getWebAddress() == null)
                    continue;

                // Use URI comparison so that URIs get canonicalised:
                URI tabURI = new URI(tab.getWebAddress());
                if (tabURI.equals(target))
                {
                    // Focus that tab and stop:
                    bringToFront(tab);
                    return;
                }
            }
        }
        catch (URISyntaxException e)
        {
            Debug.reportError("Error in URI when opening web view tab: \"" + url + "\"");
        }

        addTab(new WebTab(url, isTutorial), true, true);
    }

    /**
     * The list of currently open tabs
     */
    public ObservableList<Tab> tabsProperty()
    {
        return tabPane.getTabs();
    }

    /**
     * Returns true when, and only when, this editor window has a single tab in it
     */
    public boolean hasOneTab()
    {
        return tabPane.getTabs().size() == 1;
    }

    public boolean containsTab(Tab tab)
    {
        return tabPane.getTabs().contains(tab);
    }

    public StringExpression titleProperty()
    {
        return stage.titleProperty();
    }

    private List<FXTab> getFXTabs()
    {
        return Utility.mapList(tabPane.getTabs(), t -> (FXTab)t);
    }

    public void setTitleStatus(String status)
    {
        this.titleStatus.set(status);
    }

    public Stage getStage()
    {
        return stage;
    }

    /**
     * Called when this window is no longer going to be used
     */
    public void cleanup()
    {

    }

    /**
     * Does one of the tabs in this window contain a tutorial web view tab?
     */
    public boolean hasTutorial()
    {
        return tabPane.getTabs().stream().anyMatch(t -> t instanceof FXTab && ((FXTab)t).isTutorial());
    }

    public double getRenderScaleX()
    {
        return stage.getRenderScaleX();
    }

    public double getRenderScaleY()
    {
        return stage.getRenderScaleY();
    }

    @OnThread(Tag.FXPlatform)
    public void moveToNewLater(FXTab tab)
    {
        FXTabbedEditor newWindow = project.createNewFXTabbedEditor();
        moveTabTo(tab, newWindow);
    }

    @OnThread(Tag.FXPlatform)
    public void moveTabTo(FXTab tab, FXTabbedEditor destination)
    {
        close(tab, true);
        destination.addTab(tab, true, true, true);
    }

    @OnThread(Tag.FXPlatform)
    public void updateMoveMenus()
    {
        tabPane.getTabs().forEach(t -> updateMenusForTab((FXTab)t));
    }

    @OnThread(Tag.FX)
    public int getX()
    {
        return (int)stage.getX();
    }

    @OnThread(Tag.FX)
    public int getY()
    {
        return (int)stage.getY();
    }

    @OnThread(Tag.FX)
    public int getWidth()
    {
        return (int)stage.getWidth();
    }

    @OnThread(Tag.FX)
    public int getHeight()
    {
        return (int)stage.getHeight();
    }
}
