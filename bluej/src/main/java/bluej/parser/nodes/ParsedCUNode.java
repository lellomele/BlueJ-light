/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-11-24. GPLv2 with Classpath Exception; original notices retained. */
/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2010,2019  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.parser.nodes;

import bluej.debugger.gentype.GenTypeClass;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.Reflective;
import bluej.parser.ImportsCollection;
import bluej.parser.JavaParser;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.PackageOrClass;
import bluej.parser.entity.TypeEntity;
import bluej.parser.nodes.NodeTree.NodeAndPosition;
import bluej.utility.JavaNames;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;


/**
 * A parsed compilation unit node.
 * 
 * @author Davin McCall
 */
public class ParsedCUNode extends IncrementalParsingNode
{
    private final EntityResolver parentResolver;
    private final ImportsCollection imports = new ImportsCollection();
    private long typeScopeRevision;

    @Override
    protected void typeScopeChanged()
    {
        typeScopeRevision++;
    }

    @Override
    protected void childRemoved(NodeAndPosition<ParsedNode> child, NodeStructureListener listener)
    {
        if (child.getNode() instanceof ImportNode declaration)
            imports.removeImport(declaration.getTerminator());
        super.childRemoved(child, listener);
    }

    private int size = 0;
    
    /**
     * Construct a parsed node for as yet unknown source.
     */
    public ParsedCUNode(EntityResolver parentResolver)
    {
        super(null);
        this.parentResolver = parentResolver;
    }

    public ImportsCollection getImports()
    {
        return imports;
    }

    /**
     * Checks the existing parse tree for a classic Java entry point declared by
     * the named top-level type. The caller supplies the simple name of its target.
     */
    public boolean hasMainMethod(String simpleClassName)
    {
        if (!(getTypeNode(simpleClassName) instanceof ParsedTypeNode type) || type.getInner() == null)
            return false;

        Set<MethodNode> candidates = type.getInner().getMethods().get("main");
        if (candidates == null)
            return false;

        for (MethodNode method : candidates) {
            int modifiers = method.getModifiers();
            boolean isPublic = Modifier.isPublic(modifiers)
                || (type.getTypeKind() == JavaParser.TYPEDEF_INTERFACE && !Modifier.isPrivate(modifiers));
            if (!isPublic || !Modifier.isStatic(modifiers) || !method.isComplete() || method.getParamTypes().size() != 1)
                continue;

            // The parser represents void with a primitive type, and varargs as arrays.
            JavaEntity result = method.getReturnType();
            if (result == null || result.getType() == null || !result.getType().isVoid())
                continue;

            method.refreshParameterScope(typeScopeRevision);
            TypeEntity parameter = method.getParamTypes().get(0).resolveAsType();
            JavaType component = parameter == null ? null : parameter.getType().getArrayComponent();
            GenTypeClass componentClass = component == null ? null : component.asClass();
            if (componentClass != null && "java.lang.String".equals(componentClass.classloaderName()))
                return true;
        }
        return false;
    }
    
    public EntityResolver getParentResolver()
    {
        return parentResolver;
    }
    
    /**
     * Overridden getSize() which returns the document size.
     * 
     * @see bluej.parser.nodes.ParsedNode#getSize()
     */
    public int getSize()
    {
        return size;
    }

    @Override
    public void resize(int newSize)
    {
        size = newSize;
    }
    
    @Override
    public void setSize(int newSize)
    {
        size = newSize;
    }
    
    @Override
    protected int doPartialParse(ParseParams params, int state)
    {
        last = params.tokenStream.LA(1);
       
        if (checkBoundary(params, last)) {
            return PP_PULL_UP_CHILD;
        }
        
        params.parser.parseCUpart(state);
        return PP_OK;
    };
    
    @Override
    protected boolean isDelimitingNode(NodeAndPosition<ParsedNode> nap)
    {
        // All node types: package statement, import (Inner), type definition,
        // are all delimiting nodes. Only a comment is not.
        int nt = nap.getNode().getNodeType();
        return nt != ParsedNode.NODETYPE_COMMENT;
    }
    
    @Override
    protected boolean isNodeEndMarker(int tokenType)
    {
        return false;
    }
    
    @Override
    protected boolean marksOwnEnd()
    {
        return true;
    }
    
    @Override
    public PackageOrClass resolvePackageOrClass(String name, Reflective querySource)
    {
        PackageOrClass poc = super.resolvePackageOrClass(name, querySource);
        if (poc == null) {
            poc = imports.getTypeImport(name);
        }
        if (poc == null && parentResolver != null && querySource != null) {
            String prefix = JavaNames.getPrefix(querySource.getName());
            String fqName = JavaNames.combineNames(prefix, name);
            poc = parentResolver.resolveQualifiedClass(fqName);
        }
        if (poc == null) {
            poc = imports.getTypeImportWC(name);
        }
        if (poc == null && parentResolver != null) {
            // Implicit "import java.lang.*"
            poc = parentResolver.resolveQualifiedClass("java.lang." + name);
        }
        if (poc == null && parentResolver != null) {
            return parentResolver.resolvePackageOrClass(name, querySource);
        }
        return poc;
    }
    
    @Override
    public JavaEntity getValueEntity(String name, Reflective querySource)
    {
        // We may have static imports
        
        List<JavaEntity> simports = imports.getStaticImports(name);
        for (JavaEntity importType : simports) {
            importType = importType.resolveAsType();
            if (importType == null) {
                continue;
            }
            JavaEntity subEnt = importType.getSubentity(name, querySource);
            if (subEnt != null) {
                JavaEntity value = subEnt.resolveAsValue();
                if (value != null) {
                    return value;
                }
            }
        }
        
        simports = imports.getStaticWildcardImports();
        for (JavaEntity importType : simports) {
            importType = importType.resolveAsType();
            if (importType == null) {
                continue;
            }
            JavaEntity subEnt = importType.getSubentity(name, querySource);
            if (subEnt != null) {
                JavaEntity value = subEnt.resolveAsValue();
                if (value != null) {
                    return value;
                }
            }
        }
        
        return resolvePackageOrClass(name, querySource);
    }
    
    @Override
    public TypeEntity resolveQualifiedClass(String name)
    {
        if (parentResolver != null) {
            return parentResolver.resolveQualifiedClass(name);
        }
        return null;
    }
    
//    public static void printTree(ParsedNode node, int nodepos, int indent)
//    {
//        for (int i = 0; i < indent; i++) {
//            System.out.print("  ");
//        }
//        System.out.println("Node=" + node + " pos=" + nodepos + " size=" + node.getSize());
//        for (Iterator<NodeAndPosition<ParsedNode>> i = node.getChildren(nodepos); i.hasNext(); ) {
//            NodeAndPosition<ParsedNode> nap = i.next();
//            printTree(nap.getNode(), nap.getPosition(), indent+1);
//        }
//    }
}
