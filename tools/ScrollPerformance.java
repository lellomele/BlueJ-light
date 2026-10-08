/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
import bluej.Boot;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollBar;
import javafx.geometry.Orientation;
import javafx.stage.Window;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
import jdk.jfr.Recording;

public class ScrollPerformance {
    static Path report;
    static Object editor, pane, document;
    static Recording recording;
    static com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    public static void main(String[] args) {
        report = Path.of(args[2]);
        Thread worker = new Thread(() -> {
            try {
                Files.createDirectories(report);
                Node packageEditor = null;
                for (int i = 0; i < 300 && packageEditor == null; i++) {
                    try { packageEditor = fx(() -> find("bluej.pkgmgr.PackageEditor")); } catch (IllegalStateException ex) { }
                    if (packageEditor == null) Thread.sleep(100);
                }
                if (packageEditor == null) throw new AssertionError("Project did not open");
                Node manager = packageEditor;
                Thread.sleep(2000);
                long start = System.nanoTime();
                fx(() -> {
                    Object pkg = call(manager, "getPackage");
                    Object target = ((Collection<?>)call(pkg,"getVertices")).stream()
                        .filter(t -> t.getClass().getName().equals("bluej.pkgmgr.target.ClassTarget")).findFirst().orElseThrow();
                    target.getClass().getMethod("doubleClick",boolean.class).invoke(target,false);
                    editor=call(target,"getEditor"); pane=call(editor,"getSourcePane"); document=call(pane,"getDocument");
                    return null;
                });
                CompletableFuture<Void> parsed = new CompletableFuture<>();
                fx(() -> {
                    Object syntax=call(editor,"getSourceDocument");
                    Class<?> runnable=syntax.getClass().getClassLoader().loadClass("bluej.utility.javafx.FXPlatformRunnable");
                    Object callback=Proxy.newProxyInstance(runnable.getClassLoader(),new Class<?>[]{runnable},(object,method,values)->{parsed.complete(null);return null;});
                    syntax.getClass().getMethod("whenParsed",runnable).invoke(syntax,callback);
                    return null;
                });
                parsed.get(90,TimeUnit.SECONDS);
                double readyMs=(System.nanoTime()-start)/1e6;
                Thread.sleep(1500);
                recording = new Recording(jdk.jfr.Configuration.getConfiguration("profile"));
                recording.start();
                for(String phase : List.of("continuous","jumps")) {
                    CompletableFuture<String> metrics = new CompletableFuture<>();
                    fx(() -> { runPhase(phase,readyMs,metrics);return null; });
                    Files.writeString(report.resolve(phase+".json"),metrics.get(90,TimeUnit.SECONDS));
                    Thread.sleep(500);
                }
                fx(() -> {
                    Node root=((Node)editor).getScene().getRoot();
                    ImageIO.write(SwingFXUtils.fromFXImage(root.getScene().snapshot(null),null),"png",report.resolve("editor.png").toFile());
                    return null;
                });
                recording.stop(); recording.dump(report.resolve("scroll.jfr")); recording.close();
                System.out.println("PERFORMANCE_OK " + report);
                ClassLoader loader=editor.getClass().getClassLoader();
                fx(() -> {loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);return null;});
            } catch(Throwable failure){failure.printStackTrace();System.exit(2);}
        },"Scroll measurement");
        worker.setDaemon(true); worker.start();
        Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }
    static void runPhase(String phase,double readyMs,CompletableFuture<String> result) throws Exception {
        Parent root=((Node)editor).getScene().getRoot();
        ScrollBar bar=((Node)pane).lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance)
            .map(ScrollBar.class::cast).filter(b->b.getOrientation()==Orientation.VERTICAL).findFirst().orElseThrow();
        int count=((Number)call(document,"getLineCount")).intValue();
        bar.setValue(0);
        new AnimationTimer() {
            int frame=0; long previous=0; List<Double> intervals=new ArrayList<>(),events=new ArrayList<>(),layouts=new ArrayList<>(),allocations=new ArrayList<>();
            @Override public void handle(long now) {
                try {
                    if(frame >= 60 && previous != 0) intervals.add((now-previous)/1e6);
                    previous=now;
                    long id=Thread.currentThread().threadId(), allocated=threads.getThreadAllocatedBytes(id);
                    long begin=System.nanoTime();
                    double value=phase.equals("continuous") ? (frame*6.0)%Math.max(1,bar.getMax()) : ((frame*0.61803398875)%1)*bar.getMax();
                    bar.setValue(value);
                    long changed=System.nanoTime();
                    root.applyCss(); root.layout();
                    long laidOut=System.nanoTime();
                    if(frame>=60) {events.add((changed-begin)/1e6);layouts.add((laidOut-changed)/1e6);allocations.add((threads.getThreadAllocatedBytes(id)-allocated)/1024.0);}
                    if(++frame==240) {
                        stop();
                        int visible=root.lookupAll(".text-line").size();
                        result.complete("{\"phase\":\""+phase+"\",\"lines\":"+count+",\"visibleNodes\":"+visible+",\"readyMs\":"+readyMs
                            +",\"eventMedianMs\":"+p(events,.5)+",\"eventP95Ms\":"+p(events,.95)+",\"layoutMedianMs\":"+p(layouts,.5)
                            +",\"layoutP95Ms\":"+p(layouts,.95)+",\"frameMedianMs\":"+p(intervals,.5)+",\"frameP95Ms\":"+p(intervals,.95)
                            +",\"framesOver33ms\":"+intervals.stream().filter(v->v>33.4).count()+",\"allocationMedianKiB\":"+p(allocations,.5)+"}\n");
                    }
                } catch(Throwable error){stop();result.completeExceptionally(error);}
            }
        }.start();
    }
    static double p(List<Double> values,double fraction){List<Double> sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*fraction)));}
    static Object call(Object target,String name)throws Exception{return target.getClass().getMethod(name).invoke(target);}
    static Node find(String name){for(Window window:Window.getWindows())if(window.isShowing()&&window.getScene()!=null){Node found=find(window.getScene().getRoot(),name);if(found!=null)return found;}return null;}
    static Node find(Node node,String name){if(node.getClass().getName().equals(name))return node;if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable()){Node found=find(child,name);if(found!=null)return found;}return null;}
    static <T>T fx(Callable<T> work)throws Exception{CompletableFuture<T> value=new CompletableFuture<>();Platform.runLater(()->{try{value.complete(work.call());}catch(Throwable error){value.completeExceptionally(error);}});return value.get(60,TimeUnit.SECONDS);}
}
