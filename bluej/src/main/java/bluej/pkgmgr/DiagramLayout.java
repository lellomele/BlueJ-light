/* BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained. */
package bluej.pkgmgr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.PortConstraints;
import org.eclipse.elk.core.options.PortSide;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.ElkPort;
import org.eclipse.elk.graph.util.ElkGraphUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

/** Pure layout data keeps JavaFX nodes and the live project off the worker thread. */
@OnThread(Tag.Any)
public final class DiagramLayout
{
    @OnThread(Tag.Any) public record Node(String id, double width, double height) {}
    @OnThread(Tag.Any) public record Edge(String id, String from, String to, double sourceX, double targetX) {}
    @OnThread(Tag.Any) public record Point(double x, double y) {}
    @OnThread(Tag.Any) public record Result(Map<String, Point> positions, Map<String, List<Point>> routes) {}
    @OnThread(Tag.Any) public record Box(double x, double y, double width, double height) {}

    public static Map<String, Point> avoidObstacles(List<Node> nodes, Map<String, Point> preferred, List<Box> fixed)
    {
        List<Box> occupied = new ArrayList<>(fixed);
        Map<String, Point> result = new LinkedHashMap<>();
        for (Node node : nodes)
        {
            Point start = preferred.get(node.id());
            double x = Math.max(30, start.x()), y = Math.max(30, start.y());
            Box candidate = new Box(x, y, node.width(), node.height());
            if (collides(candidate, occupied))
            {
                // Search free cells from the top; fixed targets are never relocated.
                boolean found = false;
                double maxBottom = occupied.stream().mapToDouble(box -> box.y() + box.height()).max().orElse(30);
                for (double cy = 30; cy <= Math.min(1200, maxBottom + 40) && !found; cy += 20)
                    for (double cx = 30; cx <= 1200 && !found; cx += 20)
                    {
                        Box cell = new Box(cx, cy, node.width(), node.height());
                        if (!collides(cell, occupied)) { candidate = cell; found = true; }
                    }
                if (!found) candidate = new Box(30, maxBottom + 30, node.width(), node.height());
            }
            occupied.add(candidate);
            result.put(node.id(), new Point(candidate.x(), candidate.y()));
        }
        return Map.copyOf(result);
    }

    private static boolean collides(Box box, List<Box> occupied)
    {
        return occupied.stream().anyMatch(other -> box.x() < other.x() + other.width() + 20
            && box.x() + box.width() + 20 > other.x() && box.y() < other.y() + other.height() + 20
            && box.y() + box.height() + 20 > other.y());
    }

    public static Result arrange(List<Node> nodes, List<Edge> edges)
    {
        ElkNode graph = ElkGraphUtil.createGraph();
        graph.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
        graph.setProperty(CoreOptions.DIRECTION, Direction.UP);
        graph.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
        graph.setProperty(CoreOptions.SPACING_NODE_NODE, 40.0);
        graph.setProperty(CoreOptions.SPACING_EDGE_NODE, 20.0);
        graph.setProperty(CoreOptions.SPACING_EDGE_EDGE, 12.0);
        Map<String, ElkNode> graphNodes = new LinkedHashMap<>();
        for (Node item : nodes)
        {
            ElkNode node = ElkGraphUtil.createNode(graph);
            node.setIdentifier(item.id());
            node.setWidth(item.width());
            node.setHeight(item.height());
            node.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);
            graphNodes.put(item.id(), node);
        }
        Map<String, ElkEdge> graphEdges = new LinkedHashMap<>();
        for (Edge item : edges)
        {
            ElkNode from = graphNodes.get(item.from());
            ElkNode to = graphNodes.get(item.to());
            if (from == null || to == null) continue;
            ElkPort source = ElkGraphUtil.createPort(from);
            source.setX(item.sourceX());
            source.setY(0);
            source.setProperty(CoreOptions.PORT_SIDE, PortSide.NORTH);
            ElkPort target = ElkGraphUtil.createPort(to);
            target.setX(item.targetX());
            target.setY(to.getHeight());
            target.setProperty(CoreOptions.PORT_SIDE, PortSide.SOUTH);
            ElkEdge edge = ElkGraphUtil.createSimpleEdge(source, target);
            edge.setIdentifier(item.id());
            graphEdges.put(item.id(), edge);
        }
        new RecursiveGraphLayoutEngine().layout(graph, new BasicProgressMonitor());
        Map<String, Point> positions = new LinkedHashMap<>();
        graphNodes.forEach((id, node) -> positions.put(id, new Point(node.getX(), node.getY())));
        Map<String, List<Point>> routes = new LinkedHashMap<>();
        graphEdges.forEach((id, edge) -> {
            if (edge.getSections().isEmpty()) return;
            var section = edge.getSections().getFirst();
            List<Point> path = new ArrayList<>();
            path.add(new Point(section.getStartX(), section.getStartY()));
            section.getBendPoints().forEach(p -> path.add(new Point(p.getX(), p.getY())));
            path.add(new Point(section.getEndX(), section.getEndY()));
            routes.put(id, List.copyOf(path));
        });
        return new Result(Map.copyOf(positions), Map.copyOf(routes));
    }
}
