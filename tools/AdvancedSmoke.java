/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Window;

public class AdvancedSmoke extends BootSmoke
{
    static String label(String key) throws Exception { return (String)loader.loadClass("bluej.Config").getMethod("getString",String.class).invoke(null,key); }
    static MenuItem byText(List<? extends MenuItem> items, String text) {
        for (MenuItem item : items) { if (text.equals(item.getText())) return item; if (item instanceof Menu menu) { MenuItem found = byText(menu.getItems(),text); if (found != null) return found; } }
        return null;
    }
    static DialogPane dialog(String id) throws Exception {
        for (int i=0;i<100;i++) {
            DialogPane pane=fx(()->{for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){Node n=w.getScene().getRoot().lookup("#"+id);if(n instanceof DialogPane d)return d;}return null;});
            if(pane!=null)return pane;Thread.sleep(100);
        }
        throw new AssertionError("Dialog missing: "+id);
    }
    static void editorCommand(Object javaEditor,String key) throws Exception {
        fx(()->{ List<Menu> menus=(List<Menu>)call(javaEditor,"getFXMenu");MenuItem item=byText(menus,label(key));if(item==null)throw new AssertionError("Missing editor command "+key);Platform.runLater(item::fire);return null;});
    }
    static void verify(Object javaEditor,Object frame,List<?> vertices) throws Exception {
        Object pane=fx(()->call(javaEditor,"getSourcePane"));Object document=fx(()->call(pane,"getDocument"));
        String original=fx(()->(String)call(document,"getFullContent"));
        fx(()->{Object actions=call(javaEditor,"getActions");Object format=actions.getClass().getMethod("getActionByName",String.class).invoke(actions,"autoindent");loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed",boolean.class).invoke(format,false);return null;});
        for(int i=0;i<150;i++){
            boolean busy=fx(()->{Field formatting=javaEditor.getClass().getDeclaredField("formatting");formatting.setAccessible(true);return formatting.getBoolean(javaEditor);});
            if(!busy)break;if(i==149)throw new AssertionError("Formatter timed out");Thread.sleep(100);
        }
        fx(()->{
            String formatted=(String)call(document,"getFullContent");if(formatted.lines().count()<=original.lines().count())throw new AssertionError("One-line methods were not expanded");
            snapshot((Node)javaEditor,"formatted-java.png");Object actions=call(javaEditor,"getActions");Object undo=actions.getClass().getMethod("getActionByName",String.class).invoke(actions,"undo");loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed",boolean.class).invoke(undo,false);
            if(!original.equals(call(document,"getFullContent")))throw new AssertionError("Formatting is not a single undo");return null;
        });
        editorCommand(javaEditor,"light.methods");
        DialogPane outline=null;
        for(int i=0;i<100&&outline==null;i++){outline=fx(()->{for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot().lookup("#light-method-list")!=null)return (DialogPane)w.getScene().getRoot();return null;});if(outline==null)Thread.sleep(100);}
        if(outline==null)throw new AssertionError("Methods dialog missing");final DialogPane methods=outline;
        for(int i=0;i<100 && fx(()->((ListView<?>)methods.lookup("#light-method-list")).getItems().isEmpty());i++)Thread.sleep(100);
        fx(()->{
            ListView<?> list=(ListView<?>)methods.lookup("#light-method-list");if(list.getItems().isEmpty())throw new AssertionError("No methods");
            ((TextField)methods.lookup("#light-method-search")).setText("answer");if(list.getItems().isEmpty())throw new AssertionError("Method search failed");
            snapshot(methods,"methods.png");
            ButtonType go=methods.getButtonTypes().stream().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();((Button)methods.lookupButton(go)).fire();return null;
        });
        int methodPosition=fx(()->number(pane,"getCaretPosition"));
        fx(()->{var menus=(List<Menu>)call(javaEditor,"getFXMenu");byText(menus,label("light.nav.back")).fire();byText(menus,label("light.nav.forward")).fire();if(number(pane,"getCaretPosition")!=methodPosition)throw new AssertionError("Navigation history failed");return null;});
        Thread.sleep(1000);editorCommand(javaEditor,"light.history");DialogPane history=dialog("light-history-dialog");
        for(int i=0;i<100 && fx(()->((Button)history.lookupButton(history.getButtonTypes().getFirst())).isDisabled());i++)Thread.sleep(100);
        fx(()->{ if(((ListView<?>)history.lookup("#light-history-list")).getItems().isEmpty())throw new AssertionError("No local history");snapshot(history,"history.png");((Button)history.lookupButton(ButtonType.CANCEL)).fire();return null;});
        fx(()->{javaEditor.getClass().getMethod("restoreHistoryText",String.class).invoke(javaEditor,"// restore probe\n"+original);if(!((String)call(document,"getFullContent")).startsWith("// restore probe"))throw new AssertionError("Restore failed");
            javaEditor.getClass().getMethod("restoreHistoryText",String.class).invoke(javaEditor,original);call(javaEditor,"save");return null;});
        editorCommand(javaEditor,"light.errors");DialogPane errors=dialog("light-errors-dialog");
        fx(()->{snapshot(errors,"errors.png");((Button)errors.lookupButton(ButtonType.CLOSE)).fire();return null;});
        Object locked=vertices.stream().filter(v->v.toString().equals("School")).findFirst().orElseThrow();
        int[] position=fx(()->{locked.getClass().getMethod("setLayoutLocked",boolean.class).invoke(locked,true);return new int[]{number(locked,"getX"),number(locked,"getY")};});
        fx(()->{call(editor,"arrangeDiagram");return null;});
        for(int i=0;i<200&&fx(()->(Boolean)call(editor,"isArranging"));i++)Thread.sleep(100);
        fx(()->{if(number(locked,"getX")!=position[0]||number(locked,"getY")!=position[1])throw new AssertionError("Locked class moved");assertNoOverlap(vertices);call(editor,"undoArrangement");call(editor,"redoArrangement");return null;});
        Map<Object,int[]> fixed=new IdentityHashMap<>();
        fx(()->{for(Object target:vertices)fixed.put(target,new int[]{number(target,"getX"),number(target,"getY")});Method arrange=editor.getClass().getDeclaredMethod("arrangeDiagram",boolean.class);arrange.setAccessible(true);arrange.invoke(editor,true);return null;});
        for(int i=0;i<200&&fx(()->(Boolean)call(editor,"isArranging"));i++)Thread.sleep(100);
        fx(()->{for(Object target:vertices)if(number(target,"getX")!=fixed.get(target)[0]||number(target,"getY")!=fixed.get(target)[1])throw new AssertionError("Established class moved in new-only layout");return null;});
        Path project=((java.io.File)fx(()->call(pkg,"getPath"))).toPath();
        Files.copy(Path.of("tools/Exercises/Sum/Sum.java"),project.resolve("Sum.java"));Files.copy(Path.of("tools/Exercises/Sum/SumTest.java"),project.resolve("SumTest.java"));Files.copy(Path.of("tools/Exercises/Sum/exercise.properties"),project.resolve("exercise.properties"));
        fx(()->{MenuBar bar=(MenuBar)((Node)editor).getScene().getRoot().lookup(".menu-bar");MenuItem show=byText(bar.getMenus(),label("light.exercise.view"));if(show==null)throw new AssertionError("Exercises menu missing");Platform.runLater(show::fire);return null;});
        DialogPane exercise=dialog("light-exercise-dialog");
        fx(()->{Platform.runLater(()->((Button)exercise.lookup("#light-exercise-run")).fire());return null;});DialogPane trust=dialog("light-exercise-trust");
        fx(()->{((Button)trust.lookupButton(ButtonType.OK)).fire();return null;});
        for(int i=0;i<500 && fx(()->((Button)exercise.lookup("#light-exercise-export")).isDisabled());i++)Thread.sleep(100);
        fx(()->{ListView<?> list=(ListView<?>)exercise.lookup("#light-exercise-results");if(list.getItems().size()!=4)throw new AssertionError("Unexpected feedback: "+list.getItems());
            long failed=list.getItems().stream().filter(result->{try{return call(result,"status").equals("FAILED");}catch(Exception ex){throw new RuntimeException(ex);}}).count();if(failed!=2)throw new AssertionError("Expected two unsolved sample tests");
            ((TabPane)exercise.lookup(".tab-pane")).getSelectionModel().select(1);snapshot(exercise,"exercise-feedback.png");((Button)exercise.lookupButton(ButtonType.CLOSE)).fire();return null;});
        System.out.println("ADVANCED_OK formatter=true formatUndo=true methods=true navigation=true localHistory=true restore=true explanations=true diagramLocks=true newOnly=true exerciseFeedback=true");
    }
}
