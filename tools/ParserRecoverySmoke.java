/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;

/** Integration regression: parser failure, completion/snippets, closing/reopening and undo/redo. */
public final class ParserRecoverySmoke extends AdvancedSmoke {
    static Object sourceEditor, sourcePane, document, syntax;
    static Class<?> callbackType;
    static Method ready, schedule, suspend, assist;
    static final Queue<Throwable> uiFailures=new ConcurrentLinkedQueue<>();
    static final Properties results=new Properties();
    static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    public static void main(String[] args) {
        Thread test=new Thread(() -> {
            try {
                for(int i=0;i<300 && editor==null;i++) {
                    try { editor=fx(BootSmoke::findEditor); } catch(IllegalStateException ignored) { }
                    if(editor==null)Thread.sleep(100);
                }
                require(editor!=null,"Project did not open"); loader=editor.getClass().getClassLoader(); pkg=call(editor,"getPackage");
                fx(() -> { Thread.currentThread().setUncaughtExceptionHandler((thread,error)->uiFailures.add(error));return null; });
                Object target=fx(() -> ((Collection<?>)call(pkg,"getVertices")).stream().filter(t->t.toString().equals("School")).findFirst().orElseThrow());
                sourceEditor=fx(() -> { target.getClass().getMethod("doubleClick",boolean.class).invoke(target,false);return call(target,"getEditor"); });
                sourcePane=fx(() -> call(sourceEditor,"getSourcePane"));document=fx(() -> call(sourcePane,"getDocument"));syntax=fx(() -> call(sourceEditor,"getSourceDocument"));
                String original=fx(() -> (String)call(document,"getFullContent"));
                callbackType=loader.loadClass("bluej.utility.javafx.FXPlatformRunnable");
                ready=syntax.getClass().getMethod("whenParsed",callbackType);
                schedule=syntax.getClass().getMethod("scheduleReparse",int.class,int.class);
                suspend=syntax.getClass().getDeclaredMethod("suspendParser",String.class,RuntimeException.class);suspend.setAccessible(true);
                assist=sourceEditor.getClass().getDeclaredMethod("createContentAssist");assist.setAccessible(true);
                primeIndex();
                // A real completion request is waiting behind the parser when it fails.
                String probe="public class School { void work() { re } }\n";
                replace(probe);EditorCompletionSmoke.parsed(syntax);
                AtomicInteger failing=new AtomicInteger(), survivor=new AtomicInteger();
                fx(() -> {
                    sourcePane.getClass().getMethod("positionCaret",int.class).invoke(sourcePane,probe.indexOf("re }")+2);
                    ((Node)sourcePane).requestFocus();schedule.invoke(syntax,0,probe.length());
                    ready.invoke(syntax,callback(() -> {failing.incrementAndGet();throw new IllegalStateException("Injected parser recovery listener failure");}));
                    assist.invoke(sourceEditor);
                    ready.invoke(syntax,callback(survivor::incrementAndGet));
                    require(((List<?>)field(syntax,"parsedCallbacks")).size()>=3,"Completion did not wait behind the parser");
                    suspend.invoke(syntax,"Controlled completion/parser recovery regression",null);
                    require(failing.get()==1 && survivor.get()==1,"Suspension lost a waiting request");
                    require(call(syntax,"getParser")==null,"Suspension did not discard the failed parser");return null;
                });
                Object popup=EditorCompletionSmoke.popup(sourceEditor);
                boolean keyword=fx(() -> ((List<?>)field(popup,"choices")).stream().anyMatch(choice -> {
                    try {return choice.getClass().getField("choice").get(choice).equals("return");}catch(Exception ex){throw new RuntimeException(ex);}
                }));
                require(keyword,"Degraded completion lost Java keywords");EditorCompletionSmoke.key(popup,KeyCode.ESCAPE);
                results.setProperty("suspension.completionDelivered","true");results.setProperty("suspension.survivingListeners","true");
                // Two queued browse commands must yield one dialog; notification must not enter its modal loop.
                AtomicInteger afterBrowse=new AtomicInteger();
                fx(() -> {
                    call(sourceEditor,"browseSnippets");call(sourceEditor,"browseSnippets");
                    ready.invoke(syntax,callback(afterBrowse::incrementAndGet));
                    require(afterBrowse.get()==1,"Snippet dialog blocked parser notification");return null;
                });
                DialogPane degraded=dialog("light-snippet-browser");
                fx(() -> {
                    require(browserCount()==1,"Duplicate snippet dialogs opened");
                    ButtonType insert=degraded.getButtonTypes().stream().filter(t->t.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                    require(((Button)degraded.lookupButton(insert)).isDisabled(),"Insertion allowed without a reliable parse context");
                    ((TextField)degraded.lookup("#snippet-search")).setText("fori");
                    require(!((ListView<?>)degraded.lookup("#snippet-list")).getItems().isEmpty(),"Degraded catalogue search failed");
                    ((Button)degraded.lookupButton(ButtonType.CLOSE)).fire();return null;
                });
                Thread.sleep(150);require(fx(ParserRecoverySmoke::browserCount)==0,"An older browse command reopened a second dialog");
                results.setProperty("snippets.degradedSearch","true");results.setProperty("snippets.latestRequestOnly","true");
                replace(original);EditorCompletionSmoke.parsed(syntax);
                require(fx(() -> call(syntax,"getParser"))!=null,"Parser did not recover after an edit");
                // Normal parsing must isolate a faulty listener as well as the suspension path.
                AtomicInteger normal=new AtomicInteger();
                fx(() -> {
                    schedule.invoke(syntax,0,original.length());
                    ready.invoke(syntax,callback(() -> {throw new IllegalStateException("Injected normal parse listener failure");}));
                    ready.invoke(syntax,callback(normal::incrementAndGet));call(syntax,"flushReparseQueue");
                    require(normal.get()==1 && (Boolean)call(syntax,"isParsedForCompletion"),"Healthy completion fan-out failed");
                    call(sourceEditor,"save");return null;
                });
                // Reopen immediately with identical content/caret: old requests must still be cancelled.
                fx(() -> {
                    var previousScene=((Node)sourcePane).getScene();
                    sourcePane.getClass().getMethod("positionCaret",int.class).invoke(sourcePane,original.indexOf("public class")+3);
                    schedule.invoke(syntax,0,original.length());assist.invoke(sourceEditor);call(sourceEditor,"browseSnippets");
                    call(sourceEditor,"close");
                    sourceEditor.getClass().getMethod("setEditorVisible",boolean.class,boolean.class).invoke(sourceEditor,true,true);
                    require(((Node)sourcePane).getScene()!=previousScene,"Reopen test must use a different window scene");return null;
                });
                EditorCompletionSmoke.parsed(syntax);Thread.sleep(250);
                fx(() -> {
                    require(browserCount()==0,"Closed-editor snippet request survived reopening");
                    Object active=field(sourceEditor,"activeCompletion");
                    require(active==null || !(Boolean)call(active,"isShowing"),"Closed-editor completion request survived reopening");return null;
                });
                results.setProperty("closeReopen.pendingRequestsCancelled","true");
                // Recovery must preserve ordinary insertion and its single undo/redo operation.
                fx(() -> {sourcePane.getClass().getMethod("positionCaret",int.class).invoke(sourcePane,original.indexOf("return 42"));return null;});
                editorCommand(sourceEditor,"light.snippets.browse");DialogPane browser=dialog("light-snippet-browser");
                fx(() -> {
                    ((TextField)browser.lookup("#snippet-search")).setText("fori");
                    ButtonType insert=browser.getButtonTypes().stream().filter(t->t.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                    require(!((Button)browser.lookupButton(insert)).isDisabled(),"Recovered snippet insertion is unavailable");
                    ((Button)browser.lookupButton(insert)).fire();return null;
                });
                String inserted=fx(() -> (String)call(document,"getFullContent"));require(!inserted.equals(original),"Snippet was not inserted");
                fx(() -> {
                    action("undo");require(original.equals(call(document,"getFullContent")),"Recovered snippet undo failed");
                    action("redo");require(inserted.equals(call(document,"getFullContent")),"Recovered snippet redo failed");
                    action("undo");call(sourceEditor,"save");return null;
                });
                require(Arrays.equals(Files.readAllBytes(Path.of(args[0],"School.java")),original.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Original source not restored");
                require(uiFailures.isEmpty(),"Unexpected UI exception: "+uiFailures.peek());
                results.setProperty("snippetInsertion.undoRedo","true");results.setProperty("sourceRestored","true");results.setProperty("uiExceptions","0");
                writeResults();
                System.out.println("PARSER_RECOVERY_OK suspension=true listenerIsolation=true degradedCompletion=true degradedSnippets=true latestSnippetRequest=true nonblockingNotification=true closeReopenCancellation=true insertionUndoRedo=true sourceRestored=true uiExceptions=0");
                fx(() -> {loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);return null;});
            }catch(Throwable failure){try{writeResults();}catch(Exception ignored){}failure.printStackTrace();System.exit(2);}
        },"Parser completion and snippet recovery verification");
        test.setDaemon(true);test.start();Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }
    static Object callback(Runnable action) {
        return Proxy.newProxyInstance(loader,new Class<?>[]{callbackType},(object,method,args)->{if(method.getName().equals("run"))action.run();return null;});
    }
    static void replace(String text) throws Exception {fx(() -> {document.getClass().getMethod("replaceText",int.class,int.class,String.class).invoke(document,0,number(document,"getLength"),text);return null;});}
    static int browserCount() {int count=0;for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot().lookup("#light-snippet-browser")!=null)count++;return count;}
    static void primeIndex() throws Exception {
        Object index=fx(() -> call(call(pkg,"getProject"),"getCompletionTypeIndex"));CompletableFuture<Void> done=new CompletableFuture<>();
        fx(() -> {index.getClass().getMethod("whenReady",callbackType).invoke(index,callback(() -> done.complete(null)));return null;});done.get(5,TimeUnit.SECONDS);
        for(int i=0;i<100;i++){if(fx(() -> field(index,"pending"))==null)return;Thread.sleep(20);}throw new AssertionError("Fixture index did not settle");
    }
    static void action(String name) throws Exception {
        Object actions=call(sourceEditor,"getActions"),command=actions.getClass().getMethod("getActionByName",String.class).invoke(actions,name);
        loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction").getMethod("actionPerformed",boolean.class).invoke(command,false);
    }
    static void writeResults() throws Exception {Path folder=Path.of("bench/probe-results");Files.createDirectories(folder);try(var stream=Files.newOutputStream(folder.resolve("parser-recovery.xml"))){results.storeToXML(stream,"Parser recovery integration regression");}}
}
