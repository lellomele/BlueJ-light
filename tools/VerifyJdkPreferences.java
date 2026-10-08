/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GPLv2 with Classpath Exception. */
import bluej.Boot;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Window;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class VerifyJdkPreferences {
    static <T> T fx(Callable<T> task) throws Exception {
        CompletableFuture<T> result=new CompletableFuture<>();
        Platform.runLater(()->{try{result.complete(task.call());}catch(Throwable e){result.completeExceptionally(e);}});
        return result.get(20,TimeUnit.SECONDS);
    }
    static Object call(Object object,String method)throws Exception{return object.getClass().getMethod(method).invoke(object);}
    static Node find(Node node,String name){
        if(node.getClass().getName().equals(name))return node;
        if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable()){Node found=find(child,name);if(found!=null)return found;}
        return null;
    }
    public static void main(String[] args){
        Thread worker=new Thread(()->{
            try{
                Node manager=null;
                for(int i=0;i<300&&manager==null;i++){
                    try{manager=fx(()->{
                        for(Window window:Window.getWindows())if(window.isShowing()&&window.getScene()!=null){
                            Node found=find(window.getScene().getRoot(),"bluej.pkgmgr.PackageEditor");if(found!=null)return found;
                        }return null;
                    });}catch(IllegalStateException ignored){}
                    if(manager==null)Thread.sleep(100);
                }
                if(manager==null)throw new AssertionError("Project not opened");
                ClassLoader loader=manager.getClass().getClassLoader();
                Path actual=Path.of(System.getProperty("java.home")).toRealPath();
                Path expected=Path.of(args[3]).toRealPath();
                if(!actual.equals(expected))throw new AssertionError("Expected "+expected+" but JVM uses "+actual);
                if(!args[2].equals("@check")){
                    Node editor=manager;
                    fx(()->{
                        Object project=call(call(editor,"getPackage"),"getProject");
                        Class<?> prefs=loader.loadClass("bluej.prefmgr.PrefMgrDialog");
                        prefs.getMethod("showDialog",loader.loadClass("bluej.pkgmgr.Project"),int.class).invoke(null,project,6);
                        return null;
                    });
                    Thread.sleep(1200);
                    fx(()->{
                        DialogPane pane=null;
                        for(Window window:Window.getWindows())if(window.getScene()!=null&&window.isShowing()){
                            Node found=window.getScene().getRoot().lookup(".prefmgr-dialog-pane");if(found instanceof DialogPane dialog)pane=dialog;
                        }
                        if(pane==null)throw new AssertionError("Preferences not shown");
                        Node panel=pane.lookup("#light-jdk-panel");
                        if(panel==null)throw new AssertionError("JDK preference section missing");
                        Class<?> infoClass=loader.loadClass("bluej.prefmgr.JdkInfo");
                        Object info=((Optional<?>)infoClass.getMethod("inspect",Path.class).invoke(null,Path.of(args[4]))).orElseThrow();
                        ListView<Object> list=(ListView<Object>)panel.lookup("#light-jdk-list");
                        if(!list.getItems().contains(info))list.getItems().add(info);
                        list.getSelectionModel().select(info);
                        ((ComboBox<?>)panel.lookup("#light-jdk-mode")).getSelectionModel().select(1);
                        pane.getScene().getRoot().applyCss();pane.getScene().getRoot().layout();
                        ImageIO.write(SwingFXUtils.fromFXImage(pane.getScene().snapshot(null),null),"png",Path.of(args[2]).toFile());
                        ((Button)pane.lookupButton(ButtonType.OK)).fire();
                        return null;
                    });
                    List<String> saved=Files.readAllLines(Path.of(args[1],"bluej-light","jdk-selection.txt"));
                    if(!saved.getFirst().equals("external")||!Path.of(saved.get(1)).toRealPath().equals(Path.of(args[4]).toRealPath()))
                        throw new AssertionError("Preference not persisted: "+saved);
                }
                System.out.println("JDK_PREFERENCES_OK actual="+actual+" mode="+args[2]);
                fx(()->{loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);return null;});
            }catch(Throwable failure){failure.printStackTrace();System.exit(2);}
        },"JDK preferences verification");
        worker.setDaemon(true);worker.start();
        Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }
}
