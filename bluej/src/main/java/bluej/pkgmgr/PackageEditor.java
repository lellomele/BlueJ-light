/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2012,2013,2014,2016,2017,2018,2019,2020,2021,2022,2023,2025  Michael Kolling and John Rosenberg
 
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
package bluej.pkgmgr;

import bluej.Config;
import bluej.compiler.CompileReason;
import bluej.compiler.CompileType;
import bluej.debugger.DebuggerObject;
import bluej.debugger.gentype.GenTypeClass;
import bluej.extmgr.ExtensionsManager;
import bluej.extmgr.ExtensionsMenuManager;
import bluej.extmgr.PackageExtensionMenu;
import bluej.graph.SelectionController;
import bluej.pkgmgr.dependency.Dependency;
import bluej.pkgmgr.dependency.UsesDependency;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.DependentTarget;
import bluej.pkgmgr.target.Target;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.DialogManager;
import bluej.utility.Utility;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.JavaFXUtil;
import bluej.utility.javafx.ResizableCanvas;
import bluej.views.CallableView;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
import java.util.*;

/**
 * The main class diagram in the BlueJ package manager frame, supporting
 * dragging/resizing of classes (incl multi-select), and drawing/creation of relations.
 *
 * The PackageEditor is a StackPane with five child panes.  The back one is a Canvas,
 * on which we draw the arrows (this way, the arrows always appear behind the classes).
 * The second is a layer which contains classes which appear underneath other classes:
 * currently, this is just test classes (which always appear behind the class they
 * are testing).  The third is a layer for all other targets.  The fourth is a pane
 * on which we draw the class selection rectangle, so that it always appears on top of
 * the classes. The fifth is just a label to show a message when the current package is empty.
 */
@OnThread(Tag.FXPlatform)
public final class PackageEditor extends StackPane
    implements MouseTrackingOverlayPane.MousePositionListener, PkgMgrFrame.PkgMgrPane, PackageListener,
        PackageUI
{
    private static final int RIGHT_PLACEMENT_MIN = 300;
    private static final int WHITESPACE_SIZE = 10;
    /**  The grid resolution for graph layout. */
    public static final int GRID_SIZE = 10;
    
    private final PkgMgrFrame pmf;
    private final Package pkg;
    private final SelectionController selectionController;
    // Are we showing extends/inherits arrows?
    private final BooleanProperty showExtends;
    // Are we showing uses arrows?
    private final BooleanProperty showUses;

    // Two class layers: one front (for normal classes),
    // and one back (for test classes)
    private final AnchorPane frontClassLayer = new AnchorPaneExtraSpacing();
    private final AnchorPane backClassLayer = new AnchorPane();
    // The layer at the front on which we draw the selection rectangle:
    private final Pane selectionLayer = new Pane();
    // The label to show a massage to create or add a class
    protected Label noClassesExistedMessage;
    // The layer at the back where we draw the arrows:
    private final Canvas arrowLayer = new ResizableCanvas();
    // Boolean remembering whether we've already scheduled a repaint.
    private boolean aboutToRepaint = false;
    private boolean applyingLayout;
    private boolean layoutRunning;
    private long layoutRequest;
    private final java.util.Deque<LayoutState> layoutUndo = new java.util.ArrayDeque<>();
    private final java.util.Deque<LayoutState> layoutRedo = new java.util.ArrayDeque<>();
    private java.util.Set<Target> knownVertices = java.util.Set.of();
    private boolean selectedConnectionsOnly;
    private record LayoutState(java.util.Map<Target, Point2D> positions,
        java.util.Map<Dependency, List<Point2D>> routes) {}
    // The ContextMenu that is currently being shown on screen (null if not visible)
    @OnThread(Tag.FXPlatform)
    private ContextMenu showingContextMenu;
    
    // For showing info about the arrow-drawing in progress; the overlay pane:
    @OnThread(Tag.FXPlatform)
    private MouseTrackingOverlayPane overlay;
    // The tooltip shown when creating an arrow (null if not showing):
    @OnThread(Tag.FXPlatform)
    private Label arrowCreationTip;
    // Are we currently creating a new extends arrow?
    @OnThread(Tag.FXPlatform)
    private boolean creatingExtends = false;
    // If we are creating a new extends arrow, this is the "from" class which
    // has been selected, or null if we have not yet selected the from.
    @OnThread(Tag.FXPlatform)
    private ClassTarget extendsSubClass;
    // If we are creating a new extends arrow, and we're on to selecting a "to" class,
    // this indicates which class we are hovering over (or null if none):
    @OnThread(Tag.FXPlatform)
    private ClassTarget extendsSuperClassHover;
    // X, Y coordinates local to the pane for where the mouse is when
    // drawing new dependency.
    @OnThread(Tag.FXPlatform)
    private double newExtendsDestX;
    @OnThread(Tag.FXPlatform)
    private double newExtendsDestY;
    
    // Measured in System.currentTimeMillis():
    private long lastKeypress = 0;
    private String currentSearch = "";
    
    

    /**
     * Construct a package editor for the given package.
     */
    @OnThread(Tag.FXPlatform)
    public PackageEditor(PkgMgrFrame pmf, Package pkg, BooleanProperty showUses, BooleanProperty showInherits, MouseTrackingOverlayPane overlay)
    {
        this.pmf = pmf;
        this.pkg = pkg;
        this.selectionController = new SelectionController(this);
        this.showUses = showUses;
        this.showExtends = showInherits;
        this.overlay = overlay;

        this.selectionController.addSelectionListener(sel -> pmf.notifySelectionChanged(sel));
        JavaFXUtil.addStyleClass(this, "class-diagram");
        // Both class layers have transparent background to see through to lower layers:
        frontClassLayer.setBackground(null);
        backClassLayer.setBackground(null);
        // We need to be able to click through the holes in the front class layer
        // in order to click on the back layer:
        frontClassLayer.setPickOnBounds(false);

        JavaFXUtil.addChangeListenerPlatform(arrowLayer.widthProperty(), s -> repaint());
        JavaFXUtil.addChangeListenerPlatform(arrowLayer.heightProperty(), s -> repaint());
        // The mouse events occur on us not on the selection layer.
        // We don't want the display getting in the way of mouse events:
        selectionLayer.setMouseTransparent(true);
        javafx.scene.shape.Rectangle rect = selectionController.getMarquee().getRectangle();
        JavaFXUtil.addStyleClass(rect, "marquee");
        selectionLayer.getChildren().add(rect);

        noClassesExistedMessage = new Label(Config.getString("pkgmgr.noClassesExisted.message"));
        noClassesExistedMessage.setVisible(false);
        JavaFXUtil.addStyleClass(noClassesExistedMessage, "pmf-no-classes-msg");

        getChildren().addAll(arrowLayer, backClassLayer, frontClassLayer, selectionLayer, noClassesExistedMessage);

        JavaFXUtil.addChangeListener(showUses, e -> JavaFXUtil.runNowOrLater(this::repaint));
        JavaFXUtil.addChangeListener(showExtends, e -> JavaFXUtil.runNowOrLater(this::repaint));

        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && creatingExtends)
                stopNewInherits();
            // Don't consume either way
        });
        
        setPrefHeight(400.0);
    }

    @OnThread(Tag.FXPlatform)
    public void benchToFixture(ClassTarget t)
    {
        // put objects on object bench into fixtures
        pmf.objectBenchToTestFixture(t);
    }

    @OnThread(Tag.FXPlatform)
    public void fixtureToBench(ClassTarget t)
    {
        // put objects on object bench into fixtures
        pmf.testFixtureToObjectBench(t);
    }

    @OnThread(Tag.FXPlatform)
    public void makeTestCase(ClassTarget t)
    {
        // start recording a new test case
        pmf.makeTestCase(t);
    }

    @OnThread(Tag.FXPlatform)
    public void runTest(ClassTarget ct, String name)
    {
        // user has initiated a run operation
        ct.getRole().run(pmf, ct, name);
    }

    @OnThread(Tag.FXPlatform)
    public void openPackage(String packageName)
    {
        // user has initiated a package open operation
        pmf.openPackageTarget(packageName);
    }

    /**
     * Raise an event to notify that an object should be placed on the oject bench.
     * @param src  The source of the event
     * @param obj    The object to be put on the event
     * @param iType  The "interface" type of the object (declared type, used as a
 *               fallback if the runtime type is not accessible)
     * @param ir   The invoker record for the invocation used to create this object
     * @param animateFromScenePoint
     */
    @OnThread(Tag.FXPlatform)
    public void raisePutOnBenchEvent(Window src, DebuggerObject obj, GenTypeClass iType, InvokerRecord ir, boolean askForName, Optional<Point2D> animateFromScenePoint)
    {
        pmf.putObjectOnBench(src, obj, iType, ir, askForName, animateFromScenePoint);
    }
    
    /**
     * Notify of some interaction.
     */
    @OnThread(Tag.FXPlatform)
    public void recordInteraction(InvokerRecord ir)
    {
        pmf.recordInteraction(ir);
    }
    
    private boolean popupMenu(double screenX, double screenY)
    {
        ContextMenu menu = new ContextMenu();
        Point2D graphLoc = screenToLocal(screenX, screenY);
        for (Dependency d : getVisibleEdges())
        {
            if (d.isRemovable() && d.contains((int)graphLoc.getX(), (int)graphLoc.getY()))
            {
                // Show a remove menu
                MenuItem removeEdge = new MenuItem(Config.getString("menu.edit.remove"));
                removeEdge.setOnAction(e -> {
                    d.remove();
                });
                JavaFXUtil.addStyleClass(removeEdge, "class-action-inbuilt");
                menu.setOnShowing(e -> {
                    d.setSelected(true);
                });
                menu.setOnHiding(e -> {
                    d.setSelected(false);
                });
                menu.getItems().add(removeEdge);
                showingMenu(menu);
                menu.show(this, screenX, screenY);
                return true;
            }
        }
        MenuItem newClass = new MenuItem(Config.getString("menu.edit.newClass"));
        newClass.setOnAction(e -> {
            pmf.menuCall();
            pmf.doCreateNewClass(graphLoc.getX(), graphLoc.getY());
        });
        JavaFXUtil.addStyleClass(newClass, "class-action-inbuilt");

        MenuItem newPackage = new MenuItem(Config.getString("menu.edit.newPackage"));
        newPackage.setOnAction(e -> {
            pmf.menuCall();
            pmf.doCreateNewPackage(graphLoc.getX(), graphLoc.getY());
        });
        JavaFXUtil.addStyleClass(newPackage, "class-action-inbuilt");
        
        MenuItem newCSS = new MenuItem(Config.getString("menu.edit.newText"));
        newCSS.setOnAction(e -> {
            pmf.menuCall();
            pmf.doCreateNewTextFile(graphLoc.getX(), graphLoc.getY());
        });
        JavaFXUtil.addStyleClass(newCSS, "class-action-inbuilt");

        MenuItem addClassFromFile = new MenuItem(Config.getString("menu.edit.addClass"));
        addClassFromFile.setOnAction(e -> {
            pmf.menuCall();
            pmf.doAddFromFile();
        });
        JavaFXUtil.addStyleClass(addClassFromFile, "class-action-inbuilt");

        menu.getItems().addAll(newClass, newPackage, newCSS, addClassFromFile);
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        menu.getItems().addAll(arrangementMenuItems());

        // Add the extension items
        ExtensionsManager extMgr = ExtensionsManager.getInstance();
        PackageExtensionMenu menuGenerator = new PackageExtensionMenu(pkg);
        ExtensionsMenuManager menuManager = new ExtensionsMenuManager(menu, extMgr, menuGenerator);
        menuManager.addExtensionMenu(pkg.getProject());
        showingMenu(menu);
        menu.show(this, screenX, screenY);

        return true;
    }

    // Record that we are showing this new context menu.
    // If a context menu was already showing, dismiss it.
    private void showingMenu(ContextMenu menu)
    {
        if (showingContextMenu != null)
        {
            showingContextMenu.hide();
        }
        showingContextMenu = menu;
    }

    // Gets the FX window in which this package editor lies.
    @OnThread(Tag.FXPlatform)
    public Window getFXWindow()
    {
        return pmf.getWindow();
    }

    /**
     * Position the given vertex nicely in the graph. Thsi usually means that it
     * will be placed somewhere near the top where it does not overlap with
     * existing vertices.
     *
     * @param t
     *            The vertex to place.
     */
    public void findSpaceForVertex(Target t)
    {
        ArrayList<Rectangle2D> a = new ArrayList<>();

        for (Target vertex : pkg.getVertices())
        {
            // lets discount the vertex we are adding from the space
            // calculations
            if (vertex != t) {
                Rectangle2D vr = new Rectangle2D(vertex.getX(), vertex.getY(), (int)vertex.getWidth(), (int)vertex.getHeight());
                a.add(vr);
            }
        }
        
        double minWidth = 300;
        double minHeight = 200;

        if (RIGHT_PLACEMENT_MIN > minWidth)
            minWidth = RIGHT_PLACEMENT_MIN;

        Rectangle2D targetRect = new Rectangle2D(0, 0, (int)t.getWidth() + WHITESPACE_SIZE * 2, (int)t.getHeight() + WHITESPACE_SIZE * 2);

        for (int y = 0; y < (2 * minHeight); y += 10) {
            for (int x = 0; x < (minWidth - t.getWidth() - 2 * WHITESPACE_SIZE); x += 10) {
                final Rectangle2D targetRectMoved = new Rectangle2D(x, y, targetRect.getWidth(), targetRect.getHeight());
                if (a.stream().noneMatch(r -> r.intersects(targetRectMoved))) {
                    t.setPos(x + 10, y + 10);
                    return;
                }
            }
        }

        t.setPos(10, (int)minHeight + 10);
    }

    @Override
    @OnThread(value = Tag.FXPlatform)
    public void graphChanged()
    {
        java.util.Set<Target> currentVertices = new java.util.HashSet<>(pkg.getVertices());
        if (!knownVertices.isEmpty() && !knownVertices.equals(currentVertices))
            geometryChanged();
        knownVertices = currentVertices;
        HashMap<Node, Boolean> keep = new HashMap<>();

        // We assume all components currently in the graph belong to vertices.
        // We first mark all of them as no longer needed:
        for (Node c : frontClassLayer.getChildren())
        {
            keep.put(c, false);
        }
        for (Node c : backClassLayer.getChildren())
        {
            keep.put(c, false);
        }

        // Add what needs to be added:
        for (Target v : pkg.getVertices())
        {
            if (!keep.containsKey(v.getNode()))
            {
                (v.isFront() ? frontClassLayer : backClassLayer).getChildren().add(v.getNode());
                //v.getComponent().addFocusListener(focusListener);
                //v.getComponent().addFocusListener(selectionController);
                //v.getComponent().addKeyListener(selectionController);
                if (v.isSelected()) {
                    selectionController.addToSelection(v);
                }
            }
            // If it's in the vertices, keep it:
            keep.put(v.getNode(), true);
        }
        // Remove what needs to be removed (i.e. what we didn't see in the vertices):
        for (Map.Entry<Node, Boolean> e : keep.entrySet())
        {
            if (e.getValue().booleanValue() == false)
            {
                frontClassLayer.getChildren().remove(e.getKey());
                backClassLayer.getChildren().remove(e.getKey());
                //e.getKey().removeFocusListener(focusListener);
                //e.getKey().removeFocusListener(selectionController);
                //e.getKey().removeKeyListener(selectionController);
            }
        }

        pmf.graphChanged();
        
        //TODO make sure removed items aren't still in the selection

        repaint();
    }
    
    public void graphClosed()
    {
        
    }

    private static final int ARROW_SIZE = 18; // pixels
    private static final double ARROW_ANGLE = Math.PI / 6; // radians
    private static final double DASHES[] = {5.0f, 2.0f};

    /**
     * Schedules a repaint.  The repaint is done with a runLater,
     * but using this method avoids a double repaint in common cases,
     * e.g. we have a listener on X and Y or width and height, and both change
     * in one go; we want to redraw once, not twice.
     */
    public void repaint()
    {
        if (!aboutToRepaint)
        {
            aboutToRepaint = true;
            JavaFXUtil.runAfterCurrent(this::actualRepaint);
        }
    }

    /** Records that the mouse is now hovering over the given target */
    public void setMouseIn(Target target)
    {
        if (creatingExtends && extendsSubClass != null && target instanceof ClassTarget)
            extendsSuperClassHover = (ClassTarget)target;
    }

    /** Records that the mouse has stopped hovering over the given target. */
    public void setMouseLeft(Target target)
    {
        if (extendsSuperClassHover == target)
            extendsSuperClassHover = null;
    }

    /**
     * Check whether the focus is still on one of the vertices in the graph.
     * If not, clears the selection.
     */
    public void checkForLossOfFocus()
    {
        // If none of our children have focus any more
        // after processing has completed, select none:
        JavaFXUtil.runAfterCurrent(() -> {
            // We want to cancel selection if codepad gets focus (for example) but not
            // if the reason for our loss of focus is that the whole window has lost focus:
            if (!targetHasFocus() && getFXWindow().isFocused())
            {
                selectionController.clearSelection();
            }
        });
    }

    /**
     * Does one of the targets in the class diagram have focus?
     * @return
     */
    public boolean targetHasFocus()
    {
        return pkg.getVertices().stream().anyMatch(Target::isFocused);
    }

    /**
     * Tries to focus a target in the class diagram.  If there is
     * already a selection, we focus one of the selected items.
     * If not, we try the most recent selection.
     * If there wasn't one or it wasn't valid, we select an arbitrary target.
     * @return true if we found something to focus, false if there was nothing to focus.
     */
    public boolean focusSelectedOrArbitrary()
    {
        if (selectionController.getSelection().isEmpty())
        {
            if (pkg.getVertices().isEmpty())
                return false;
            else
            {
                if (!selectionController.restoreRecentSelectionAndFocus(pkg.getVertices()::contains))
                    pkg.getVertices().get(0).requestFocus();
                return true;
            }
        }
        else
        {
            selectionController.getSelection().get(0).requestFocus();
            return true;
        }
    }

    public boolean isCreatingExtends()
    {
        return creatingExtends;
    }

    public List<Target> getSelection()
    {
        return selectionController.getSelection();
    }

    public void setReRun(ClassTarget target, FXPlatformRunnable run)
    {
        pmf.setReRun(target, run);
    }

    /**
     * A class caching the vital details needed to draw an extends dependency line,
     * which could be either real and finished, or in-progress of being created.
     */
    @OnThread(Tag.FXPlatform)
    private static class ExtendsDepInfo
    {
        private final Dependency.Line line;
        private final boolean selected;
        private final boolean creating;
        private Dependency.Type type;

        public ExtendsDepInfo(Dependency d)
        {
            this.line = d.computeLine();
            this.selected = d.isSelected();
            this.creating = false;
            this.type=d.getType();
        }

        // When we have a firm from, but the to point is not currently
        // over any target
        public ExtendsDepInfo(DependentTarget from, double toX, double toY)
        {
            // Compute centre points of source and dest target
            Point2D pFrom = new Point2D(from.getX() + from.getWidth() / 2, from.getY() + from.getHeight() / 2);
            Point2D pTo = new Point2D(toX, toY);

            // Get the angle of the line from pFrom to pTo.
            double angle = Math.atan2(-(pFrom.getY() - pTo.getY()), pFrom.getX() - pTo.getX());

            // Compute intersection points with target border
            pFrom = from.getAttachment(angle + Math.PI);

            line = new Dependency.Line(pFrom, pTo, angle);
            selected = false;
            creating = true;
        }

        // When we have a firm from, and the mouse is hovering over an
        // actual target for a to.
        public ExtendsDepInfo(DependentTarget from, DependentTarget to)
        {
            // Compute centre points of source and dest target
            Point2D pFrom = new Point2D(from.getX() + from.getWidth() / 2, from.getY() + from.getHeight() / 2);
            Point2D pTo = new Point2D(to.getX() + to.getWidth() / 2, to.getY() + to.getHeight() / 2);

            // Get the angle of the line from pFrom to pTo.
            double angle = Math.atan2(-(pFrom.getY() - pTo.getY()), pFrom.getX() - pTo.getX());

            // Compute intersection points with target border
            pFrom = from.getAttachment(angle + Math.PI);
            pTo = to.getAttachment(angle);

            line = new Dependency.Line(pFrom, pTo, angle);
            selected = false;
            creating = true;
        }
    }

    /**
     * Does the actual repaint of the arrowLayer (do not call directly;
     * see repaint method).
     */
    private void actualRepaint()
    {
        aboutToRepaint = false;
        List<Dependency> extendsDeps = isShowExtends() ? new ArrayList<>(pkg.getExtendsArrows()) : Collections.emptyList();;
        List<UsesDependency> usesDeps = isShowUses() ? new ArrayList<>(pkg.getUsesArrows()) : Collections.emptyList();

        List<ExtendsDepInfo> extendsLines = new ArrayList<>(Utility.mapList(
            extendsDeps.stream().filter(d -> d.getLayoutRoute().isEmpty()).collect(java.util.stream.Collectors.toList()), ExtendsDepInfo::new));
        if (extendsSubClass != null)
        {
            if (extendsSuperClassHover != null)
            {
                extendsLines.add(new ExtendsDepInfo(extendsSubClass, extendsSuperClassHover));
            }
            else
            {
                Point2D p = arrowLayer.sceneToLocal(newExtendsDestX, newExtendsDestY);
                extendsLines.add(new ExtendsDepInfo(extendsSubClass, p.getX(), p.getY()));
            }
        }
        
        
        GraphicsContext g = arrowLayer.getGraphicsContext2D();
        g.clearRect(0, 0, arrowLayer.getWidth(), arrowLayer.getHeight());
        for (Dependency dependency : getVisibleEdges())
            if (!dependency.getLayoutRoute().isEmpty())
                drawLayoutRoute(g, dependency);

        for (ExtendsDepInfo d : extendsLines)
        {
            g.setStroke(d.creating ? Color.BLUE : Color.BLACK);
            g.setLineWidth(d.selected ? 3.0 : 1.0);
            Dependency.Line line = d.line;
            double fromY = line.from.getY();
            double fromX = line.from.getX();
            double toY = line.to.getY();
            double toX = line.to.getX();


            double angle = Math.atan2(-(fromY - toY), fromX - toX);

            double arrowJoinX = toX + ((ARROW_SIZE - 2) * Math.cos(angle));
            double arrowJoinY = toY - ((ARROW_SIZE - 2) * Math.sin(angle));

            // draw the arrow head
            double[] xPoints = {toX, toX + ((ARROW_SIZE) * Math.cos(angle + ARROW_ANGLE)),
                    toX + (ARROW_SIZE * Math.cos(angle - ARROW_ANGLE))};
            double[] yPoints = {toY, toY - ((ARROW_SIZE) * Math.sin(angle + ARROW_ANGLE)),
                    toY - (ARROW_SIZE * Math.sin(angle - ARROW_ANGLE))};
            g.setLineDashes();
            g.strokePolygon(xPoints, yPoints, 3);
            if (d.type==Dependency.Type.IMPLEMENTS)
            {
                g.setLineDashes(DASHES);
            }
            else
            {
                g.setLineDashes();
            }
            g.strokeLine(fromX, fromY, arrowJoinX, arrowJoinY);
        }

        for (UsesDependency d : usesDeps)
        {
            if (!d.getLayoutRoute().isEmpty()) continue;
            // Special case - don't draw a dependency line between a Foo class and the FooTest class:
            if (d.to instanceof DependentTarget && ((DependentTarget)d.to).getAssociation() == d.from)
                continue;
            
            g.setLineWidth(1.0);
            g.setLineDashes(DASHES);
            // These should all be rounded to the nearest integer+0.5 value:
            double src_x = d.getSourceX();
            double src_y = d.getSourceY();
            double dst_x = d.getDestX();
            double dst_y = d.getDestY();

            g.setStroke(Color.BLACK);
            // Draw the end arrow
            int delta_x = d.isEndLeft() ? -10 : 10;

            g.strokeLine(dst_x, dst_y, dst_x + delta_x, dst_y + 4);
            g.strokeLine(dst_x, dst_y, dst_x + delta_x, dst_y - 4);
            g.setLineDashes(DASHES);

            // Draw the start
            double corner_y = src_y + (d.isStartTop() ? -15 : 15);
            g.strokeLine(src_x, corner_y, src_x, src_y);
            src_y = corner_y;

            // Draw the last line segment
            double corner_x = dst_x + (d.isEndLeft() ? -15 : 15);
            g.strokeLine(corner_x, dst_y, dst_x, dst_y);
            dst_x = corner_x;

            // if arrow vertical corner, draw first segment up to corner
            if ((src_y != dst_y) && (d.isStartTop() == (src_y < dst_y))) {
                corner_x = Utility.roundHalf(((src_x + dst_x) / 2) + (d.isEndLeft() ? 15 : -15));
                corner_x = (d.isEndLeft() ? Math.min(dst_x, corner_x) : Math.max(dst_x, corner_x));
                g.strokeLine(src_x, src_y, corner_x, src_y);
                src_x = corner_x;
            }

            // if arrow horiz. corner, draw first segment up to corner
            if ((src_x != dst_x) && (d.isEndLeft() == (src_x > dst_x))) {
                corner_y = Utility.roundHalf(((src_y + dst_y) / 2) + (d.isStartTop() ? 15 : -15));
                corner_y = (d.isStartTop() ? Math.min(src_y, corner_y) : Math.max(src_y, corner_y));
                g.strokeLine(dst_x, corner_y, dst_x, dst_y);
                dst_y = corner_y;
            }

            // draw the middle bit
            g.strokeLine(src_x, src_y, src_x, dst_y);
            g.strokeLine(src_x, dst_y, dst_x, dst_y);
        }
    }


    /**
     * Clear the set of selected classes. (Nothing will be selected after this.)
     */
    public void clearSelection()
    {
        selectionController.clearSelection();
    }

    private void drawLayoutRoute(GraphicsContext g, Dependency dependency)
    {
        List<Point2D> points = dependency.getLayoutRoute();
        if (points.size() < 2) return;
        g.setStroke(Color.BLACK);
        g.setLineWidth(dependency.isSelected() ? 3.0 : 1.0);
        if (dependency.getType() == Dependency.Type.USES || dependency.getType() == Dependency.Type.IMPLEMENTS)
            g.setLineDashes(DASHES);
        else g.setLineDashes();
        for (int i = 1; i < points.size(); i++)
        {
            Point2D a = points.get(i - 1), b = points.get(i);
            g.strokeLine(a.getX(), a.getY(), b.getX(), b.getY());
        }
        Point2D end = points.getLast(), previous = points.get(points.size() - 2);
        Point2D direction = previous.subtract(end).normalize();
        Point2D normal = new Point2D(-direction.getY(), direction.getX());
        Point2D a = end.add(direction.multiply(12)).add(normal.multiply(5));
        Point2D b = end.add(direction.multiply(12)).subtract(normal.multiply(5));
        g.setLineDashes();
        if (dependency.getType() == Dependency.Type.USES)
        {
            g.strokeLine(end.getX(), end.getY(), a.getX(), a.getY());
            g.strokeLine(end.getX(), end.getY(), b.getX(), b.getY());
        }
        else
        {
            g.setFill(Color.WHITE);
            g.fillPolygon(new double[] {end.getX(), a.getX(), b.getX()},
                new double[] {end.getY(), a.getY(), b.getY()}, 3);
            g.strokePolygon(new double[] {end.getX(), a.getX(), b.getX()},
                new double[] {end.getY(), a.getY(), b.getY()}, 3);
        }
    }

    public void geometryChanged()
    {
        if (applyingLayout) return;
        layoutRequest++;
        layoutRunning = false;
        layoutUndo.clear();
        layoutRedo.clear();
        for (Dependency edge : pkg.getUsesArrows()) edge.setLayoutRoute(List.of());
        for (Dependency edge : pkg.getExtendsArrows()) edge.setLayoutRoute(List.of());
    }

    public boolean isArranging() { return layoutRunning; }
    public boolean canUndoArrangement() { return !layoutUndo.isEmpty() && !layoutRunning; }
    public boolean canRedoArrangement() { return !layoutRedo.isEmpty() && !layoutRunning; }

    public List<MenuItem> arrangementMenuItems()
    {
        MenuItem arrange = new MenuItem(Config.getString("light.diagram.arrange"));
        arrange.setDisable(layoutRunning || pkg.getVertices().isEmpty());
        arrange.setOnAction(e -> arrangeDiagram());
        MenuItem undo = new MenuItem(Config.getString("light.diagram.undo"));
        undo.setDisable(!canUndoArrangement());
        undo.setOnAction(e -> undoArrangement());
        MenuItem redo = new MenuItem(Config.getString("light.diagram.redo"));
        redo.setDisable(!canRedoArrangement());
        redo.setOnAction(e -> redoArrangement());
        MenuItem cancel = new MenuItem(Config.getString("light.diagram.cancel"));
        cancel.setDisable(!layoutRunning);
        cancel.setOnAction(e -> cancelArrangement());
        MenuItem newOnly = new MenuItem(Config.getString("light.diagram.newOnly"));
        newOnly.setDisable(layoutRunning); newOnly.setOnAction(e -> arrangeDiagram(true));
        javafx.scene.control.CheckMenuItem lock = new javafx.scene.control.CheckMenuItem(Config.getString("light.diagram.lock"));
        lock.setDisable(getSelection().isEmpty());
        lock.setSelected(!getSelection().isEmpty() && getSelection().stream().allMatch(Target::isLayoutLocked));
        lock.setOnAction(e -> {
            for (Target target : getSelection())
            {
                target.setLayoutLocked(lock.isSelected());
                if (target instanceof DependentTarget dependent && dependent.getAssociation() != null)
                    dependent.getAssociation().setLayoutLocked(lock.isSelected());
                for (Target owner : pkg.getVertices())
                    if (owner instanceof DependentTarget dependent && dependent.getAssociation() == target)
                        owner.setLayoutLocked(lock.isSelected());
            }
            geometryChanged(); repaint();
        });
        javafx.scene.control.CheckMenuItem focus = new javafx.scene.control.CheckMenuItem(Config.getString("light.diagram.focus"));
        focus.setSelected(selectedConnectionsOnly);
        focus.setOnAction(e -> { selectedConnectionsOnly = focus.isSelected(); repaint(); });
        return List.of(arrange, newOnly, lock, focus, undo, redo, cancel);
    }

    public void cancelArrangement() { layoutRequest++; layoutRunning = false; }

    private LayoutState captureLayout()
    {
        java.util.Map<Target, Point2D> positions = new java.util.LinkedHashMap<>();
        pkg.getVertices().forEach(t -> positions.put(t, new Point2D(t.getX(), t.getY())));
        java.util.Map<Dependency, List<Point2D>> routes = new java.util.IdentityHashMap<>();
        pkg.getUsesArrows().forEach(d -> routes.put(d, d.getLayoutRoute()));
        pkg.getExtendsArrows().forEach(d -> routes.put(d, d.getLayoutRoute()));
        return new LayoutState(positions, routes);
    }

    private void restoreLayout(LayoutState state)
    {
        applyingLayout = true;
        try
        {
            state.positions().forEach((target, point) -> target.setPos((int)point.getX(), (int)point.getY()));
            // Associated targets are normally moved by their owner; restore their exact saved locations last.
            state.positions().forEach((target, point) -> {
                if (target instanceof DependentTarget dependent && !dependent.isMoveable())
                    target.setPos((int)point.getX(), (int)point.getY());
            });
            state.routes().forEach(Dependency::setLayoutRoute);
        }
        finally { applyingLayout = false; }
        repaint();
    }

    public void undoArrangement()
    {
        if (!canUndoArrangement()) return;
        layoutRedo.push(captureLayout());
        restoreLayout(layoutUndo.pop());
    }

    public void redoArrangement()
    {
        if (!canRedoArrangement()) return;
        layoutUndo.push(captureLayout());
        restoreLayout(layoutRedo.pop());
    }

    public void arrangeDiagram()
    {
        arrangeDiagram(false);
    }

    private void arrangeDiagram(boolean newOnly)
    {
        if (layoutRunning) return;
        List<Target> vertices = new ArrayList<>(pkg.getVertices());
        vertices.sort(java.util.Comparator.comparing(Target::getIdentifierName));
        java.util.Map<Target, Target> owners = new java.util.IdentityHashMap<>();
        for (Target target : vertices)
            if (target instanceof DependentTarget dependent && dependent.getAssociation() != null)
                owners.put(dependent.getAssociation(), target);
        java.util.Map<String, Target> roots = new java.util.LinkedHashMap<>();
        java.util.Map<Target, String> nodeIds = new java.util.IdentityHashMap<>();
        java.util.Map<Target, Double> portOffsets = new java.util.IdentityHashMap<>();
        List<DiagramLayout.Node> nodes = new ArrayList<>();
        List<DiagramLayout.Box> fixed = new ArrayList<>();
        double top = 30;
        for (Target target : vertices)
        {
            if (owners.containsKey(target)) continue;
            String id = target.getIdentifierName();
            double width = Math.max(40, target.getWidth()), height = Math.max(20, target.getHeight());
            boolean associatedLocked = target instanceof DependentTarget dependent && dependent.getAssociation() != null
                && dependent.getAssociation().isLayoutLocked();
            if (!target.isMoveable() || target.isLayoutLocked() || associatedLocked || (newOnly && target.isLayoutPlaced()))
            {
                fixed.add(new DiagramLayout.Box(target.getX(), target.getY(), width, height));
                if (target instanceof DependentTarget dependent && dependent.getAssociation() != null)
                {
                    Target child = dependent.getAssociation();
                    fixed.add(new DiagramLayout.Box(child.getX(), child.getY(), child.getWidth(), child.getHeight()));
                }
                continue;
            }
            roots.put(id, target);
            nodeIds.put(target, id);
            portOffsets.put(target, width / 2);
            if (target instanceof DependentTarget dependent && dependent.getAssociation() != null)
            {
                nodeIds.put(dependent.getAssociation(), id);
                portOffsets.put(dependent.getAssociation(), width + 30 + width / 2);
                width = width * 2 + 30;
            }
            nodes.add(new DiagramLayout.Node(id, width, height));
        }
        if (nodes.isEmpty()) return;
        List<DiagramLayout.Edge> edges = new ArrayList<>();
        java.util.Map<String, Dependency> dependencies = new java.util.LinkedHashMap<>();
        for (Dependency dependency : getVisibleEdges())
        {
            if (dependency.to instanceof DependentTarget to && to.getAssociation() == dependency.from) continue;
            String from = nodeIds.get(dependency.from), to = nodeIds.get(dependency.to);
            if (from == null || to == null) continue;
            String id = Integer.toString(edges.size());
            edges.add(new DiagramLayout.Edge(id, from, to, portOffsets.get(dependency.from), portOffsets.get(dependency.to)));
            dependencies.put(id, dependency);
        }
        LayoutState before = captureLayout();
        long request = ++layoutRequest;
        layoutRunning = true;
        double offsetY = top;
        List<DiagramLayout.Node> nodeSnapshot = List.copyOf(nodes);
        List<DiagramLayout.Edge> edgeSnapshot = List.copyOf(edges);
        List<DiagramLayout.Box> obstacleSnapshot = List.copyOf(fixed);
        Utility.runBackground(() -> {
            try
            {
                DiagramLayout.Result result = DiagramLayout.arrange(nodeSnapshot, edgeSnapshot);
                java.util.Map<String, DiagramLayout.Point> desired = new java.util.LinkedHashMap<>();
                result.positions().forEach((id, point) -> desired.put(id, new DiagramLayout.Point(point.x() + 30, point.y() + offsetY)));
                java.util.Map<String, DiagramLayout.Point> placed = obstacleSnapshot.isEmpty() ? desired
                    : DiagramLayout.avoidObstacles(nodeSnapshot, desired, obstacleSnapshot);
                JavaFXUtil.runNowOrLater(() -> {
                    if (request != layoutRequest) return;
                    applyingLayout = true;
                    try
                    {
                        placed.forEach((id, position) -> {
                            roots.get(id).setPos((int)Math.round(position.x()), (int)Math.round(position.y()));
                            roots.get(id).setLayoutPlaced(true);
                        });
                        for (Dependency edge : pkg.getUsesArrows()) edge.setLayoutRoute(List.of());
                        for (Dependency edge : pkg.getExtendsArrows()) edge.setLayoutRoute(List.of());
                        if (obstacleSnapshot.isEmpty()) result.routes().forEach((id, route) -> dependencies.get(id).setLayoutRoute(route.stream()
                            .map(point -> new Point2D(point.x() + 30, point.y() + offsetY))
                            .collect(java.util.stream.Collectors.toList())));
                        if (layoutUndo.size() == 10) layoutUndo.removeLast();
                        layoutUndo.push(before);
                        layoutRedo.clear();
                    }
                    finally { applyingLayout = false; layoutRunning = false; }
                    repaint();
                });
            }
            catch (RuntimeException | LinkageError | java.util.ServiceConfigurationError ex)
            {
                JavaFXUtil.runNowOrLater(() -> {
                    if (request != layoutRequest) return;
                    layoutRunning = false;
                    bluej.utility.Debug.reportError("Diagram arrangement failed", ex);
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR, Config.getString("light.diagram.failed"),
                        javafx.scene.control.ButtonType.OK);
                    alert.initOwner(getFXWindow());
                    alert.showAndWait();
                });
            }
        });
    }


    /**
     * Add to the current selection
     * @param element the element to add
     */
    public void addToSelection(Target element)
    {
        selectionController.addToSelection(element);
    }

    /**
     * Toggles whether the given element is selected or not.
     */
    public void toggleSelection(Target element)
    {
        if (!element.isSelected())
            selectionController.addToSelection(element);
        else
            selectionController.removeFromSelection(element);
    }

    @OnThread(Tag.Any)
    public Package getPackage()
    {
        return pkg;
    }

    /**
     * Start our mouse listener. This is not done in the constructor, because we want 
     * to give others (the PkgMgrFrame) the chance to listen first.
     * 
     * NB: I'm not sure this is true now that we are in JavaFX.
     */
    public void startMouseListening()
    {
        // Needs to be filter because we want to dismiss the menu when
        // child nodes are clicked on:
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> showingMenu(null));
        addEventHandler(MouseEvent.MOUSE_DRAGGED, selectionController::mouseDragged);
        addEventHandler(MouseEvent.MOUSE_CLICKED, selectionController::mouseClicked);
        addEventHandler(MouseEvent.MOUSE_PRESSED, selectionController::mousePressed);
        addEventHandler(MouseEvent.MOUSE_RELEASED, selectionController::mouseReleased);
        JavaFXUtil.listenForContextMenu(this, this::popupMenu);
    }

    /**
     * Gets all the currently visible edges (depending on showUses/showExtends settings).
     */
    @OnThread(Tag.FXPlatform)
    public synchronized Collection<Dependency> getVisibleEdges()
    {
        List<Dependency> deps = new ArrayList<>();

        if (isShowUses())
            deps.addAll(pkg.getUsesArrows());
        if (isShowExtends())
            deps.addAll(pkg.getExtendsArrows());

        if (selectedConnectionsOnly) deps.removeIf(edge -> !edge.from.isSelected() && !edge.to.isSelected());

        return deps;
    }

    /**
     * Returns the {@link Dependency} with the specified <code>origin</code>,
     * <code>target</code> and <code>type</code> or <code>null</code> if there
     * is no such dependency.
     *
     * @param origin
     *            The origin of the dependency.
     * @param target
     *            The target of the dependency.
     * @param type
     *            The type of the dependency (there may be more than one
     *            dependencies with the same origin and target but different
     *            types).
     * @return The {@link Dependency} with the specified <code>origin</code>,
     *         <code>target</code> and <code>type</code> or <code>null</code> if
     *         there is no such dependency.
     */
    public synchronized Dependency getDependency(DependentTarget origin, DependentTarget target, Dependency.Type type)
    {
        List<? extends Dependency> dependencies;

        switch (type) {
            case USES :
                dependencies = pkg.getUsesArrows();
                break;
            case IMPLEMENTS :
            case EXTENDS :
                dependencies = pkg.getExtendsArrows();
                break;
            default :
                // If the type of the dependency is UNKNOWN, the requested
                // dependency does not exist anymore. In this case the method
                // returns null.
                return null;
        }

        for (Dependency dependency : dependencies) {
            DependentTarget from = dependency.getFrom();
            DependentTarget to = dependency.getTo();

            if (from.equals(origin) && to.equals(target)) {
                return dependency;
            }
        }

        return null;
    }

    public void setShowUses(boolean state)
    {
        showUses.set(state);
    }

    public void setShowExtends(boolean state)
    {
        showExtends.set(state);
    }

    public boolean isShowUses()
    {
        return showUses.get();
    }

    public boolean isShowExtends()
    {
        return showExtends.get();
    }

    /**
     * Clears the state: stops creating a new extends arrow.
     */
    public void clearState()
    {
        if (creatingExtends)
            stopNewInherits();
    }

    /**
     * Called to start creating a new inherits arrow.
     */
    public void doNewInherits()
    {
        if (!creatingExtends) {
            arrowCreationTip = new Label(Config.getString("pkgmgr.chooseInhFrom"));
            JavaFXUtil.addStyleClass(arrowCreationTip, "pmf-create-extends-tip");
            overlay.addMouseTrackingOverlay(arrowCreationTip, false, new ReadOnlyDoubleWrapper(5.0), arrowCreationTip.heightProperty().negate().add(-5.0));
            creatingExtends = true;
            extendsSubClass = null;
            JavaFXUtil.setPseudoclass("bj-drawing-extends", true, this);
            for (Target t : pkg.getVertices())
                t.setCreatingExtends(true);
            repaint();
        }

    }

    /**
     * Stops creating a new inherits arrow.
     */
    private void stopNewInherits()
    {
        overlay.removeMouseListener(this);
        overlay.remove(arrowCreationTip);
        arrowCreationTip = null;
        creatingExtends = false;
        extendsSubClass = null;
        extendsSuperClassHover = null;
        JavaFXUtil.setPseudoclass("bj-drawing-extends", false, this);
        for (Target t : pkg.getVertices())
            t.setCreatingExtends(false);
        repaint();
    }

    public boolean doRemoveDependency()
    {
        return false;
    }

    /**
     * Selects the given single target, and no others.
     */
    public void selectOnly(Target target)
    {
        selectionController.selectOnly(target);
    }


    /**
     * Modify the given point to be one of the defined grid points.
     *
     * @param x  The original point's x coordinate.
     * @return      The x coordinate of a point close to the original which is on the grid.
     */
    public int snapToGrid(int x)
    {
        int steps = (int)Math.round((double)x / PackageEditor.GRID_SIZE);
        int new_x = steps * PackageEditor.GRID_SIZE;//new x-coor w/ respect to
        // grid
        return new_x;
    }

    /**
     * Started a move operation on the selection
     */
    public void startedMove()
    {
        for (Target element : selectionController.getSelection())
        {
            if (element.isMoveable() && !element.isLayoutLocked())
                element.savePreMovePosition();
        }
    }

    /**
     * Performing a move operation: move by the given amounts
     * from the last time we called startedMove
     */
    public void moveBy(int deltaX, int deltaY)
    {
        for (Target element : selectionController.getSelection())
        {
            if (element.isMoveable() && !element.isLayoutLocked())
            {
                element.setPos(Math.max(0, element.getPreMoveX() + deltaX), Math.max(0, element.getPreMoveY() + deltaY));
                element.setLayoutPlaced(true);
            }
        }
    }

    /**
     * Started a move operation on the selection
     */
    @OnThread(Tag.FXPlatform)
    public void startedResize()
    {
        for (Target element : selectionController.getSelection())
        {
            if (element.isResizable() && !element.isLayoutLocked())
            {
                element.savePreResize();
                JavaFXUtil.setPseudoclass("bj-resizing", true, element.getNode());
            }
        }
    }

    /**
     * Performing a resize operation: resize by the given amounts
     * from the last time we called startedResize
     */
    public void resizeBy(int deltaWidth, int deltaHeight)
    {
        for (Target element : selectionController.getSelection())
        {
            if (element.isResizable() && !element.isLayoutLocked())
                element.setSize(Math.max(40, element.getPreResizeWidth() + deltaWidth), Math.max(20, element.getPreResizeHeight() + deltaHeight));
        }
    }

    /**
     * Finishes a resize operation.
     */
    public void endResize()
    {
        for (Target element : selectionController.getSelection())
        {
            if (element.isResizable())
                JavaFXUtil.setPseudoclass("bj-resizing", false, element.getNode());
        }
    }

    /**
     * Navigates the diagram according to the given key press.
     */
    public void navigate(KeyEvent event)
    {
        selectionController.navigate(event);
    }

    /**
     * Select everything in the diagram.
     */
    public void selectAll()
    {
        selectionController.selectAll();
    }

    /**
     * The given target has been clicked.  If we are creating a new extends
     * arrow then select this target and return true.  If we are not creating
     * a new extends arrow, return false.
     */
    public boolean clickForExtends(Target target, double sceneX, double sceneY)
    {
        if (creatingExtends && target instanceof ClassTarget)
        {
            if (extendsSubClass == null)
            {
                extendsSubClass = (ClassTarget)target;
                arrowCreationTip.setText(Config.getString("pkgmgr.chooseInhTo"));
                overlay.addMouseListener(this); 
                newExtendsDestX = sceneX;
                newExtendsDestY = sceneY;
                repaint();
            }
            else
            {
                // Finished; we can actually add the dependency
                // Take a copy because we're going to null it:
                ClassTarget subClassFinal = this.extendsSubClass;
                if (!subClassFinal.hasSourceCode())
                {
                    DialogManager.showErrorFX(pmf.getWindow(),
                            "no-extends-arrow-from-no-source-class");
                    clearState();
                    return false;
                }
                ClassTarget superClass = (ClassTarget)target;
                if (subClassFinal.isInterface())
                {
                    if (superClass.isInterface())
                        pkg.userAddExtendsInterfaceDependency(subClassFinal, superClass);
                    // TODO else give an error about why this won't work?
                }
                else
                {
                    if (superClass.isInterface())
                        pkg.userAddImplementsClassDependency(subClassFinal, superClass);
                    else
                        pkg.userAddExtendsClassDependency(subClassFinal, superClass);
                }
                pkg.compile(subClassFinal, CompileReason.MODIFIED_EXTENDS, CompileType.INDIRECT_USER_COMPILE);
                
                stopNewInherits();
            }
        }
        return false;
    }

    @Override
    public void mouseMoved(double localX, double localY)
    {
        newExtendsDestX = localX;
        newExtendsDestY = localY;
        repaint();
    }
    
    @Override
    public Stage getStage()
    {
        return pmf.getWindow();
    }
    
    @Override
    public void callStaticMethodOrConstructor(CallableView view)
    {
        pmf.callStaticMethodOrConstructor(view);
    }

    @Override
    public void highlightObject(DebuggerObject currentObject)
    {
        pmf.getObjectBench().highlightObject(currentObject);
    }

    @Override
    public Node getPkgMgrPaneNode()
    {
        return this;
    }

    /**
     * The given String has been typed while focus was on a target in the class diagram. 
     */
    public void typedAtTarget(String entry)
    {
        long time = System.currentTimeMillis();
        // If it's been more than a second since our previous entry, blank it and start again:
        if (time - lastKeypress > 1000)
        {
            currentSearch = "";
        }
        currentSearch += entry;
        lastKeypress = time;
        // Now do the search and update our selection with the first found target:
        pkg.findTargetsBeginningWith(currentSearch)
            .sorted(Comparator.comparing(t -> t.getIdentifierName(), String.CASE_INSENSITIVE_ORDER))
            .findFirst()
            .ifPresent(t -> selectOnly(t));
        // If nothing is found (either invalid search or the user kept typing beyond what is needed, do nothing)
    }

    /**
     * An AnchorPane with extra space at the right and bottom.
     * There's no API/CSS for this, so we override the size computations
     * and add the spacing to the parent's return value.
     */
    @OnThread(Tag.FX)
    private static class AnchorPaneExtraSpacing extends AnchorPane
    {
        public static final double EXTRA_SPACE = 20.0;

        @Override
        protected double computePrefWidth(double height)
        {
            return super.computePrefWidth(height) + EXTRA_SPACE;
        }

        @Override
        protected double computePrefHeight(double width)
        {
            return super.computePrefHeight(width) + EXTRA_SPACE;
        }

        @Override
        protected double computeMinHeight(double width)
        {
            return super.computeMinHeight(width) + EXTRA_SPACE;
        }

        @Override
        protected double computeMinWidth(double height)
        {
            return super.computeMinWidth(height) + EXTRA_SPACE;
        }
    }
}
