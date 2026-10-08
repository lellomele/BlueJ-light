/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
import bluej.Boot;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.shape.Path;
import javafx.scene.control.IndexRange;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public class AnnotationRenderingCheck {
    static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(object);
    }
    static Object invoke(Object object, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(object, arguments);
    }
    public static void main(String[] args) {
        Thread worker = new Thread(() -> {
            try {
                Node manager = null;
                for (int i=0; i<300 && manager==null; i++) {
                    try { manager=ScrollPerformance.fx(() -> ScrollPerformance.find("bluej.pkgmgr.PackageEditor")); }
                    catch (IllegalStateException ignored) { }
                    if (manager==null) Thread.sleep(100);
                }
                if(manager==null) throw new AssertionError("Project not opened");
                Node packageEditor=manager;
                Object editor=ScrollPerformance.fx(() -> {
                    Object pkg=ScrollPerformance.call(packageEditor,"getPackage");
                    Object target=((Collection<?>)ScrollPerformance.call(pkg,"getVertices")).stream()
                        .filter(t->t.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")).findFirst().orElseThrow();
                    target.getClass().getMethod("doubleClick",boolean.class).invoke(target,false);
                    return ScrollPerformance.call(target,"getEditor");
                });
                CompletableFuture<Void> parsed=new CompletableFuture<>();
                ScrollPerformance.fx(() -> {
                    Object view=ScrollPerformance.call(editor,"getSourceDocument");
                    Class<?> runnable=editor.getClass().getClassLoader().loadClass("bluej.utility.javafx.FXPlatformRunnable");
                    view.getClass().getMethod("whenParsed",runnable).invoke(view,
                        Proxy.newProxyInstance(runnable.getClassLoader(),new Class<?>[]{runnable},(o,m,v)->{parsed.complete(null);return null;}));
                    return null;
                });
                parsed.get(90,TimeUnit.SECONDS); Thread.sleep(1000);
                Object pane=ScrollPerformance.call(editor,"getSourcePane");
                Object document=ScrollPerformance.call(pane,"getDocument");
                int line=ScrollPerformance.fx(() -> {
                    String content=(String)ScrollPerformance.call(document,"getFullContent");
                    int position=content.indexOf("total",content.length()/2);
                    if(position<0) throw new AssertionError("Fixture must contain total");
                    int lineIndex=(Integer)document.getClass().getMethod("getLineFromPosition",int.class).invoke(document,position);
                    // Inject annotation state to exercise native rendering independently of the debugger.
                    ((BitSet)field(editor,"breakpoints")).set(lineIndex);
                    Object navigator=invoke(editor,"doFind",new Class<?>[]{String.class,boolean.class},"total",false);
                    invoke(navigator,"highlightAll",new Class<?>[0]);
                    Class<?> query=editor.getClass().getClassLoader().loadClass("bluej.editor.flow.FlowEditorPane$ErrorQuery");
                    pane.getClass().getMethod("setErrorQuery",query).invoke(pane,
                        Proxy.newProxyInstance(query.getClassLoader(),new Class<?>[]{query},(o,m,v)->List.of(new IndexRange(position,position+5))));
                    return lineIndex;
                });
                for(int destination:new int[]{0,line+200,line}) {
                    ScrollPerformance.fx(() -> {
                        pane.getClass().getMethod("scrollTo",int.class).invoke(pane,destination);
                        Parent root=((Node)editor).getScene().getRoot(); root.applyCss(); root.layout(); return null;
                    });
                    Thread.sleep(350);
                }
                String result=ScrollPerformance.fx(() -> {
                    Parent root=((Node)editor).getScene().getRoot();
                    boolean find=root.lookupAll(".flow-find-result").stream().map(Path.class::cast)
                        .anyMatch(p->p.isVisible()&&!p.getElements().isEmpty());
                    boolean error=false,breakpoint=false;
                    for(Node node:root.lookupAll(".text-line")) {
                        Path underline=(Path)field(node,"errorUnderlineShape");
                        error|=underline.isVisible()&&!underline.getElements().isEmpty();
                    }
                    for(Node node:root.lookupAll(".margin-and-text-line"))
                        breakpoint|=((Node)field(node,"breakpointIcon")).getOpacity()==1.0;
                    ImageIO.write(SwingFXUtils.fromFXImage(root.getScene().snapshot(null),null),"png",new File(args[2]));
                    if(!find||!error||!breakpoint) throw new AssertionError("find="+find+" error="+error+" breakpoint="+breakpoint);
                    return "ANNOTATIONS_OK find=true errorUnderline=true breakpoint=true version="+Boot.BLUEJ_VERSION;
                });
                System.out.println(result);
                ClassLoader loader=editor.getClass().getClassLoader();
                ScrollPerformance.fx(() -> {loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);return null;});
            } catch(Throwable failure) {failure.printStackTrace();System.exit(2);}
        },"Annotation rendering verification");
        worker.setDaemon(true);worker.start();
        Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }
}
