/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
import bluej.Boot;
import java.nio.file.*;
import java.util.*;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

public class ProjectCompletionSmoke extends BootSmoke
{
    static final List<Double> latencies = new ArrayList<>();
    static final List<String> lastTagged = new ArrayList<>();
    public static void main(String[] args) {
        Path folder = Path.of(args[0]);
        int compiled = javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",folder.toString(),folder.resolve("b1.java").toString());
        if(compiled != 0) throw new AssertionError("Fixture compilation failed");
        Thread worker = new Thread(() -> {
            try {
                for(int i=0;i<200 && editor==null;i++) {
                    try { editor=fx(BootSmoke::findEditor); } catch(IllegalStateException ex) { }
                    if(editor==null) Thread.sleep(100);
                }
                if(editor==null) throw new AssertionError("No diagram");
                loader=editor.getClass().getClassLoader(); pkg=call(editor,"getPackage");
                Object a=target("a1"), b=target("b1"), unused=target("Unused");
                String originalA=Files.readString(folder.resolve("a1.java")), originalB=Files.readString(folder.resolve("b1.java"));
                byte[] binaryB=Files.readAllBytes(folder.resolve("b1.class"));
                Object ae=open(a);
                Object initialState=call(b,"getState");
                fx(() -> { b.getClass().getMethod("setState",initialState.getClass()).invoke(b,Enum.valueOf((Class)initialState.getClass(),"COMPILED")); return null; });
                double compiledBackend = backendMillis(b);
                try(var lockA=new java.io.FileInputStream(folder.resolve("a1.java").toFile());
                    var lockB=new java.io.FileInputStream(folder.resolve("b1.java").toFile())) {
                    verifyKeywords(ae);
                    replace(ae,originalA);
                    List<String> names=popupNames(ae,"public class a1 { void test() { new b| } }\n");
                    require(names.contains("a1") && names.contains("b1") && names.contains("Unused"),"Uncompiled project types missing: "+names);
                    require(fx(() -> call(unused,"getEditorIfOpen"))==null,"Type names opened an unused editor");
                    Object be=open(b);
                    String fresh="public class b1 { public int newField; public void newMethod() {} private void secret() {} public static class Nested {} }\n";
                    replace(be,fresh); parsed(be);
                    double liveBackend = backendMillis(b);
                    System.out.println("PROJECT_COMPLETION_BACKEND compiledMs="+compiledBackend+" liveSourceMs="+liveBackend+" samples=50");
                    Object state=call(b,"getState");
                    fx(() -> { b.getClass().getMethod("setState",state.getClass()).invoke(b,Enum.valueOf((Class)state.getClass(),"COMPILED")); return null; });
                    names=popupNames(ae,"public class a1 { void test() { b1 value = new b1(); value.n| } }\n");
                    require(names.contains("newField") && names.contains("newMethod"),"Unsaved members missing: "+names);
                    require(!names.contains("oldField") && !names.contains("oldMethod") && !names.contains("secret"),"Stale/private members: "+names);
                    names=popupNames(ae,"public class a1 { int currentField; void test(int aParam) { int aLocal=0; | } } class Helper {}\n");
                    require(names.containsAll(List.of("currentField","aParam","aLocal","Helper","Nested")),"Current-file elements missing: "+names);
                    for(String incomplete:List.of("class a1 { void test( |","class a1 { int missing = ; new b|","class a1 { new b|","class |"))
                        require(popupNames(ae,incomplete).contains("b1"),"Incomplete source lost project types");
                    replace(be,fresh.substring(0,fresh.lastIndexOf('}'))+" public void broken(\n");
                    names=popupNames(ae,"public class a1 { void test() { b1 value = new b1(); value.n| } }\n");
                    require(names.contains("newField"),"Incomplete sibling lost known members: "+names);
                    undo(be); parsed(be);
                    require(popupNames(ae,"public class a1 { void test() { b1 value = new b1(); value.n| } }\n").contains("newMethod"),"Sibling undo stale");
                    redo(be); parsed(be);
                    require(popupNames(ae,"public class a1 { void test() { b1 value = new b1(); value.n| } }\n").contains("newField"),"Sibling redo stale");
                    replace(be,"public class Renamed { public int renamedField; }\n"); parsed(be);
                    names=popupNames(ae,"public class a1 { void test() { new Ren| } }\n");
                    require(names.contains("Renamed") && !lastTagged.contains("b1#class"),"Unsaved type rename stale: "+lastTagged);
                    require(lastTagged.contains("b1#other") && !names.contains("Internal"),"Package visibility/collision failed: "+lastTagged);
                    acceptQualified(ae);
                    require(popupNames(ae,"public class a1 { void test() { Renamed r=null; r.| } }\n").contains("renamedField"),"Renamed sibling resolution failed");
                    replace(be,fresh); parsed(be);
                    for(int i=0;i<20;i++) require(popupNames(ae,"public class a1 { void test() { b1 value = new b1(); value.n| } }\n").contains("newMethod"),"Warm suggestions stale");
                    require(Files.readString(folder.resolve("a1.java")).equals(originalA),"Current source saved by completion");
                    require(Files.readString(folder.resolve("b1.java")).equals(originalB),"Sibling source saved by completion");
                    require(Arrays.equals(binaryB,Files.readAllBytes(folder.resolve("b1.class"))),"Completion recompiled sibling");
                    replace(ae,originalA); replace(be,originalB);
                }
                Object be=fx(() -> call(b,"getEditorIfOpen"));
                fx(() -> { call(ae,"save"); call(be,"save"); return null; });
                latencies.sort(Double::compare);
                System.out.println("PROJECT_COMPLETION_OK types=true uncompiled=true unsavedMembers=true incomplete=true rename=true undoRedo=true noSave=true noCompile=true unusedEditorClosed=true samples="+latencies.size()
                    +" popupMedianMs="+latencies.get(latencies.size()/2)+" popupP95Ms="+latencies.get(Math.min(latencies.size()-1,(int)(latencies.size()*0.95))));
                fx(() -> { loader.loadClass("bluej.Main").getMethod("doQuit").invoke(null); return null; });
            } catch(Throwable ex) { System.out.println("PROJECT_COMPLETION_LATENCIES "+latencies); ex.printStackTrace(); System.exit(2); }
        },"Project source completion verification");
        worker.setDaemon(true); worker.start();
        Boot.main(new String[]{"-bluej.userHome="+args[1],args[0]});
    }
    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static void verifyKeywords(Object sourceEditor) throws Exception {
        Object pane = fx(() -> call(sourceEditor, "getSourcePane"));
        Object document = fx(() -> call(pane, "getDocument"));
        var caret = pane.getClass().getMethod("positionCaret", int.class);
        var assist = sourceEditor.getClass().getDeclaredMethod("createContentAssist"); assist.setAccessible(true);
        String probe = "public class a1 { int zMember; void zMethod() {} void test() { int aLocal=0; ret } }\n";
        replace(sourceEditor, probe);
        fx(() -> { caret.invoke(pane, probe.indexOf("ret }")+3); ((Node)pane).requestFocus(); return null; });
        parsed(sourceEditor);
        fx(() -> { assist.invoke(sourceEditor); return null; });
        Object popup = EditorCompletionSmoke.popup(sourceEditor);
        List<String> choices = fx(() -> {
            List<String> result = new ArrayList<>();
            for (Object choice : (List<?>)EditorCompletionSmoke.field(popup, "choices"))
                result.add((String)choice.getClass().getField("choice").get(choice));
            return result;
        });
        require(choices.contains("return"), "Return keyword missing");
        EditorCompletionSmoke.key(popup, KeyCode.ENTER);
        String accepted = fx(() -> (String)call(document, "getFullContent"));
        require(accepted.contains("return }"), "Keyword insertion failed: " + accepted);
        undo(sourceEditor);
        require(fx(() -> call(document,"getFullContent")).equals(probe), "Keyword undo");
        redo(sourceEditor);
        require(fx(() -> call(document,"getFullContent")).equals(accepted), "Keyword redo");
        List<String> order = popupNames(sourceEditor,
            "public class a1 { int zMember; void zMethod() {} void test() { int aLocal=0; | } }\n");
        require(order.indexOf("zMember") >= 0 && order.indexOf("aLocal") >= 0 && order.indexOf("zMember") < order.indexOf("aLocal"),
            "Own member priority failed: " + order);
        List<String> qualified = popupNames(sourceEditor,
            "public class a1 { void test() { b1 partner = new b1(); partner.| } }\n");
        require(qualified.contains("oldField") && !qualified.contains("return") && !qualified.contains("public"),
            "Keywords leaked into member access: " + qualified);
        latencies.clear();
        System.out.println("KEYWORD_COMPLETION_OK insert=true undo=true redo=true ownMembersFirst=true noKeywordsAfterDot=true");
    }
    static double backendMillis(Object target) throws Exception {
        Class<?> reflective=loader.loadClass("bluej.debugger.gentype.Reflective");
        Class<?> gen=loader.loadClass("bluej.debugger.gentype.GenTypeClass"), javaType=loader.loadClass("bluej.debugger.gentype.JavaType");
        Class<?> info=loader.loadClass("bluej.parser.ExpressionTypeInfo");
        var constructor=info.getConstructor(javaType,gen,loader.loadClass("bluej.parser.lexer.LocatableToken"),boolean.class,boolean.class);
        var completions=loader.loadClass("bluej.parser.ParseUtils").getMethod("getPossibleCompletions",info,
            loader.loadClass("bluej.pkgmgr.JavadocResolver"),loader.loadClass("bluej.parser.ParseUtils$AssistContentConsumer"),
            loader.loadClass("bluej.parser.nodes.MethodNode"),int.class,boolean.class);
        return fx(() -> {
            Object type=gen.getConstructor(reflective).newInstance(call(target,"getTypeReflective"));
            Object expression=constructor.newInstance(type,null,null,false,false);
            for(int i=0;i<10;i++) completions.invoke(null,expression,null,null,null,-1,false);
            long start=System.nanoTime();
            for(int i=0;i<50;i++) completions.invoke(null,expression,null,null,null,-1,false);
            return (System.nanoTime()-start)/50.0/1_000_000.0;
        });
    }
    static void acceptQualified(Object e) throws Exception {
        replace(e,"public class a1 { void test() { new b1 } }\n");
        fx(() -> { Object pane=call(e,"getSourcePane"); pane.getClass().getMethod("positionCaret",int.class)
            .invoke(pane,"public class a1 { void test() { new b1".length()); ((Node)pane).requestFocus(); return null; });
        parsed(e);
        fx(() -> { var method=e.getClass().getDeclaredMethod("createContentAssist"); method.setAccessible(true); method.invoke(e); return null; });
        Object document=fx(() -> call(call(e,"getSourcePane"),"getDocument"));
        boolean accepted=false;
        for(int i=0;i<30;i++) {
            if(fx(() -> (String)call(document,"getFullContent")).contains("new other.b1")) { accepted=true; break; }
            Thread.sleep(20);
        }
        if(!accepted) {
            Object popup=EditorCompletionSmoke.popup(e); EditorCompletionSmoke.key(popup,KeyCode.ENTER);
        }
        String text=fx(() -> (String)call(call(call(e,"getSourcePane"),"getDocument"),"getFullContent"));
        require(text.contains("new other.b1"),"Qualified insertion failed: "+text); undo(e);
    }
    static Object target(String name) throws Exception {
        return fx(() -> ((List<?>)call(pkg,"getClassTargets")).stream().filter(t -> {
            try { return call(t,"getIdentifierName").equals(name); } catch(Exception ex) { throw new RuntimeException(ex); }
        }).findFirst().orElseThrow());
    }
    static Object open(Object target) throws Exception {
        return fx(() -> { Object e=call(target,"getEditor"); e.getClass().getMethod("setEditorVisible",boolean.class,boolean.class).invoke(e,true,false); return e; });
    }
    static void replace(Object e,String text) throws Exception {
        fx(() -> { Object document=call(call(e,"getSourcePane"),"getDocument"); document.getClass().getMethod("replaceText",int.class,int.class,String.class)
            .invoke(document,0,((Number)call(document,"getLength")).intValue(),text); return null; });
    }
    static void parsed(Object e) throws Exception { EditorCompletionSmoke.parsed(fx(() -> call(e,"getSourceDocument"))); }
    static void undo(Object e) throws Exception { history(e,"undo"); }
    static void redo(Object e) throws Exception { history(e,"redo"); }
    static void history(Object e,String action) throws Exception {
        fx(() -> { Object manager=EditorCompletionSmoke.field(e,"undoManager"); var method=manager.getClass().getMethod(action); method.setAccessible(true); method.invoke(manager); return null; });
    }
    static List<String> popupNames(Object e,String marked) throws Exception {
        int pos=marked.indexOf('|'); require(pos>=0,"Missing caret");
        replace(e,marked.replace("|",""));
        fx(() -> { Object pane=call(e,"getSourcePane"); e.getClass().getMethod("setEditorVisible",boolean.class,boolean.class).invoke(e,true,false);
            pane.getClass().getMethod("positionCaret",int.class).invoke(pane,pos); ((Node)pane).requestFocus(); return null; });
        parsed(e);
        long start=System.nanoTime();
        fx(() -> { var method=e.getClass().getDeclaredMethod("createContentAssist"); method.setAccessible(true); method.invoke(e); return null; });
        Object popup=EditorCompletionSmoke.popup(e);
        latencies.add((System.nanoTime()-start)/1_000_000.0);
        List<String> names=fx(() -> {
            List<String> result=new ArrayList<>();
            lastTagged.clear();
            for(Object choice:(List<?>)EditorCompletionSmoke.field(popup,"choices")) {
                String name=(String)choice.getClass().getField("choice").get(choice);
                result.add(name); lastTagged.add(name+"#"+choice.getClass().getField("type").get(choice));
            }
            return result;
        });
        EditorCompletionSmoke.key(popup,KeyCode.ESCAPE); return names;
    }
}
