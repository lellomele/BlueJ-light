/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.*;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.control.*;

public class RecoverySmoke extends AdvancedSmoke
{
    public static void main(String[] args)
    {
        Thread verify = new Thread(() -> {
            try
            {
                for (int i=0;i<300 && editor==null;i++) { try {editor=fx(BootSmoke::findEditor);} catch(IllegalStateException ignored){} if(editor==null)Thread.sleep(100); }
                if(editor==null)throw new AssertionError("No project opened");loader=editor.getClass().getClassLoader();pkg=call(editor,"getPackage");
                Object target=fx(()->((Collection<?>)call(pkg,"getVertices")).stream().filter(t->t.toString().equals("School")).findFirst().orElseThrow());
                Path file=((java.io.File)fx(()->call(target,"getJavaSourceFile"))).toPath();String original=Files.readString(file);String marker=original+"\n// recovered-marker\n";
                Path preferences=((java.io.File)fx(()->loader.loadClass("bluej.Config").getMethod("getUserConfigDir").invoke(null))).toPath();
                Class<?> historyClass=loader.loadClass("bluej.light.LocalHistory");Object history=historyClass.getConstructor(Path.class).newInstance(preferences);
                historyClass.getMethod("draft",Path.class,String.class,String.class).invoke(history,file,marker,"changed-external-base");
                Object javaEditor=fx(()->{target.getClass().getMethod("doubleClick",boolean.class).invoke(target,false);return call(target,"getEditor");});
                DialogPane recovery=dialog("light-recovery-dialog");
                fx(()->{if(recovery.getContentText().isBlank())throw new AssertionError("Recovery explanation missing");snapshot(recovery,"recovery.png");ButtonType restore=recovery.getButtonTypes().stream().filter(t->t.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();((Button)recovery.lookupButton(restore)).fire();return null;});
                Object source=fx(()->call(javaEditor,"getSourcePane"));Object document=fx(()->call(source,"getDocument"));
                fx(()->{if(!marker.equals(call(document,"getFullContent")))throw new AssertionError("Recovery did not restore complete text");Object actions=call(javaEditor,"getActions");Object undo=actions.getClass().getMethod("getActionByName",String.class).invoke(actions,"undo");loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed",boolean.class).invoke(undo,false);if(!original.equals(call(document,"getFullContent")))throw new AssertionError("Recovery is not undoable");return null;});
                if(!original.equals(Files.readString(file)))throw new AssertionError("Recovery immediately overwrote the original source");
                System.out.println("RECOVERY_OK conflictPrompt=true draftRestored=true undo=true originalPreserved=true");
                fx(()->{loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);return null;});
            }
            catch(Throwable ex){ex.printStackTrace();System.exit(2);}
        },"Recovery verification");verify.setDaemon(true);verify.start();
        Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }
}
