package org.matsim.network;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.util.*;

/**
 * 改进版公交线路集成器：
 * 1. 读取公交站点与线路Shapefile
 * 2. 匹配线路到路网 link 序列
 * 3. 保存线路的 fullLinkPathIds 和 stop->link 映射
 * 4. 使用最近端点距离 + 局部方向角判断
 * 5. 含回退查找机制
 */
public class BusNetworkIntegrator {
    private final Network network;
    private final double angleThresholdDeg;
    private final double distanceThresholdMeter;

    // 线路 -> 链路ID路径
    private final Map<String, List<Id<Link>>> lineLinkPaths = new HashMap<>();
    // stopId -> linkId
    private final Map<String, Id<Link>> stopToLinkMapping = new HashMap<>();

    private final GeometryFactory gf = new GeometryFactory();

    public BusNetworkIntegrator(Network network, double angleThresholdDeg, double distanceThresholdMeter) {
        this.network = network;
        this.angleThresholdDeg = angleThresholdDeg;
        this.distanceThresholdMeter = distanceThresholdMeter;
    }

    /**
     * 入口：集成公交线路
     */
    public void integrateBusLines(String busStopShp, String busLineShp) {
        try {
            // Step1: 读取公交站点
            Map<String, Coordinate> busStops = readBusStops(busStopShp);

            // Step2: 读取公交线路（polyline）
            FileDataStore store = FileDataStoreFinder.getDataStore(new File(busLineShp));
            SimpleFeatureSource featureSource = store.getFeatureSource();
            SimpleFeatureIterator it = featureSource.getFeatures().features();

            while (it.hasNext()) {
                SimpleFeature f = it.next();
                String lineId = (String) f.getAttribute("line_name");
                String shortName = (String) f.getAttribute("short_name");
                Geometry geom = (Geometry) f.getDefaultGeometry();

                if (!(geom instanceof LineString)) continue;
                LineString line = (LineString) geom;

                // Step3: 逐段匹配为 link 序列
                List<Id<Link>> fullPath = new ArrayList<>();
                Coordinate[] coords = line.getCoordinates();

                for (int i = 0; i < coords.length - 1; i++) {
                    Coordinate start = coords[i];
                    Coordinate end   = coords[i+1];
                    Coordinate dir   = new Coordinate(end.x - start.x, end.y - start.y);

                    List<Coordinate> localDir = Collections.singletonList(dir);

                    // 尝试路径匹配（含回退）
                    List<Id<Link>> segPath = matchSegmentWithFallback(start, end, localDir);
                    fullPath.addAll(segPath);
                }

                // 去重（避免重复 link）
                LinkedHashSet<Id<Link>> dedup = new LinkedHashSet<>(fullPath);
                fullPath = new ArrayList<>(dedup);

                lineLinkPaths.put(lineId, fullPath);
            }
            it.close();
            store.dispose();

            // Step4: 建立 stop -> link 映射
            for (Map.Entry<String, Coordinate> e : busStops.entrySet()) {
                String stopId = e.getKey();
                Coordinate stopCoord = e.getValue();

                Link candidate = findCandidateLink(stopCoord, null);
                if (candidate != null) {
                    stopToLinkMapping.put(stopId, candidate.getId());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 用回退机制匹配一个线段
     */
    private List<Id<Link>> matchSegmentWithFallback(Coordinate start, Coordinate end, List<Coordinate> lineSegmentDirs) {
        // 1. 严格阈值
        List<Id<Link>> path = dijkstraPath(start, end, lineSegmentDirs, angleThresholdDeg, distanceThresholdMeter);
        if (!path.isEmpty()) return path;

        // 2. 放宽阈值
        path = dijkstraPath(start, end, lineSegmentDirs, angleThresholdDeg * 2, distanceThresholdMeter * 2);
        if (!path.isEmpty()) return path;

        // 3. synthetic link 补齐
        String synId = "SYN_" + start.x + "_" + start.y + "_" + end.x + "_" + end.y;
        return Collections.singletonList(Id.createLinkId(synId));
    }

    /**
     * 改进版路径搜索：基于最短距离 + 局部方向过滤
     */
    private List<Id<Link>> dijkstraPath(Coordinate startCoord, Coordinate endCoord,
                                        List<Coordinate> lineSegmentDirs,
                                        double angleThreshold, double distThreshold) {
        Link startLink = findCandidateLink(startCoord, lineSegmentDirs.get(0), angleThreshold, distThreshold);
        Link endLink   = findCandidateLink(endCoord,   lineSegmentDirs.get(0), angleThreshold, distThreshold);

        if (startLink == null || endLink == null) return Collections.emptyList();

        // TODO: 替换为真实 Dijkstra
        List<Link> pathLinks = runDijkstra(startLink.getFromNode(), endLink.getToNode());

        List<Id<Link>> linkIds = new ArrayList<>();
        for (Link l : pathLinks) {
            linkIds.add(l.getId());
        }
        return linkIds;
    }

    /**
     * 候选link选择逻辑：
     * - 点到线段的最短距离 < 阈值
     * - 与线路方向角差 < 阈值
     */
    private Link findCandidateLink(Coordinate coord, Coordinate dirVec) {
        return findCandidateLink(coord, dirVec, angleThresholdDeg, distanceThresholdMeter);
    }

    private Link findCandidateLink(Coordinate coord, Coordinate dirVec,
                                   double angleThres, double distThres) {
        Link bestLink = null;
        double bestScore = Double.MAX_VALUE;

        Vector2D lineDir = (dirVec != null) ? new Vector2D(dirVec) : null;

        for (Link link : network.getLinks().values()) {
            Coordinate from = toCoord(link.getFromNode());
            Coordinate to   = toCoord(link.getToNode());

            double dist = pointToSegmentDistance(coord, from, to);
            if (dist > distThres) continue;

            if (lineDir != null) {
                Vector2D linkDir = new Vector2D(to.x - from.x, to.y - from.y);
                double angle = lineDir.angleDiffDeg(linkDir);
                if (angle > angleThres) continue;
            }

            double score = dist;
            if (score < bestScore) {
                bestScore = score;
                bestLink = link;
            }
        }
        return bestLink;
    }

    private Coordinate toCoord(Node node) {
        return new Coordinate(node.getCoord().getX(), node.getCoord().getY());
    }

    private double pointToSegmentDistance(Coordinate p, Coordinate a, Coordinate b) {
        LineSegment seg = new LineSegment(a, b);
        return seg.distance(p);
    }

    /**
     * 假的 Dijkstra（请替换为真实实现）
     */
    private List<Link> runDijkstra(Node fromNode, Node toNode) {
        // TODO: 使用MATSim的Dijkstra或者AStar实现
        return new ArrayList<>();
    }

    // ===== 读取公交站点 =====
    private Map<String, Coordinate> readBusStops(String shpPath) throws Exception {
        Map<String, Coordinate> stops = new HashMap<>();

        FileDataStore store = FileDataStoreFinder.getDataStore(new File(shpPath));
        SimpleFeatureSource featureSource = store.getFeatureSource();
        SimpleFeatureIterator it = featureSource.getFeatures().features();

        while (it.hasNext()) {
            SimpleFeature f = it.next();
            String stopId = String.valueOf(f.getAttribute("id"));
            double lng = (double) f.getAttribute("lng");
            double lat = (double) f.getAttribute("lat");
            stops.put(stopId, new Coordinate(lng, lat));
        }
        it.close();
        store.dispose();

        return stops;
    }

    // ===== 导出结果 =====
    public Map<String, List<Id<Link>>> getLineLinkPaths() {
        return lineLinkPaths;
    }

    public Map<String, Id<Link>> getStopToLinkMapping() {
        return stopToLinkMapping;
    }

    // ===== 内部向量类 =====
    static class Vector2D {
        double x, y;
        Vector2D(Coordinate c) { this.x = c.x; this.y = c.y; }
        Vector2D(double x, double y) { this.x = x; this.y = y; }

        double norm() { return Math.sqrt(x*x + y*y); }
        double dot(Vector2D o) { return x*o.x + y*o.y; }

        double angleDiffDeg(Vector2D o) {
            double cos = dot(o) / (norm() * o.norm() + 1e-9);
            cos = Math.max(-1, Math.min(1, cos));
            return Math.toDegrees(Math.acos(cos));
        }
    }
}
