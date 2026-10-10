/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Window;

public final class ErrorStressSmoke extends AdvancedSmoke
{
    static Object sourceEditor, sourcePane, document, target, reason, explicit;
    static final Queue<Throwable> uiFailures = new ConcurrentLinkedQueue<>();
    public static void main(String[] args)
    {
        Thread test = new Thread(() -> {
            try
            {
                for (int i=0;i<300&&editor==null;i++) { try {editor=fx(BootSmoke::findEditor);}catch(IllegalStateException ignored){} if(editor==null)Thread.sleep(100); }
                if(editor==null)throw new AssertionError("Project did not open");
                loader=editor.getClass().getClassLoader();pkg=call(editor,"getPackage");
                fx(()->{Thread.currentThread().setUncaughtExceptionHandler((thread,error)->uiFailures.add(error));return null;});
                target=fx(()->((Collection<?>)call(pkg,"getVertices")).stream().filter(value->value.toString().equals("School")).findFirst().orElseThrow());
                sourceEditor=fx(()->{target.getClass().getMethod("doubleClick",boolean.class).invoke(target,false);return call(target,"getEditor");});
                sourcePane=fx(()->call(sourceEditor,"getSourcePane"));document=fx(()->call(sourcePane,"getDocument"));
                String original=fx(()->(String)call(document,"getFullContent"));String invalid=original.replace("int answer()","answer()");
                if(original.equals(invalid))throw new AssertionError("Fixture missing return type");
                reason=Enum.valueOf((Class)loader.loadClass("bluej.compiler.CompileReason"),"USER");
                explicit=Enum.valueOf((Class)loader.loadClass("bluej.compiler.CompileType"),"EXPLICIT_USER_COMPILE");
                for(int round=0;round<16;round++)
                {
                    replace(invalid); compile(); waitForMissingType();
                    int position=invalid.indexOf("answer()");
                    fx(()->{
                        for(int offset=-1;offset<8;offset++)sourceEditor.getClass().getMethod("showErrorPopupForCaretPos",int.class,boolean.class).invoke(sourceEditor,position+offset,true);
                        sourceEditor.getClass().getMethod("showErrorPopupForCaretPos",int.class,boolean.class).invoke(sourceEditor,position,true);
                        return null;
                    });
                    Thread.sleep(100);
                    fx(()->{
                        Label explanation=null;for(Window window:Window.getWindows())if(window.isShowing()&&window.getScene()!=null){Node node=window.getScene().getRoot().lookup("#light-error-explanation");if(node instanceof Label label)explanation=label;}
                        if(explanation==null||!explanation.getText().contains("public int answer()"))throw new AssertionError("Missing helpful hover explanation");
                        if(explanation!=null&&!explanation.getText().contains("mancante"))throw new AssertionError("Italian explanation missing");
                        return null;
                    });
                    if(round==0)
                    {
                        editorCommand(sourceEditor,"light.errors");DialogPane pane=dialog("light-errors-dialog");
                        fx(()->{TextArea details=(TextArea)pane.lookup("#light-errors-explanation");if(!details.getText().contains("public int answer()"))throw new AssertionError("Explanation still generic");
                            if(((Node)sourceEditor).getScene().getRoot().isDisabled())throw new AssertionError("Explanation disables editing");snapshot(pane,"missing-return-type.png");return null;});
                        replace("\n");
                        fx(()->{Object actions=call(sourceEditor,"getActions");Object undo=actions.getClass().getMethod("getActionByName",String.class).invoke(actions,"undo");loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed",boolean.class).invoke(undo,false);return null;});
                        fx(()->{((Button)pane.lookupButton(ButtonType.CLOSE)).fire();return null;});
                    }
                    else if(round%3==0)replace("\n");
                    replace(original); compile();
                    for(int i=0;i<300;i++){if(fx(()->!(Boolean)call(target,"isQueued")&&(Boolean)call(target,"isCompiled")))break;if(i==299)throw new AssertionError("Repair compilation stalled");Thread.sleep(100);}
                    if(!uiFailures.isEmpty())throw new AssertionError("UI exception while editing",uiFailures.peek());
                }
                if(!original.equals(fx(()->call(document,"getFullContent"))))throw new AssertionError("Source not restored");
                Path preferences=((java.io.File)fx(()->loader.loadClass("bluej.Config").getMethod("getUserConfigDir").invoke(null))).toPath();
                fx(()->{Platform.runLater(()->{try{Thread.sleep(11500);}catch(InterruptedException error){Thread.currentThread().interrupt();}});return null;});
                Thread.sleep(12500);
                Path stallLog=preferences.resolve("bluej-ui-stall.log");
                if(!Files.isRegularFile(stallLog)||!Files.readString(stallLog).contains("UI heartbeat"))throw new AssertionError("Local stall diagnostics missing");
                if(Files.readString(stallLog).contains("public class School"))throw new AssertionError("Source text leaked into stall diagnostics");
                System.out.println("ERROR_STRESS_OK rounds=16 hover=true italian=true modeless=true emptyBuffer=true undo=true compileRepair=true uiExceptions=0 localStallLog=true");
                fx(()->{loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);return null;});
            }
            catch(Throwable error){error.printStackTrace();System.exit(2);}
        },"Editor error stress verification");test.setDaemon(true);test.start();
        Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }
    static void replace(String text) throws Exception
    { fx(()->{document.getClass().getMethod("replaceText",int.class,int.class,String.class).invoke(document,0,number(document,"getLength"),text);return null;}); }
    static void compile() throws Exception
    { fx(()->{sourceEditor.getClass().getMethod("scheduleCompilation",reason.getClass(),explicit.getClass()).invoke(sourceEditor,reason,explicit);return null;}); }
    static void waitForMissingType() throws Exception
    {
        for(int i=0;i<300;i++)
        {
            boolean found=fx(()->((List<?>)call(sourceEditor,"getLightDiagnostics")).stream().anyMatch(value->{try{return ((String)call(value,"getCompilerCode")).contains("invalid.meth.decl.ret.type.req");}catch(Exception error){throw new RuntimeException(error);}}));
            if(found)return;Thread.sleep(100);
        }
        throw new AssertionError("Compiler missing-return-type diagnostic not received");
    }
}
