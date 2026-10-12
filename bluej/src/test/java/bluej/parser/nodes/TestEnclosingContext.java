/* Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-12. GPLv2 with Classpath Exception. */
package bluej.parser.nodes;

import bluej.parser.JavaParser;
import bluej.parser.lexer.JavaLexer;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestEnclosingContext
{
    private static CommentNode comment(ParsedNode parent)
    {
        return new CommentNode(parent, new JavaLexer(new StringReader("/* comment */")).nextToken());
    }

    @Test(timeout = 2000) public void topLevelCommentHasNoEnclosingMethodOrClass()
    {
        ParsedCUNode root = new ParsedCUNode(null);
        root.getNodeTree().insertNode(comment(root), 0, 13);
        assertNull(root.getContainingMethodOrClassNode(1));
    }

    @Test(timeout = 2000) public void topLevelCommentIsNotAControlStatement()
    {
        ParsedCUNode root = new ParsedCUNode(null);
        root.getNodeTree().insertNode(comment(root), 0, 13);
        assertFalse(root.isCurrentlyInControlStatement(1));
    }

    @Test(timeout = 2000) public void classHeaderWithoutAnEnclosingTypeTerminates()
    {
        ParsedCUNode root = new ParsedCUNode(null);
        ParsedTypeNode type = new ParsedTypeNode(root, null, JavaParser.TYPEDEF_CLASS, "", 0);
        type.setName("School");
        root.getNodeTree().insertNode(type, 0, 40);
        assertNull(root.getContainingMethodOrClassNode(0));
        assertFalse(root.isCurrentlyInControlStatement(0));
    }

    @Test(timeout = 2000) public void emptyRootHasNoContext()
    {
        ParsedCUNode root = new ParsedCUNode(null);
        assertNull(root.getContainingMethodOrClassNode(0));
        assertFalse(root.isCurrentlyInControlStatement(0));
    }

    @Test(timeout = 2000) public void nearestNamedMethodAndClassArePreserved()
    {
        ParsedCUNode root = new ParsedCUNode(null);
        ParsedTypeNode type = new ParsedTypeNode(root, null, JavaParser.TYPEDEF_CLASS, "", 0);
        type.setName("School");
        InnerNode classBody = new InnerNode(type);
        MethodNode method = new MethodNode(classBody, "answer", "");
        InnerNode methodBody = new InnerNode(method);
        assertSame(method, comment(methodBody).getContainingMethodOrClassNode(1));
        assertSame(type, comment(classBody).getContainingMethodOrClassNode(1));
        assertFalse(comment(methodBody).isCurrentlyInControlStatement(1));
    }
}
