/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.pkgmgr;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.Any)
public class TestDiagramLayout
{
    @Test public void boxesDoNotOverlapAndRoutesAvoidOtherBoxes()
    {
        List<DiagramLayout.Node> nodes = List.of(new DiagramLayout.Node("Base", 100, 60),
            new DiagramLayout.Node("Child", 230, 60), new DiagramLayout.Node("Helper", 100, 60),
            new DiagramLayout.Node("Other", 180, 80));
        List<DiagramLayout.Edge> edges = List.of(new DiagramLayout.Edge("inherit", "Child", "Base", 50, 50),
            new DiagramLayout.Edge("use", "Child", "Helper", 180, 50),
            new DiagramLayout.Edge("back", "Helper", "Child", 50, 50));
        var result = DiagramLayout.arrange(nodes, edges);
        assertEquals(nodes.size(), result.positions().size());
        assertEquals(edges.size(), result.routes().size());
        for (int i = 0; i < nodes.size(); i++)
            for (int j = i + 1; j < nodes.size(); j++)
            {
                var a = nodes.get(i); var b = nodes.get(j);
                var ap = result.positions().get(a.id()); var bp = result.positions().get(b.id());
                assertFalse(a.id() + " overlaps " + b.id(), ap.x() < bp.x() + b.width()
                    && ap.x() + a.width() > bp.x() && ap.y() < bp.y() + b.height() && ap.y() + a.height() > bp.y());
            }
        for (var edge : edges)
        {
            var route = result.routes().get(edge.id());
            assertTrue(route.size() >= 2);
            for (int segment = 1; segment < route.size(); segment++)
            {
                var a = route.get(segment - 1); var b = route.get(segment);
                assertTrue(a.x() == b.x() || a.y() == b.y());
                for (var node : nodes)
                {
                    if (node.id().equals(edge.from()) || node.id().equals(edge.to())) continue;
                    var p = result.positions().get(node.id());
                    boolean passes = a.x() == b.x()
                        ? a.x() > p.x() && a.x() < p.x() + node.width() && Math.max(a.y(), b.y()) > p.y() && Math.min(a.y(), b.y()) < p.y() + node.height()
                        : a.y() > p.y() && a.y() < p.y() + node.height() && Math.max(a.x(), b.x()) > p.x() && Math.min(a.x(), b.x()) < p.x() + node.width();
                    assertFalse("Route passes through " + node.id(), passes);
                }
            }
        }
        assertEquals(result, DiagramLayout.arrange(nodes, edges));
    }

    @Test public void handlesDisconnectedNodesAndSelfLoops()
    {
        List<DiagramLayout.Node> nodes = new ArrayList<>();
        for (int i = 0; i < 60; i++) nodes.add(new DiagramLayout.Node("N" + i, 100 + i % 4 * 30, 60));
        var result = DiagramLayout.arrange(nodes, List.of(new DiagramLayout.Edge("self", "N0", "N0", 50, 50)));
        assertEquals(60, result.positions().size());
        assertTrue(result.routes().get("self").size() >= 4);
    }
}
