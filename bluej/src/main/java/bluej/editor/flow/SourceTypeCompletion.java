/* Copyright (C) 2026 Raffaele Mele. GPLv2 with Classpath Exception. */
package bluej.editor.flow;

import bluej.parser.AssistContent;
import bluej.parser.entity.ParsedReflective;
import bluej.parser.entity.TypeEntity;
import bluej.parser.nodes.ParsedCUNode;
import bluej.parser.nodes.ParsedNode;
import bluej.parser.nodes.ParsedTypeNode;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public final class SourceTypeCompletion extends AssistContent
{
    private record Declaration(String qualified, String pkg, String name, int modifiers, ParsedTypeNode node) { }
    static record Cached(long revision, List<Declaration> declarations) { }
    private final String name, insertion, pkg;
    private final int locality;

    private SourceTypeCompletion(String name, String insertion, String pkg, int locality) {
        this.name = name; this.insertion = insertion; this.pkg = pkg; this.locality = locality;
    }
    @Override @OnThread(Tag.Any) public String getName() { return name; }
    @Override public String getType() { return pkg.isEmpty() ? (insertion.contains(".") ? insertion.substring(0,insertion.lastIndexOf('.')) : "class") : pkg; }
    @Override public String getPackage() { return pkg; }
    @Override public List<ParamInfo> getParams() { return null; }
    @Override public Access getAccessPermission() { return null; }
    @Override public String getDeclaringClass() { return null; }
    @Override public CompletionKind getKind() { return CompletionKind.TYPE; }
    @Override public String getJavadoc() { return null; }
    public String insertion() { return insertion; }
    public int locality() { return locality; }

    private static List<Declaration> declarations(FlowEditor editor)
    {
        ParsedCUNode root = editor.peekCompletionParser();
        if (root == null) { editor.completionTypeSnapshot = null; return List.of(); }
        Cached cached = editor.completionTypeSnapshot;
        if (cached == null || cached.revision() != editor.getContentRevision()) {
            List<Declaration> found = new ArrayList<>();
            collect(root, found, 0);
            cached = new Cached(editor.getContentRevision(), List.copyOf(found));
            editor.completionTypeSnapshot = cached;
        }
        return cached.declarations();
    }

    private static void collect(ParsedNode node, List<Declaration> found, int depth)
    {
        if (depth > 64) return;
        if (node instanceof ParsedTypeNode type && type.getName() != null && ProjectTypeIndex.validName(type.getName())) {
            ParsedTypeNode top = type;
            while (top.getContainingClass() != null) top = top.getContainingClass();
            String prefix = top.getPrefix();
            String pkg = prefix.endsWith(".") ? prefix.substring(0, prefix.length()-1) : prefix;
            found.add(new Declaration((type.getPrefix()+type.getName()).replace('$','.'), pkg, type.getName(), type.getModifiers(), type));
            if (type.getInner() != null) collect(type.getInner(), found, depth+1);
            return;
        }
        var children = node.getChildren(0);
        while (children.hasNext()) {
            ParsedNode child = children.next().getNode();
            if (child instanceof ParsedTypeNode || child.getNodeType() == ParsedNode.NODETYPE_NONE)
                collect(child, found, depth+1);
        }
    }

    public static TypeEntity resolveOpen(Project project, String qualifiedName)
    {
        String name = qualifiedName.replace('$','.');
        for (var pkg : List.copyOf(project.getProjectPackages()))
            for (ClassTarget target : pkg.getClassTargets())
                if (target.getEditorIfOpen() instanceof FlowEditor editor)
                    for (Declaration type : declarations(editor))
                        if (type.qualified().equals(name)) return new TypeEntity(new ParsedReflective(type.node()));
        return null;
    }

    public static List<AssistContent> candidates(FlowEditor current, ParsedCUNode parser)
    {
        Project project = current.getProject();
        List<Declaration> own = declarations(current);
        ParsedNode context = parser == null ? null : parser.getContainingMethodOrClassNode(current.getSourcePane().getCaretPosition());
        while (context != null && !(context instanceof ParsedTypeNode)) context = context.getParentNode();
        ParsedTypeNode currentOwner = (ParsedTypeNode)context;
        String currentPackage = own.isEmpty() ? current.completionPackageName() : own.getFirst().pkg();
        Map<String, SourceTypeCompletion> result = new LinkedHashMap<>();
        Set<Path> covered = new HashSet<>();
        Set<String> cachedPackages = new HashSet<>();
        Set<Path> sourceFiles = new HashSet<>();
        List<ClassTarget> targets = new ArrayList<>();
        if (project != null) {
            for (var pkg : List.copyOf(project.getProjectPackages())) {
                cachedPackages.add(pkg.getQualifiedName());
                targets.addAll(pkg.getClassTargets());
            }
            for (ClassTarget target : targets) {
                Path source = target.getJavaSourceFile().toPath().toAbsolutePath().normalize();
                sourceFiles.add(source);
                if (target.getEditorIfOpen() instanceof FlowEditor editor) {
                    List<Declaration> live = declarations(editor);
                    if (!live.isEmpty()) {
                        covered.add(source);
                        for (Declaration type : live) addDeclaration(result, type, currentPackage, editor == current, parser, currentOwner);
                    }
                }
            }
            for (ProjectTypeIndex.Type type : project.getCompletionTypeIndex().snapshot()) {
                Path source = type.source().toAbsolutePath().normalize();
                if (covered.contains(source) || (cachedPackages.contains(type.pkg()) && !sourceFiles.contains(source))) continue;
                if (type.pkg().equals(currentPackage) || (type.publicType() && !type.pkg().isEmpty())) {
                    String insertion = type.pkg().equals(currentPackage) || (parser != null && parser.getImports().getImportInfo(type.qualifiedName()) != null)
                        ? type.name() : type.qualifiedName();
                    result.putIfAbsent(type.qualifiedName(), new SourceTypeCompletion(type.name(), insertion, type.pkg(), type.pkg().equals(currentPackage) ? 1 : 2));
                }
            }
            for (ClassTarget target : targets) {
                Path source = target.getJavaSourceFile().toPath().toAbsolutePath().normalize();
                String name = target.getIdentifierName();
                if (!covered.contains(source) && target.hasSourceCode() && target.getPackage().getQualifiedName().equals(currentPackage) && ProjectTypeIndex.validName(name))
                    result.putIfAbsent(target.getQualifiedName(), new SourceTypeCompletion(name, name, currentPackage, 1));
            }
        }
        for (Declaration type : own) addDeclaration(result, type, currentPackage, true, parser, currentOwner);
        if (own.isEmpty()) {
            String filename = current.completionFileBaseName();
            if (ProjectTypeIndex.validName(filename)) result.put((currentPackage.isEmpty() ? "" : currentPackage+".")+filename,
                new SourceTypeCompletion(filename, filename, currentPackage, 0));
        }
        List<SourceTypeCompletion> types = new ArrayList<>(result.values());
        if (types.size() > 256) {
            int end = current.getSourcePane().getCaretPosition(), start = end;
            var document = current.getSourcePane().getDocument();
            while (start > 0 && Character.isJavaIdentifierPart(document.getContent(start-1,start).charAt(0))) start--;
            String prefix = document.getContent(start,end).toString();
            return types.stream().filter(t -> prefix.isEmpty() || t.name.regionMatches(true,0,prefix,0,prefix.length()))
                .sorted(Comparator.comparingInt(SourceTypeCompletion::locality).thenComparing(SourceTypeCompletion::getName))
                .limit(256).map(t -> (AssistContent)t).toList();
        }
        return new ArrayList<>(types);
    }

    private static void addDeclaration(Map<String, SourceTypeCompletion> result, Declaration type, String currentPackage, boolean own, ParsedCUNode parser, ParsedTypeNode currentOwner)
    {
        if (!own && Modifier.isPrivate(type.modifiers())) return;
        if (own && Modifier.isPrivate(type.modifiers()) && !sameNest(currentOwner,type.node())) return;
        if (!own && type.pkg().isEmpty() && !currentPackage.isEmpty()) return;
        if (!own && !type.pkg().equals(currentPackage) && !Modifier.isPublic(type.modifiers())) return;
        String withinPackage = type.pkg().isEmpty() ? type.qualified() : type.qualified().substring(type.pkg().length()+1);
        String insertion = type.pkg().equals(currentPackage) ? withinPackage : type.qualified();
        if (parser != null && parser.getImports().getImportInfo(type.qualified()) != null) insertion = type.name();
        result.put(type.qualified(), new SourceTypeCompletion(type.name(), insertion, type.pkg(), own ? 0 : type.pkg().equals(currentPackage) ? 1 : 2));
    }

    private static boolean sameNest(ParsedTypeNode a, ParsedTypeNode b) {
        if (a == null || b == null) return false;
        while (a.getContainingClass() != null) a = a.getContainingClass();
        while (b.getContainingClass() != null) b = b.getContainingClass();
        return a == b;
    }
}
