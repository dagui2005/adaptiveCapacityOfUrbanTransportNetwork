package org.matsim.network;

import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.*;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.linearref.LinearLocation;
import org.locationtech.jts.linearref.LocationIndexedLine;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.speedy.*;
import org.matsim.core.router.util.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.File;
import java.util.*;

/**
 * BusNetworkIntegrator
 * 用于从公交线路 shp 与站点 shp 构建公交线路网络，自动识别线路方向、按方向顺序匹配站点，并加入 network。
 * 主要功能：
 *  1. 读取公交线路 Shapefile；
 *  2. 读取公交站点 Shapefile；
 *  3. 基于线路几何方向匹配站点；
 *  4. 生成公交专用 link（添加 bus 模式），并支持路径方向判断；
 *  5. 自动吸附最近节点（支持距离阈值）。
 */

public class BusNetworkIntegrator {

    private final Network network;
    private final double angleThresholdDeg;
    private final double distanceThresholdMeter;

//    private final Map<String, List<Id<Link>>> lineLinkPaths = new HashMap<>();
    private final Map<String, Id<Link>> stopToLinkMapping = new HashMap<>();
    private final Map<String, BusLinePathInfo> linePathInfos = new HashMap<>();

    private String networkCRS;
    private MathTransform transformToNetworkCRS;

    public BusNetworkIntegrator(Network network, double angleThresholdDeg, double distanceThresholdMeter, String networkCRS) {
        this.network = network;
        this.angleThresholdDeg = angleThresholdDeg;
        this.distanceThresholdMeter = distanceThresholdMeter;
        this.networkCRS = networkCRS;
    }

    /**
     * === 主流程 ===
     * 自动读取公交线 + 公交站 shapefile，融合进 MATSim 网络
     */
        /**
     * 执行公交线路融合流程，将公交线路与网络进行匹配并生成线路路径。
     *
     * @param busStopShp       公交站点 Shapefile 文件路径，用于读取各线路的站点信息。
     * @param busLineShp       公交线路 Shapefile 文件路径，包含线路几何信息。
     * @param outputNetworkPath 输出网络文件路径（当前函数中未使用，可能用于后续处理）。
     */
    public void integrateBusLines(String busStopShp, String busLineShp, String outputNetworkPath) {
        try {
            System.out.println("=== [BusNetworkIntegrator] 公交线融合流程启动 ===");
            System.out.println("[1] 加载公交线 shapefile: " + busLineShp);

            // 加载公交线路 Shapefile 数据源
            SimpleFeatureSource lineFeatureSource = FileDataStoreFinder.getDataStore(new File(busLineShp)).getFeatureSource();
            setupCoordinateTransform(lineFeatureSource);

            // 按线路分组读取公交站点数据
            Map<String, List<BusStop>> stopsByLine = readBusStopsGroupedByLine(busStopShp);
            SimpleFeatureIterator it = lineFeatureSource.getFeatures().features();

            // 遍历每条公交线路，进行几何处理和路径匹配
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                String lineId = String.valueOf(f.getAttribute("line_name"));
                Geometry geom = (Geometry) f.getDefaultGeometry();
                // === CRS转换 ===
                if (transformToNetworkCRS != null) {
                    try {
                        geom = JTS.transform(geom, transformToNetworkCRS);
                    } catch (Exception ex) {
                        System.err.println("[Warning] CRS transform failed for " + lineId + ": " + ex.getMessage());
                    }
                }

                // 只处理 MultiLineString 类型的几何对象
                if (!(geom instanceof MultiLineString)) continue;
                MultiLineString multiLine = (MultiLineString) geom;

                // 按顺序匹配站点到线路几何, 并保存方向角
                sortStopsAlongLineGeometry(lineId, multiLine.getGeometryN(0), stopsByLine);

                // 过滤可匹配站点 + 路径连接
                Iterator<BusStop> iter = stopsByLine.get(lineId).iterator();
                while (iter.hasNext()) {
                    BusStop stop = iter.next();
                    Link nearestLink = findNearestLink(stop.coord, distanceThresholdMeter, stop.directionDeg, angleThresholdDeg);
                    if (nearestLink == null) {
                        iter.remove();
                    } else {
                        stop.nearestLink = nearestLink;
                        stopToLinkMapping.put(stop.id, nearestLink.getId());
                    }
                }
                //todo: 添加路径连接逻辑
                //todo：（1）从当前lineName的stopsByLine（已删除不匹配link的站点）的第一个stop对应的nearestLink开始匹配到下一个stop的nearestLink的最短路径，加入fullPath
                //todo：（2）对fullPath中的link，增加 bus 模式AllowedModes。
                List<Id<Link>> fullPath = new ArrayList<>();
                List<Integer> stopLinkPositions = new ArrayList<>(); // 记录站点在路径中的位置

// 记录起始站点位置
                if (!stopsByLine.get(lineId).isEmpty()) {
                    stopLinkPositions.add(fullPath.size()); // 起始站点位置
                }

                for(int i = 0; i < stopsByLine.get(lineId).size() - 1; i++){
                    BusStop currentStop = stopsByLine.get(lineId).get(i);
                    BusStop nextStop = stopsByLine.get(lineId).get(i + 1);
                    List<Id<Link>> linkPath = dijkstraPath(currentStop.nearestLink, nextStop.nearestLink);
                    fullPath.addAll(linkPath);
                    // 记录下一站点在完整路径中的位置
                    stopLinkPositions.add(fullPath.size());
                }

                // 保存线路对应的完整路径
//                lineLinkPaths.put(lineId, fullPath);
                linePathInfos.put(lineId, new BusLinePathInfo(fullPath, stopLinkPositions));
                System.out.println("[Bus] Line " + lineId + " 完成匹配，路径长度：" + fullPath.size());
            }
            it.close();

            new NetworkWriter(network).write(outputNetworkPath);
            System.out.println("✅ 已写出融合后的 network: " + outputNetworkPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sortStopsAlongLineGeometry(String lineId, Geometry geom, Map<String, List<BusStop>> stopsByLine) {

        // 获取并排序该线路的站点
        for (BusStop stop : stopsByLine.get(lineId)) {
            // 投影站点到线路几何
            LocationIndexedLine lil = new LocationIndexedLine(geom);
            LinearLocation projectedLoc = lil.project(stop.coord);
//            stop.distAlong = projectedLoc.getSegmentFraction(); // 正确赋值 LinearLocation
            stop.distAlong = projectedLoc.getSegmentIndex() + projectedLoc.getSegmentFraction();

        }
        stopsByLine.get(lineId).sort(Comparator.comparingDouble(s -> s.distAlong));

        // 计算每个公交站的方向角
        //用于后续匹配link方向时判断夹角最小者。
        // 计算每个公交站的方向角
//用于后续匹配link方向时判断夹角最小者。
        for (BusStop stop : stopsByLine.get(lineId)) {
            LocationIndexedLine lil = new LocationIndexedLine(geom);
            LinearLocation loc = lil.project(stop.coord);

            try {
                // 直接获取线段的起点和终点来计算方向
                Coordinate[] coords = geom.getCoordinates();
                if (coords.length >= 2) {
                    // 找到投影点附近的两个坐标点
                    int segmentIndex = loc.getSegmentIndex();
                    if (segmentIndex < coords.length - 1) {
                        Coordinate c1 = coords[segmentIndex];
                        Coordinate c2 = coords[segmentIndex + 1];
                        stop.directionDeg = Math.toDegrees(Math.atan2(c2.y - c1.y, c2.x - c1.x));
                    } else {
                        // 使用最后两个点
                        Coordinate c1 = coords[coords.length - 2];
                        Coordinate c2 = coords[coords.length - 1];
                        stop.directionDeg = Math.toDegrees(Math.atan2(c2.y - c1.y, c2.x - c1.x));
                    }
                }
            } catch (IllegalStateException e) {
                // 处理零长度线段的情况
                // 使用几何对象起点和终点来计算方向
                Coordinate[] coords = geom.getCoordinates();
                if (coords.length >= 2) {
                    Coordinate first = coords[0];
                    Coordinate last = coords[coords.length - 1];
                    stop.directionDeg = Math.toDegrees(Math.atan2(last.y - first.y, last.x - first.x));
                } else {
                    // 如果只有一个坐标点，则设置默认方向为0度
                    stop.directionDeg = 0.0;
                }
            }
        }

    }



    /**
     * === 改进版 Dijkstra 路径计算（含端点吸附逻辑）===
     */
    private List<Id<Link>> dijkstraPath(Link startLink, Link endLink) {
        // 创建不限制模式的 SpeedyDijkstra
        TravelTime tt = (link, t, p, v) -> link.getLength() / link.getFreespeed();
        TravelDisutility td = new OnlyTimeDependentTravelDisutility(tt);
        SpeedyGraph speedyGraph = new SpeedyGraphBuilder().build(network);
        LeastCostPathCalculator router = new SpeedyDijkstra(speedyGraph, tt, td);

        List<Id<Link>> bestPath = new ArrayList<>();
        LeastCostPathCalculator.Path path = router.calcLeastCostPath(
                startLink.getFromNode(), endLink.getToNode(), 0, null, null);

        if (path == null || path.links == null || path.links.isEmpty()) {
            System.err.println("⚠️ Dijkstra 未找到路径: from=" + startLink.getId() + " to=" + endLink.getId());
            return Collections.emptyList();
        }

        for (Link l : path.links) {
            bestPath.add(l.getId());
            // 自动增加 bus 模式
            if (!l.getAllowedModes().contains("bus")) {
                Set<String> modes = new HashSet<>(l.getAllowedModes());
                modes.add("bus");
                l.setAllowedModes(modes);
            }
        }
        return bestPath;
    }

    // 匹配最近link时使用方向约束，增加方向一致性判断。
    private Link findNearestLink(Coordinate coord, double radius, double stopDirDeg, double angleThresholdDeg) {
        Link nearest = null;
        double bestScore = Double.MAX_VALUE;
        double finalAngle = 9999;
        double finalDist = 9999;
        for (Link l : network.getLinks().values()) {
            Coordinate a = toCoord(l.getFromNode());
            Coordinate b = toCoord(l.getToNode());
            double dist = new LineSegment(a, b).distance(coord);
            if (dist < finalDist) {
                finalDist = dist;
            }
            if (dist > radius) continue;

            double linkDirDeg = Math.toDegrees(Math.atan2(b.y - a.y, b.x - a.x));
            double angleDiff = Math.abs(linkDirDeg - stopDirDeg);
            if (angleDiff > 180) angleDiff = 360 - angleDiff;
            if(angleDiff < finalAngle) {
                finalAngle = angleDiff;
            }
            if (angleDiff > angleThresholdDeg) continue;

            double score = dist + angleDiff * 5; // 角度偏差权重
            if (score < bestScore) {
                bestScore = score;
                nearest = l;
            }
        }
        if (nearest != null) {
            System.out.println("✅ 站点 匹配 Link=" + nearest.getId() );
        } else {
            System.err.println("❌ 站点 未匹配Link：距离=" + finalDist + " 角度=" + finalAngle);
        }
        return nearest;
    }


        // === CRS ===
    private void setupCoordinateTransform(SimpleFeatureSource featureSource) {
        if (networkCRS == null || networkCRS.isEmpty()) return;
        try {
            SimpleFeatureCollection features = featureSource.getFeatures();
            CoordinateReferenceSystem sourceCRS = features.getSchema().getCoordinateReferenceSystem();
            if (sourceCRS == null) {
                System.out.println("[CRS] 源文件 CRS 未定义，假定为 EPSG:4326");
                sourceCRS = CRS.decode("EPSG:4326");
            }
            CoordinateReferenceSystem targetCRS = CRS.decode(networkCRS);
            this.transformToNetworkCRS = CRS.findMathTransform(sourceCRS, targetCRS, true);
            System.out.println("[CRS] 已建立坐标转换: " + CRS.toSRS(sourceCRS) + " → " + networkCRS);
        } catch (Exception e) {
            System.err.println("[CRS Error] 无法建立转换: " + e.getMessage());
        }
    }

    private Coordinate transformCoordinate(Coordinate coord) {
        if (transformToNetworkCRS != null) {
            try {
                return JTS.transform(coord, null, transformToNetworkCRS);
            } catch (Exception e) {
                System.err.println("Warning: 坐标转换失败: " + e.getMessage());
            }
        }
        return coord;
    }

    // === 工具函数 ===
    private Coordinate toCoord(Node n) { return new Coordinate(n.getCoord().getX(), n.getCoord().getY()); }

    // === 站点读取 ===
    private Map<String, List<BusStop>> readBusStopsGroupedByLine(String shpPath) throws Exception {
        Map<String, List<BusStop>> result = new HashMap<>();
        SimpleFeatureSource featureSource = FileDataStoreFinder.getDataStore(new File(shpPath)).getFeatureSource();
        MathTransform originalTransform = this.transformToNetworkCRS;
        setupCoordinateTransform(featureSource);

        SimpleFeatureIterator it = featureSource.getFeatures().features();
        while (it.hasNext()) {
            SimpleFeature f = it.next();
            String lineName = String.valueOf(f.getAttribute("line_name"));
            String stopId = String.valueOf(f.getAttribute("id"));
            double lng = (double) f.getAttribute("lng");
            double lat = (double) f.getAttribute("lat");
            Coordinate transformedCoord = transformCoordinate(new Coordinate(lng, lat));
            BusStop stop = new BusStop(stopId, transformedCoord);
            result.computeIfAbsent(lineName, k -> new ArrayList<>()).add(stop);
        }
        it.close();
        this.transformToNetworkCRS = originalTransform;
        System.out.println("[2] 已读取公交站点，总计线路: " + result.size());
        return result;
    }

    // === 内部类 ===
    static class BusStop {
        double directionDeg;
        double distAlong;
        Link nearestLink;
        String id;
        Coordinate coord;
        BusStop(String id, Coordinate c) { this.id = id; this.coord = c; }
    }
    public static class BusLinePathInfo {
        public List<Id<Link>> fullPath;
        public List<Integer> stopPositions;

        public BusLinePathInfo(List<Id<Link>> fullPath, List<Integer> stopPositions) {
            this.fullPath = fullPath;
            this.stopPositions = stopPositions;
        }
    }
    // === 外部访问 ===
//    public Map<String, List<Id<Link>>> getLineLinkPaths() { return lineLinkPaths; }
    public Map<String, Id<Link>> getStopToLinkMapping() { return stopToLinkMapping; }
    // 在 BusNetworkIntegrator 类中添加新的公共方法
    public Map<String, BusLinePathInfo> getLinePathInfos() {
        return linePathInfos;
    }

}
