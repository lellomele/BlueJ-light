/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception. */
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class EditorCompletionSmoke extends BootSmoke
{
    static Object field(Object value, String name) throws Exception {
        Field f=value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    static void parsed(Object syntax) throws Exception {
        CompletableFuture<Void> ready=new CompletableFuture<>();
        fx(()->{Class<?> type=loader.loadClass("bluej.utility.javafx.FXPlatformRunnable");
            syntax.getClass().getMethod("whenParsed",type).invoke(syntax,Proxy.newProxyInstance(loader,new Class<?>[]{type},
                (o,m,a)->{ready.complete(null);return null;}));return null;});
        ready.get(30,TimeUnit.SECONDS);
    }
    static Object popup(Object sourceEditor) throws Exception {
        for(int i=0;i<300;i++){
            Object p=fx(()->{Object active=field(sourceEditor,"activeCompletion");return active!=null&&(Boolean)call(active,"isShowing")?active:null;});
            if(p!=null)return p;Thread.sleep(20);
        }
        throw new AssertionError("Completion popup missing");
    }
    static void key(Object popup,KeyCode code) throws Exception {
        fx(()->{Stage window=(Stage)field(popup,"window");Event.fireEvent(window.getScene().getRoot(),
            new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,false,false,false));return null;});
    }
    static void verify(Object sourceEditor) throws Exception {
        Object pane=fx(()->call(sourceEditor,"getSourcePane")),document=fx(()->call(pane,"getDocument"));
        Object syntax=fx(()->call(sourceEditor,"getSourceDocument"));
        String original=fx(()->(String)call(document,"getFullContent"));
        if(fx(()->field(sourceEditor,"htmlPane"))!=null)throw new AssertionError("Source editor eagerly created WebKit");
        String probe=original.substring(0,original.lastIndexOf('}'))+
            "String completionProbe() { String word = \"value\"; return \"probe\"; }\n}\n";
        Method replace=document.getClass().getMethod("replaceText",int.class,int.class,String.class);
        Method caret=pane.getClass().getMethod("positionCaret",int.class);
        Method assist=sourceEditor.getClass().getDeclaredMethod("createContentAssist");assist.setAccessible(true);
        Method perform=loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed",boolean.class);
        Object actions=fx(()->call(sourceEditor,"getActions"));
        Object undo=actions.getClass().getMethod("getActionByName",String.class).invoke(actions,"undo");
        Object redo=actions.getClass().getMethod("getActionByName",String.class).invoke(actions,"redo");
        fx(()->{replace.invoke(document,0,((Number)call(document,"getLength")).intValue(),probe);return null;});parsed(syntax);
        int position=probe.indexOf("return \"probe\"")+"return ".length();
        Object previous=null,previousWindow=null;
        for(int round=0;round<20;round++){
            boolean member=round%2==0;String prefix=member?"word.sub":"courses.g";
            fx(()->{replace.invoke(document,position,position+"\"probe\"".length(),prefix);caret.invoke(pane,position+prefix.length());
                ((Node)pane).getScene().getWindow().requestFocus();((Node)pane).requestFocus();return null;});parsed(syntax);
            fx(()->{assist.invoke(sourceEditor);return null;});Object active=popup(sourceEditor);
            Object window=fx(()->field(active,"window"));
            if(previous!=null&&(active!=previous||window!=previousWindow))throw new AssertionError("Completion window was not reused");
            previous=active;previousWindow=window;
            List<?> choices=fx(()->(List<?>)field(active,"choices"));
            boolean substring=choices.stream().anyMatch(c->{try{return c.getClass().getField("choice").get(c).equals("substring");}catch(Exception e){throw new RuntimeException(e);}});
            if(substring!=member)throw new AssertionError("Old completion candidates leaked between contexts");
            String before=fx(()->(String)call(document,"getFullContent"));
            fx(()->{Object manager=field(sourceEditor,"undoManager");Method forget=manager.getClass().getMethod("forgetHistory");forget.setAccessible(true);forget.invoke(manager);return null;});
            key(active,KeyCode.ENTER);
            String accepted=fx(()->(String)call(document,"getFullContent"));
            if(accepted.equals(before))throw new AssertionError("Completion was not accepted");
            fx(()->{perform.invoke(undo,false);if(!before.equals(call(document,"getFullContent")))throw new AssertionError("Completion undo failed");
                perform.invoke(redo,false);if(!accepted.equals(call(document,"getFullContent")))throw new AssertionError("Completion redo failed");
                perform.invoke(undo,false);replace.invoke(document,position,position+prefix.length(),"\"probe\"");return null;});parsed(syntax);
        }
        fx(()->{replace.invoke(document,0,((Number)call(document,"getLength")).intValue(),original);call(sourceEditor,"save");return null;});parsed(syntax);
        if(fx(()->field(sourceEditor,"htmlPane"))!=null)throw new AssertionError("Completions initialized the interface WebView");
        Path html=Path.of((String)fx(()->field(sourceEditor,"docFilename")));
        Files.createDirectories(html.getParent());
        Files.writeString(html,"<!doctype html><html><body><section class=\"detail\" id=\"answer()\">\n<h3>answer</h3>\n</section></body></html>");
        Files.setLastModifiedTime(html,FileTime.fromMillis(System.currentTimeMillis()+10000));
        fx(()->{sourceEditor.getClass().getMethod("showInterface",boolean.class).invoke(sourceEditor,true);return null;});
        boolean loaded=false;
        for(int i=0;i<150;i++){
            loaded=fx(()->{WebView view=(WebView)field(sourceEditor,"htmlPane");return view!=null&&view.isVisible()
                &&view.getEngine().getDocument()!=null&&view.getEngine().getDocument().getElementsByTagName("a").getLength()>0;});
            if(loaded)break;Thread.sleep(100);
        }
        if(!loaded){fx(()->{WebView view=(WebView)field(sourceEditor,"htmlPane");System.out.println("HTML_DIAGNOSTIC view="+view+" visible="+(view==null?null:view.isVisible())
            +" state="+(view==null?null:view.getEngine().getLoadWorker().getState())+" error="+(view==null?null:view.getEngine().getLoadWorker().getException()));return null;});
            throw new AssertionError("Lazy interface documentation or source links failed");}
        fx(()->{sourceEditor.getClass().getMethod("showInterface",boolean.class).invoke(sourceEditor,false);
            WebView view=(WebView)field(sourceEditor,"htmlPane");if(view.isVisible()||view.isManaged())throw new AssertionError("Hidden HTML view still participates in layout");return null;});
        System.out.println("COMPLETION_REUSE_OK rounds=20 accepted=true undo=true redo=true freshCandidates=true lazyHtml=true sourceLinks=true");
    }
}
