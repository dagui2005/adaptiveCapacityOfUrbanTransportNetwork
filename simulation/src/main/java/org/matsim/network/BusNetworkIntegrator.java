package org.matsim.network;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import java.io.File;
import java.util.*;

/**
 * BusNetworkIntegrator
 *
 * - 读取 stops/lines shapefile
 * - 将同一 line_name 的 stops 按 polyline 的投影顺序排序
 * - 在网络上使用 Dijkstra 找到 link 序列（或补充 synthetic link）
 *
 * 使用说明：
 *   BusNetworkIntegrator integrator = new BusNetworkIntegrator(network, coordDecimal, nodeSnapToleranceMeters);
 *   integrator.integrateBusLines(busStopsShp, busLinesShp);
 */
public class BusNetworkIntegrator {

    // ========== 配置参数（可调整） ==========
    private final int coordDecimal; // 修改点：合并节点时保留的小数位数（例如 5 表示保留 5 位）
    private final double nodeSnapTolerance; // m：站点与已有 node 的最大匹配距离，否则创建新 node (用于起终点定位)
    private final double maxLinkToLineDistance = 50.0; // m：link 与 bus line 的最大允许距离（用于过滤）
    private final double angleThresholdDeg = 45.0; // deg：用于匹配 link 方向与线方向（简化版）
    private final double projectionEpsilon = 1e-9;

    // network + helper maps
    private final Network network;
    private final Map<Id<Node>, Node> nodeById;
    private final Map<String, Id<Node>> nodeCoordKeyMap = new HashMap<>(); // key = rounded x_y -> nodeId (用于合并)
    private final Map<Id<Node>, List<Link>> outgoing = new HashMap<>();

    // synthetic counters
    private long syntheticNodeCounter = 1; // for NDBS...
    private long syntheticLinkCounter = 1; // for LKBS...

    // 图搜索临时数据
    private static class PQItem implements Comparable<PQItem> {
        final Id<Node> nodeId;
        final double dist;
        PQItem(Id<Node> nodeId, double dist) { this.nodeId = nodeId; this.dist = dist; }
        @Override public int compareTo(PQItem o) { return Double.compare(this.dist, o.dist); }
    }

    // ========== 构造 ==========
    public BusNetworkIntegrator(Network network, int coordDecimal, double nodeSnapToleranceMeters) {
        this.network = network;
        this.coordDecimal = coordDecimal;
        this.nodeSnapTolerance = nodeSnapToleranceMeters;
        this.nodeById = (Map<Id<Node>, Node>) network.getNodes();
        buildOutgoingIndex();
        buildInitialCoordMap();
    }

    // --- 修改点：初始化 outgoing 链接表（用于 Dijkstra） ---
    private void buildOutgoingIndex() {
        outgoing.clear();
        for (Link link : network.getLinks().values()) {
            Id<Node> from = link.getFromNode().getId();
            outgoing.computeIfAbsent(from, k -> new ArrayList<>()).add(link);
        }
    }

    // --- 修改点：用已有 network 中的节点构建 "坐标合并" 初始索引 ---
    private void buildInitialCoordMap() {
        for (Node n : network.getNodes().values()) {
            String key = roundedCoordKey(n.getCoord());
            nodeCoordKeyMap.putIfAbsent(key, n.getId());
        }
    }

    // ========== 主流程：读取 shapefile 并集成到 network ==========
    public void integrateBusLines(String busStopsShp, String busLinesShp) throws Exception {
        Map<String, List<StopPoint>> stopsByLine = loadStops(busStopsShp);
        List<BusLineGeom> lines = loadLines(busLinesShp);

        for (BusLineGeom line : lines) {
            List<StopPoint> stops = stopsByLine.get(line.lineName);
            if (stops == null || stops.size() < 2) {
                // 没有对应的 stops，跳过
                continue;
            }

            // 1) 在 polyline 上计算每个 stop 的投影距离（从起点开始的沿线距离），并排序
            for (StopPoint s : stops) {
                ProjectResult pr = projectPointOntoLine(line.geom, s.coord);
                s.projDist = pr != null ? pr.distanceAlong : Double.POSITIVE_INFINITY;
            }
            stops.sort(Comparator.comparingDouble(s -> s.projDist));

            // 2) 逐段（相邻站点对）寻找 network link 路径或补充 synthetic link
            List<String> fullLinkPathIds = new ArrayList<>();
            for (int i = 0; i < stops.size() - 1; i++) {
                StopPoint a = stops.get(i);
                StopPoint b = stops.get(i + 1);

                // 将 stop 坐标转换为 MATSim Coord (network 坐标系假设相同)
                Coord ca = a.coord;
                Coord cb = b.coord;

                // 先尝试找到 network 上可用节点：优先复用已有坐标相同 / 四舍五入后相同的节点
                Id<Node> startNode = findOrCreateNodeAt(ca);
                Id<Node> endNode = findOrCreateNodeAt(cb);

                // 使用 Dijkstra 在 network 上寻找最短路径（link id 列表）
                List<Link> path = dijkstraPath(startNode, endNode);

                if (path == null || path.isEmpty()) {
                    // 无可连通路径，退而补一条 synthetic link（起终点直接连线）
                    Id<Node> sNode = ensureNodeExists(startNode, ca); // 保证 network 中存在节点
                    Id<Node> eNode = ensureNodeExists(endNode, cb);

                    String syntheticId = createSyntheticLink(sNode, eNode, a, b, line);
                    fullLinkPathIds.add(syntheticId);
                } else {
                    // path 存在，把 path 的 link id 加到序列，并把 link 标记允许 bus 模式
                    for (Link l : path) {
                        markLinkWithBusMode(l);
                        fullLinkPathIds.add(l.getId().toString());
                    }
                }
            }

            // TODO: 你可以在这里保存每条公交线路对应的 fullLinkPathIds 到外部结构（例如写文件或内存结构）
            System.out.println("BUS LINE " + line.lineName + " matched links: " + fullLinkPathIds.size());
        }

        // rebuild outgoing index as we may have added synthetic links/nodes
        buildOutgoingIndex();
    }

    // ================= helper & small data-classes =================

    // Stop point with metadata
    private static class StopPoint {
        String id;
        String station;
        Coord coord;
        String lineName;
        double projDist = Double.POSITIVE_INFINITY;
    }

    // bus line geometry holder
    private static class BusLineGeom {
        String lineName;
        LineString geom;
    }

    /**
     * 读取 stops shapefile（只读取常用属性）
     */
    private Map<String, List<StopPoint>> loadStops(String stopsShp) throws Exception {
        Map<String, List<StopPoint>> byLine = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("url", new File(stopsShp).toURI().toURL());
        DataStore ds = DataStoreFinder.getDataStore(map);
        String typeName = ds.getTypeNames()[0];
        SimpleFeatureSource source = ds.getFeatureSource(typeName);

        try (var it = source.getFeatures().features()) {
            while (it.hasNext()) {
                org.opengis.feature.simple.SimpleFeature f = it.next();
                Object geom = f.getDefaultGeometry();
                if (!(geom instanceof Point)) continue;
                Point p = (Point) geom;
                StopPoint sp = new StopPoint();

                Object idAttr = f.getAttribute("id");
                sp.id = idAttr != null ? idAttr.toString() : UUID.randomUUID().toString();
                Object stationAttr = f.getAttribute("station");
                sp.station = stationAttr != null ? stationAttr.toString() : sp.id;
                // 注意：shp 中可能有 lng/lat 字段，也可能 geometry 本身是经纬或投影，请确保与 network CRS 一致
                sp.coord = new org.matsim.api.core.v01.Coord(p.getX(), p.getY());
                Object lineNameAttr = f.getAttribute("line_name");
                sp.lineName = lineNameAttr != null ? lineNameAttr.toString() : "UNK";

                byLine.computeIfAbsent(sp.lineName, k -> new ArrayList<>()).add(sp);
            }
        } finally {
            ds.dispose();
        }
        return byLine;
    }

    /**
     * 读取 bus line shapefile（polyline），只读取 line_name 属性与 geometry（如果是 MultiLineString，合并成第一个 LineString）
     */
    private List<BusLineGeom> loadLines(String linesShp) throws Exception {
        List<BusLineGeom> list = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("url", new File(linesShp).toURI().toURL());
        DataStore ds = DataStoreFinder.getDataStore(map);
        String typeName = ds.getTypeNames()[0];
        SimpleFeatureSource source = ds.getFeatureSource(typeName);

        try (var it = source.getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                Object geom = f.getDefaultGeometry();
                LineString ls = null;
                if (geom instanceof LineString) {
                    ls = (LineString) geom;
                } else if (geom instanceof MultiLineString) {
                    // take the first part (simple approach)
                    MultiLineString mls = (MultiLineString) geom;
                    if (mls.getNumGeometries() > 0) {
                        ls = (LineString) mls.getGeometryN(0);
                    }
                }
                if (ls == null) continue;
                BusLineGeom bl = new BusLineGeom();
                Object lineNameAttr = f.getAttribute("line_name");
                bl.lineName = lineNameAttr != null ? lineNameAttr.toString() : UUID.randomUUID().toString();
                bl.geom = ls;
                list.add(bl);
            }
        } finally {
            ds.dispose();
        }
        return list;
    }

    // --- 投影点到 line，返回沿线距离（从起点开始） ---
    private static class ProjectResult { double distanceAlong; Coordinate projected; }
    private ProjectResult projectPointOntoLine(LineString line, Coord p) {
        Coordinate[] segs = line.getCoordinates();
        double cum = 0.0;
        double bestDist = Double.POSITIVE_INFINITY;
        ProjectResult best = null;

        for (int i = 0; i < segs.length - 1; i++) {
            Coordinate a = segs[i];
            Coordinate b = segs[i + 1];
            double segLen = a.distance(b);
            // project point p onto segment a-b
            double vx = b.x - a.x, vy = b.y - a.y;
            double wx = p.getX() - a.x, wy = p.getY() - a.y;
            double segLen2 = vx * vx + vy * vy;
            double t = (segLen2 > 0) ? (vx * wx + vy * wy) / segLen2 : 0.0;
            t = Math.max(0.0, Math.min(1.0, t));
            double projx = a.x + t * vx;
            double projy = a.y + t * vy;
            double dist = Math.hypot(projx - p.getX(), projy - p.getY());
            double distAlong = cum + t * Math.sqrt(segLen2);

            if (dist < bestDist) {
                bestDist = dist;
                best = new ProjectResult();
                best.distanceAlong = distAlong;
                best.projected = new Coordinate(projx, projy);
            }
            cum += segLen;
        }
        return best;
    }

    // ========== 节点合并与创建逻辑 ==========
    // 修改点：将坐标四舍五入为 coordDecimal 位，返回 key 字符串
    private String roundedCoordKey(Coord c) {
        double scale = Math.pow(10, coordDecimal);
        double x = Math.round(c.getX() * scale) / scale;
        double y = Math.round(c.getY() * scale) / scale;
        return x + "_" + y;
    }

    // 尝试寻找已有 node（rounding key）或最接近 node（在 nodeSnapTolerance 内），否则创建新的 node
    private Id<Node> findOrCreateNodeAt(Coord c) {
        String key = roundedCoordKey(c);
        Id<Node> existing = nodeCoordKeyMap.get(key);
        if (existing != null) return existing;

        // 找最近节点（遍历所有 node，选择最小距离）
        Node nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Node n : network.getNodes().values()) {
            double d = calcDist(n.getCoord(), c);
            if (d < best) {
                best = d;
                nearest = n;
            }
        }
        if (nearest != null && best <= nodeSnapTolerance) {
            // 复用 nearest
            nodeCoordKeyMap.put(key, nearest.getId());
            return nearest.getId();
        } else {
            // 创建新节点（synthetic node NDBS...）
            Id<Node> newId = Id.createNodeId("NDBS" + String.format("%011d", syntheticNodeCounter++)); // NDBS + 11 位
            Node nn = network.getFactory().createNode(newId, c);
            network.addNode(nn);
            nodeCoordKeyMap.put(key, newId);
            return newId;
        }
    }

    // Ensure the nodeId exists in network; if not, create node at coord
    private Id<Node> ensureNodeExists(Id<Node> maybeId, Coord c) {
        if (maybeId != null && network.getNodes().containsKey(maybeId)) return maybeId;
        // otherwise create new
        Id<Node> newId = Id.createNodeId("NDBS" + String.format("%011d", syntheticNodeCounter++));
        Node n = network.getFactory().createNode(newId, c);
        network.addNode(n);
        nodeCoordKeyMap.put(roundedCoordKey(c), newId);
        return newId;
    }

    // 计算欧式距离
    private double calcDist(org.matsim.api.core.v01.Coord a, Coord b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.hypot(dx, dy);
    }

    // ========== Dijkstra shortest path on network (by node ids) ==========
    // 返回 Link 列表（从 start 到 end），找不到返回 null
    private List<Link> dijkstraPath(Id<Node> startId, Id<Node> endId) {
        if (startId == null || endId == null) return null;
        if (!network.getNodes().containsKey(startId) || !network.getNodes().containsKey(endId)) return null;
        if (startId.equals(endId)) return Collections.emptyList();

        Map<Id<Node>, Double> dist = new HashMap<>();
        Map<Id<Node>, Link> prevLink = new HashMap<>();
        PriorityQueue<PQItem> pq = new PriorityQueue<>();
        dist.put(startId, 0.0);
        pq.add(new PQItem(startId, 0.0));

        while (!pq.isEmpty()) {
            PQItem cur = pq.poll();
            if (cur.dist > dist.getOrDefault(cur.nodeId, Double.POSITIVE_INFINITY)) continue;
            if (cur.nodeId.equals(endId)) break;

            List<Link> outs = outgoing.getOrDefault(cur.nodeId, Collections.emptyList());
            for (Link l : outs) {
                Id<Node> to = l.getToNode().getId();
                double nd = cur.dist + l.getLength();
                if (nd < dist.getOrDefault(to, Double.POSITIVE_INFINITY)) {
                    dist.put(to, nd);
                    prevLink.put(to, l);
                    pq.add(new PQItem(to, nd));
                }
            }
        }

        if (!dist.containsKey(endId)) return null; // not reachable

        // rebuild path by backtracking prevLink
        LinkedList<Link> path = new LinkedList<>();
        Id<Node> cur = endId;
        while (!cur.equals(startId)) {
            Link l = prevLink.get(cur);
            if (l == null) break;
            path.addFirst(l);
            cur = l.getFromNode().getId();
        }
        return path;
    }

    // ========== 合成 link / 标记 link 模式等 ==========
    // 标记 link 允许 bus（这里将 modes 存到 link attributes）
    private void markLinkWithBusMode(Link link) {
        try {
            // 首先尝试读已有 modes 属性并合并
            Object old = link.getAttributes().getAttribute("modes");
            String newModes;
            if (old != null && old.toString().length() > 0) {
                String s = old.toString();
                Set<String> set = new LinkedHashSet<>();
                for (String part : s.split(",")) if (part.trim().length() > 0) set.add(part.trim());
                set.add("bus");
                newModes = String.join(",", set);
            } else {
                newModes = "bus,car";
            }
            link.getAttributes().putAttribute("modes", newModes);
        } catch (Exception e) {
            // ignore
        }
    }

    // 创建一条 synthetic link（直接按起终点直线）
    // 返回新 link id 字符串
    private String createSyntheticLink(Id<Node> fromNodeId, Id<Node> toNodeId, StopPoint a, StopPoint b, BusLineGeom line) {
        // create forward link
        String linkIdStr = "LKBS" + String.format("%011d", syntheticLinkCounter++);
        Id<Link> linkId = Id.createLinkId(linkIdStr);
        Node fromNode = network.getNodes().get(fromNodeId);
        Node toNode = network.getNodes().get(toNodeId);

        // length use euclidean distance in network coords
        double length = org.matsim.core.utils.geometry.CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord());
        Link l = network.getFactory().createLink(linkId, fromNode, toNode);
        l.setLength(length);
        l.setFreespeed(20.0);
        l.setNumberOfLanes(1.0);
        l.setCapacity(9999.0);
        l.getAttributes().putAttribute("modes", "bus,artificial");
        network.addLink(l);

        // create backward for two-way (optional - follow earlier spec: both directions maybe)
        // 如果你希望只有单向，请删除以下代码
        String backIdStr = "LKBS" + String.format("%011d", syntheticLinkCounter++);
        Id<Link> backId = Id.createLinkId(backIdStr);
        Link back = network.getFactory().createLink(backId, toNode, fromNode);
        back.setLength(length);
        back.setFreespeed(20.0);
        back.setNumberOfLanes(1.0);
        back.setCapacity(9999.0);
        back.getAttributes().putAttribute("modes", "bus,artificial");
        network.addLink(back);

        return linkIdStr;
    }
}
