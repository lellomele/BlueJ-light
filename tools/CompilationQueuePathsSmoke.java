/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-10. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;

public final class CompilationQueuePathsSmoke extends AdvancedSmoke
{
    static Object project, school, person, schoolEditor, personEditor, reasonUser, reasonModified, explicit, automatic;
    static String schoolSource, personSource;
    static Path work, releaseFile;
    static final Queue<String> events = new ConcurrentLinkedQueue<>();
    static final Queue<Throwable> uiErrors = new ConcurrentLinkedQueue<>();
    static CompilationRaceSmoke.Gate gate;
    static Thread runner;
    static int cases;

    public static void main(String[] args)
    {
        Thread test = new Thread(() -> {
            try {
                await("editor", () -> { try { editor = fx(BootSmoke::findEditor); return editor != null; } catch(IllegalStateException missing) {return false;} });
                loader=editor.getClass().getClassLoader(); pkg=fx(() -> call(editor,"getPackage")); project=call(pkg,"getProject");
                work=Path.of(args[0]).toAbsolutePath().getParent(); releaseFile=work.resolve("runner-release.flag");
                fx(() -> {
                    Thread.currentThread().setUncaughtExceptionHandler((t,e) -> {uiErrors.add(e);e.printStackTrace();});
                    school=target("School");person=target("Person");
                    school.getClass().getMethod("doubleClick",boolean.class).invoke(school,false);schoolEditor=call(school,"getEditor");
                    person.getClass().getMethod("doubleClick",boolean.class).invoke(person,false);personEditor=call(person,"getEditor");
                    return null;
                });
                reasonUser=constant("CompileReason","USER");reasonModified=constant("CompileReason","MODIFIED");
                explicit=constant("CompileType","EXPLICIT_USER_COMPILE");automatic=constant("CompileType","ERROR_CHECK_ONLY");
                addObserver();await("initial checks",CompilationQueuePathsSmoke::idle);
                schoolSource="public class School {\n public static int answer() { return 42; }\n"
                    +" public static void main(String[] args) throws Exception { while (!new java.io.File(\""
                    +releaseFile.toString().replace('\\','/')+"\").exists()) Thread.sleep(20); }\n"
                    +" public static void run() throws Exception { main(new String[0]); }\n}\n";
                personSource="public class Person {\n private String name;\n public Person(String name) { this.name=name; }\n"
                    +" public String getName() { return name; }\n public static int answer() { return 42; }\n}\n";
                replace(schoolEditor,schoolSource);replace(personEditor,personSource);compileAll(explicit);
                await("baseline classes", () -> idle() && compiled(school) && compiled(person));
                String mode=System.getProperty("bluej.light.queue.mode","all");
                if(mode.equals("all")||mode.equals("global")) { globalPriority(false);globalPriority(true); }
                if(mode.equals("all")||mode.equals("mixed")) { mixedTargets();dependencyQueued();editingStorm(); }
                if(mode.equals("all")||mode.equals("idle")) { waitingForDebugger(); }
                if(mode.equals("all")||mode.equals("failure")) { saveFailure();observerFailure();compilerFailure(); }
                if(mode.equals("all")) closeWithPendingTimer();
                if(!uiErrors.isEmpty())throw new AssertionError("UI exception",uiErrors.peek());
                System.out.println("COMPILATION_PATHS_OK cases="+cases+" mode="+mode+" uiExceptions=0");
                fx(() -> {loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null);return null;});
            } catch(Throwable failure) {
                try {if(gate!=null)gate.release.countDown();if(releaseFile!=null)Files.writeString(releaseFile,"release");}catch(Exception ignored){}
                failure.printStackTrace();System.exit(2);
            }
        },"Compilation queue paths");test.setDaemon(true);test.start();
        Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }

    static Object target(String name) throws Exception {
        return ((Collection<?>)call(pkg,"getVertices")).stream().filter(t -> t.toString().equals(name)).findFirst().orElseThrow();
    }
    static Object constant(String name,String value) throws Exception {return Enum.valueOf((Class)loader.loadClass("bluej.compiler."+name),value);}
    static Object fieldValue(Object value,String name) throws Exception {return findField(value,name).get(value);}
    static Field findField(Object value,String name) throws Exception {
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static boolean pendingEmpty(String name) throws Exception {
        Object pending=fieldValue(project,name);return pending instanceof Map<?,?> map ? map.isEmpty() : ((Collection<?>)pending).isEmpty();
    }
    static boolean idle() throws Exception {return fx(() -> {
        for(Object t:(Collection<?>)call(pkg,"getVertices")) if((Boolean)call(t,"isQueued"))return false;
        for(Object ed:List.of(schoolEditor,personEditor))for(String flag:List.of("compilationQueued","compilationStarted","requeueForCompilation"))if((Boolean)fieldValue(ed,flag))return false;
        return pendingEmpty("scheduledTargets")&&pendingEmpty("scheduledPkgs")&&!(Boolean)fieldValue(pkg,"currentlyCompiling")&&!(Boolean)fieldValue(pkg,"waitingForIdleToCompile");
    });}
    static boolean compiled(Object t) throws Exception {return fx(() -> (Boolean)call(t,"isCompiled"));}
    static void replace(Object ed,String source) throws Exception {fx(() -> {
        Object doc=call(call(ed,"getSourcePane"),"getDocument");doc.getClass().getMethod("replaceText",int.class,int.class,String.class).invoke(doc,0,number(doc,"getLength"),source);return null;
    });}
    static void save(Object ed) throws Exception {fx(() -> {call(ed,"save");return null;});}
    static void compileAll(Object type) throws Exception {fx(() -> {pkg.getClass().getMethod("compile",reasonUser.getClass(),type.getClass()).invoke(pkg,reasonUser,type);return null;});}
    static void editCompile() throws Exception {fx(() -> {call(schoolEditor,"compileOrShowNextError");return null;});}
    static void hold() throws Exception {gate=CompilationRaceSmoke.blockCompiler(project,work);if(!gate.entered.await(15,TimeUnit.SECONDS))throw new AssertionError("Gate not entered");}
    static void release() {gate.release.countDown();gate=null;}
    static void await(String name,Callable<Boolean> condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        while(!condition.call()) {
            if(!uiErrors.isEmpty())throw new AssertionError("UI exception",uiErrors.peek());
            if(System.nanoTime()>=end)throw new AssertionError("Timeout: "+name+" events="+events);
            Thread.sleep(20);
        }
    }
    static void passed(String name) {cases++;System.out.println("QUEUE_CASE_OK "+name);}

    static void globalPriority(boolean singleTargetFirst) throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);events.clear();hold();
        replace(schoolEditor,schoolSource.replace("return 42;","return "+(singleTargetFirst?52:51)+";"));save(schoolEditor);
        if(singleTargetFirst)fx(() -> {schoolEditor.getClass().getMethod("scheduleCompilation",reasonUser.getClass(),automatic.getClass()).invoke(schoolEditor,constant("CompileReason","LOADED"),automatic);return null;});
        else compileAll(automatic);
        await("auto queued", () -> fx(() -> (Boolean)call(school,"isQueued")));
        compileAll(explicit);compileAll(automatic);fx(() -> null);release();
        await("global manual survives", () -> idle()&&compiled(school)&&events.contains("School:end:EXPLICIT_USER_COMPILE:true"));
        passed(singleTargetFirst?"global-during-target":"global-priority");
    }

    static void mixedTargets() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);events.clear();hold();
        replace(schoolEditor,schoolSource.replace("return 42;","return 61;"));save(schoolEditor);
        replace(personEditor,personSource.replace("return 42;","return 62;"));save(personEditor);
        fx(() -> {
            var method=project.getClass().getMethod("scheduleCompilation",boolean.class,reasonUser.getClass(),explicit.getClass(),school.getClass());
            method.invoke(project,false,reasonUser,explicit,school);method.invoke(project,false,reasonModified,automatic,person);return null;
        });
        await("both queued", () -> fx(() -> (Boolean)call(school,"isQueued")&&(Boolean)call(person,"isQueued")));
        release();await("mixed intents", () -> idle()&&compiled(school)&&events.contains("School:end:EXPLICIT_USER_COMPILE:true")&&events.contains("Person:end:ERROR_CHECK_ONLY:true"));
        compileAll(explicit);await("all compiled", () -> idle()&&compiled(person));passed("per-target-intent");
    }

    static void waitingForDebugger() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);events.clear();Files.deleteIfExists(releaseFile);
        Object debugger=call(pkg,"getDebugger");AtomicReference<Throwable> failure=new AtomicReference<>();
        runner=new Thread(() -> {try{debugger.getClass().getMethod("runClassMain",String.class).invoke(debugger,"School");}catch(Throwable e){failure.set(e);}},"Fixture user program");runner.setDaemon(true);runner.start();
        await("debugger running", () -> ((Number)call(debugger,"getStatus")).intValue()==3);
        replace(schoolEditor,schoolSource.replace("int answer()","void answer()"));
        replace(personEditor,personSource.replace("int answer()","void answer()"));
        await("timers dispatched", () -> fx(() -> (Boolean)fieldValue(pkg,"waitingForIdleToCompile")&&pendingEmpty("scheduledTargets")));
        fx(() -> null);Files.writeString(releaseFile,"release");runner.join(10000);
        if(failure.get()!=null)throw new AssertionError("Fixture failed",failure.get());
        await("both latest diagnostics", () -> idle()&&diagnostic(schoolEditor)&&diagnostic(personEditor));
        replace(schoolEditor,schoolSource);replace(personEditor,personSource);compileAll(explicit);
        await("repair after idle", () -> idle()&&compiled(school)&&compiled(person));passed("debugger-two-targets");
    }

    static void dependencyQueued() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);
        String dependent=schoolSource.replace("return 42;","return Person.answer();");
        replace(schoolEditor,dependent);compileAll(explicit);await("dependency baseline", () -> idle()&&compiled(school));
        hold();replace(personEditor,personSource.replace("return 42;","return 63;"));
        await("dependency queued", () -> fx(() -> (Boolean)call(person,"isQueued")));
        replace(schoolEditor,dependent.replace("return Person.answer();","return Person.answer()+1;"));editCompile();fx(() -> null);release();
        await("dependency and caller current", () -> idle()&&compiled(school)&&compiled(person));
        replace(schoolEditor,schoolSource);replace(personEditor,personSource);compileAll(explicit);
        await("dependency repair", () -> idle()&&compiled(school)&&compiled(person));passed("queued-dependency");
    }
    static boolean diagnostic(Object ed) throws Exception {return fx(() -> !((List<?>)call(ed,"getLightDiagnostics")).isEmpty());}

    static void saveFailure() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);replace(schoolEditor,schoolSource.replace("return 42;","return 71;"));
        Field editorField=findField(school,"editor");Object original=editorField.get(school);AtomicInteger cancels=new AtomicInteger(), callbacks=new AtomicInteger();
        Object wrapper=Proxy.newProxyInstance(loader,new Class<?>[]{loader.loadClass("bluej.editor.Editor")},(p,m,a) -> {
            if(m.getName().equals("save"))throw new IOException("synthetic save failure");
            if(m.getName().equals("compileCancelled"))cancels.incrementAndGet();
            try{return m.invoke(original,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
        fx(() -> {editorField.set(school,wrapper);return null;});
        try {
            Object observer=observer((m,a) -> {if(m.equals("endCompile"))callbacks.incrementAndGet();});
            fx(() -> {pkg.getClass().getMethod("compile",school.getClass(),boolean.class,loader.loadClass("bluej.compiler.FXCompileObserver"),reasonUser.getClass(),explicit.getClass()).invoke(pkg,school,false,observer,reasonUser,explicit);return null;});
            if(callbacks.get()!=1||cancels.get()==0||fx(() -> (Boolean)call(school,"isQueued")||(Boolean)fieldValue(schoolEditor,"compilationQueued")))throw new AssertionError("Save failure left a queued request");
        } finally {fx(() -> {editorField.set(school,original);return null;});}
        editCompile();await("retry after save failure", () -> idle()&&compiled(school));passed("save-failure-recovery");
    }

    static void observerFailure() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);replace(schoolEditor,schoolSource.replace("return 42;","return 81;"));
        AtomicInteger end=new AtomicInteger();Object bad=observer((method,args) -> {if(method.equals("endCompile")){end.incrementAndGet();throw new IllegalStateException("synthetic observer failure");}});
        fx(() -> {pkg.getClass().getMethod("compile",loader.loadClass("bluej.compiler.FXCompileObserver"),reasonUser.getClass(),explicit.getClass()).invoke(pkg,bad,reasonUser,explicit);return null;});
        await("callback recovery", () -> idle()&&compiled(school));if(end.get()!=1)throw new AssertionError("End callback repeated");
        replace(schoolEditor,schoolSource);editCompile();await("next compile", () -> idle()&&compiled(school));passed("observer-failure-recovery");
        int previous=end.get();
        fx(() -> {pkg.getClass().getMethod("compile",loader.loadClass("bluej.compiler.FXCompileObserver"),reasonUser.getClass(),explicit.getClass()).invoke(pkg,bad,reasonUser,explicit);return null;});
        if(end.get()!=previous+1)throw new AssertionError("Empty-job end callback repeated");
        passed("empty-observer-failure");
    }

    static void compilerFailure() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);events.clear();setOption("--bluej-synthetic-invalid-option");
        try {
            replace(schoolEditor,schoolSource.replace("return 42;","return 91;"));editCompile();
            await("failed compile drains",CompilationQueuePathsSmoke::idle);
            long ends=events.stream().filter(e -> e.equals("School:end:EXPLICIT_USER_COMPILE:false")).count();
            if(ends<1||ends>2)throw new AssertionError("Unbounded compiler retry: "+ends);
        } finally {setOption("");}
        editCompile();await("compiler alive", () -> idle()&&compiled(school));passed("bounded-failure-recovery");
    }
    static void setOption(String value) throws Exception {fx(() -> {loader.loadClass("bluej.Config").getMethod("putPropString",String.class,String.class).invoke(null,"bluej.compiler.options",value);return null;});}

    static void editingStorm() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);
        replace(schoolEditor,schoolSource);editCompile();await("storm baseline", () -> idle()&&compiled(school));
        fx(() -> {schoolEditor.getClass().getMethod("setEditorVisible",boolean.class,boolean.class).invoke(schoolEditor,true,false);return null;});
        hold();
        verifySnippetBrowser(schoolEditor);
        for(int i=0;i<12;i++)fx(() -> {
            Object doc=call(call(schoolEditor,"getSourcePane"),"getDocument");doc.getClass().getMethod("replaceText",int.class,int.class,String.class).invoke(doc,0,0,"// rapid edit\n");
            Object actions=call(schoolEditor,"getActions");Class<?> action=loader.loadClass("bluej.editor.flow.FlowActions$FlowAbstractAction");
            for(String name:List.of("undo","redo","undo"))action.getMethod("actionPerformed",boolean.class).invoke(actions.getClass().getMethod("getActionByName",String.class).invoke(actions,name),false);
            return null;
        });
        replace(schoolEditor,schoolSource);editCompile();fx(() -> null);release();await("storm final code", () -> idle()&&compiled(school));
        if(!schoolSource.equals(fx(() -> call(call(call(schoolEditor,"getSourcePane"),"getDocument"),"getFullContent"))))throw new AssertionError("Undo/redo lost source");
        passed("completion-undo-redo-storm");
    }

    static void closeWithPendingTimer() throws Exception {
        await("idle",CompilationQueuePathsSmoke::idle);hold();replace(schoolEditor,schoolSource.replace("return 42;","return 99;"));fx(() -> null);
        fx(() -> {loader.loadClass("bluej.pkgmgr.PkgMgrFrame").getMethod("closeProject",project.getClass()).invoke(null,project);return null;});release();
        if(!fx(() -> pendingEmpty("scheduledTargets")&&pendingEmpty("scheduledPkgs")))throw new AssertionError("Project timers remain");
        passed("close-cancels-timers");
    }

    interface Event {void accept(String method,Object[] args) throws Throwable;}
    static Object observer(Event event) throws Exception {return Proxy.newProxyInstance(loader,new Class<?>[]{loader.loadClass("bluej.compiler.FXCompileObserver")},(p,m,a) -> {event.accept(m.getName(),a);return m.getReturnType()==boolean.class?false:null;});}
    static void addObserver() throws Exception {
        Object observer=observer((method,args) -> {
            if(!method.equals("endCompile"))return;
            for(int i=0;i<Array.getLength(args[0]);i++) {
                File file=(File)call(Array.get(args[0],i),"getJavaCompileInputFile");String name=file.getName().replace(".java","");
                events.add(name+":end:"+args[2]+":"+args[1]);
            }
        });
        fx(() -> {pkg.getClass().getMethod("addCompileObserver",loader.loadClass("bluej.compiler.FXCompileObserver")).invoke(pkg,observer);return null;});
    }
}
