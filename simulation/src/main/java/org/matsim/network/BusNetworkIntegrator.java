package org.matsim.network;

//import org.geotools.data.FileDataStoreFinder;
import org.geotools.api.data.FileDataStoreFinder;

import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
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
    private org.geotools.api.referencing.operation.MathTransform transformToNetworkCRS;

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
                org.geotools.api.feature.simple.SimpleFeature f = it.next();
                String lineId = String.valueOf(f.getAttribute("line_name"));
//                if (!lineId.equals("107路(中山八路总站--花城广场西总站)")) continue;
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
                Geometry lineGeom = multiLine.getGeometryN(0);
                sortStopsAlongLineGeometry(lineId, lineGeom, stopsByLine);

                // 匹配站点到最近 link，未匹配的站点标记 nearestLink=null（不再删除）
                for (BusStop stop : stopsByLine.get(lineId)) {
                    Link nearestLink = findNearestLink(stop.coord, distanceThresholdMeter, stop.directionDeg, angleThresholdDeg);
                    if (nearestLink != null) {
                        stop.nearestLink = nearestLink;
                        stopToLinkMapping.put(stop.id, nearestLink.getId());
                    }
                    // nearestLink==null 的站点保留，后续用虚拟 link 处理
                }

                // 在创建 fullPath 和 stopLinkPositions 之前，记录站点 ID
                List<String> stopIds = new ArrayList<>();
                for (BusStop stop : stopsByLine.get(lineId)) {
                    stopIds.add(stop.id);
                }
                List<Id<Link>> fullPath = new ArrayList<>();
                List<Integer> stopLinkPositions = new ArrayList<>(); // 记录站点在路径中的位置
                Map<String, Double> segmentArcLengths = new HashMap<>(); // 键="fromStopId->toStopId"

                List<BusStop> stops = stopsByLine.get(lineId);

                // 记录起始站点位置
                if (!stops.isEmpty()) {
                    stopLinkPositions.add(fullPath.size()); // 起始站点位置
                }

                // fullPath 末尾实际 toNode，用于段间连通性校验与过渡 link 插入
                Node lastPathEndNode = null;

                for (int i = 0; i < stops.size() - 1; i++) {
                    BusStop currentStop = stops.get(i);
                    BusStop nextStop    = stops.get(i + 1);

                    // 计算本段真实折线弧长，以站点 ID 对为键存储
                    double arcLength = computeArcLength(lineGeom, currentStop.distAlong, nextStop.distAlong);
                    segmentArcLengths.put(currentStop.id + "->" + nextStop.id, arcLength);

                    List<Id<Link>> linkPath = new ArrayList<>();

                    if (currentStop.nearestLink == null || nextStop.nearestLink == null) {
                        // 至少一个端点未匹配 → 创建虚拟 link
                        Link fromLink = currentStop.nearestLink;
                        Link toLink   = nextStop.nearestLink;

                        if (fromLink == null && toLink == null) {
                            for (int k = i - 1; k >= 0 && fromLink == null; k--) {
                                fromLink = stops.get(k).nearestLink;
                            }
                            for (int k = i + 2; k < stops.size() && toLink == null; k++) {
                                toLink = stops.get(k).nearestLink;
                            }
                            if (fromLink == null && toLink == null) {
                                stopLinkPositions.add(fullPath.size());
                                System.err.println("⚠️ [" + lineId + "] 站点 " + currentStop.id
                                        + " 和 " + nextStop.id + " 均未匹配且无法锚定，跳过本段");
                                continue;
                            }
                        } else if (fromLink == null) {
                            for (int k = i - 1; k >= 0 && fromLink == null; k--) {
                                fromLink = stops.get(k).nearestLink;
                            }
                            if (fromLink == null) fromLink = toLink;
                        } else {
                            for (int k = i + 2; k < stops.size() && toLink == null; k++) {
                                toLink = stops.get(k).nearestLink;
                            }
                            if (toLink == null) toLink = fromLink;
                        }

                        // 虚拟 link 的起点：优先接续 fullPath 末尾节点，否则用 fromLink.toNode
                        Node anchorFrom = (fromLink != null) ? fromLink.getToNode()
                                        : (toLink  != null) ? toLink.getFromNode() : null;
                        Node anchorTo   = (toLink   != null) ? toLink.getFromNode()
                                        : (fromLink != null) ? fromLink.getToNode() : null;
                        if (anchorFrom == null && lastPathEndNode == null) {
                            stopLinkPositions.add(fullPath.size());
                            System.err.println("⚠️ [" + lineId + "] seg" + i + " 无法确定锚点节点，跳过");
                            continue;
                        }
                        Node virtFromNode = (lastPathEndNode != null) ? lastPathEndNode : anchorFrom;
                        Node virtToNode   = (anchorTo != null) ? anchorTo : virtFromNode;
                        Link newLink = createVirtualLinkBetweenNodes(virtFromNode, virtToNode, arcLength, lineId, i);
                        if (newLink != null) {
                            if (!network.getLinks().containsKey(newLink.getId())) {
                                network.addLink(newLink);
                            }
                            if (currentStop.nearestLink == null) {
                                currentStop.nearestLink = newLink;
                                stopToLinkMapping.put(currentStop.id, newLink.getId());
                            }
                            linkPath.add(newLink.getId());
                        } else {
                            stopLinkPositions.add(fullPath.size());
                            continue;
                        }
                    } else {
                        // 两端均已匹配，走 Dijkstra 最短路
                        linkPath = dijkstraPath(currentStop.nearestLink, nextStop.nearestLink);
                        if (linkPath.isEmpty()) {
                            Link newLink = createDirectLinkWithArcLength(
                                    currentStop.nearestLink, nextStop.nearestLink, arcLength, lineId, i);
                            if (newLink != null) {
                                if (!network.getLinks().containsKey(newLink.getId())) {
                                    network.addLink(newLink);
                                }
                                linkPath.add(newLink.getId());
                            }
                        } else {
                            // Dijkstra 成功：修正端点 link
                            Id<Link> firstLinkId = linkPath.get(0);
                            if (firstLinkId != null && !firstLinkId.equals(currentStop.nearestLink.getId())) {
                                Link firstLink = network.getLinks().get(firstLinkId);
                                Link newLink = createDirectLink(currentStop.nearestLink, firstLink);
                                if (newLink != null) {
                                    if (!network.getLinks().containsKey(newLink.getId())) network.addLink(newLink);
                                    linkPath.add(0, newLink.getId());
                                    linkPath.add(0, currentStop.nearestLink.getId());
                                }
                            }
                            Id<Link> lastLinkId = linkPath.get(linkPath.size() - 1);
                            if (lastLinkId != null && !lastLinkId.equals(nextStop.nearestLink.getId())) {
                                Link lastLink = network.getLinks().get(lastLinkId);
                                Link newLink = createDirectLink(lastLink, nextStop.nearestLink);
                                if (newLink != null) {
                                    if (!network.getLinks().containsKey(newLink.getId())) network.addLink(newLink);
                                    linkPath.add(newLink.getId());
                                    linkPath.add(nextStop.nearestLink.getId());
                                }
                            }
                        }
                    }

                    if (linkPath.isEmpty()) {
                        stopLinkPositions.add(fullPath.size());
                        continue;
                    }

                    // ===== 统一连通性校验：在追加 linkPath 前，检查段间节点是否连通 =====
                    if (lastPathEndNode != null) {
                        Node segFirstNode = network.getLinks().get(linkPath.get(0)).getFromNode();
                        if (!lastPathEndNode.getId().equals(segFirstNode.getId())) {
                            // 插入过渡 link 弥补断点
                            double dx = lastPathEndNode.getCoord().getX() - segFirstNode.getCoord().getX();
                            double dy = lastPathEndNode.getCoord().getY() - segFirstNode.getCoord().getY();
                            double transLen = Math.max(Math.sqrt(dx * dx + dy * dy), 1.0);
                            Link transLink = createVirtualLinkBetweenNodes(
                                    lastPathEndNode, segFirstNode, transLen, lineId, i * 10000);
                            if (transLink != null) {
                                if (!network.getLinks().containsKey(transLink.getId())) {
                                    network.addLink(transLink);
                                }
                                fullPath.add(transLink.getId());
                            }
                        }
                    }

                    fullPath.addAll(linkPath);
                    // 更新 fullPath 末尾节点
                    lastPathEndNode = network.getLinks().get(linkPath.get(linkPath.size() - 1)).getToNode();
                    // 记录下一站点在完整路径中的位置
                    stopLinkPositions.add(fullPath.size());
                }

                // 保存线路对应的完整路径、站点信息和真实弧长
                linePathInfos.put(lineId, new BusLinePathInfo(fullPath, stopLinkPositions, stopIds, segmentArcLengths));
                System.out.println("[Bus] Line " + lineId + " 完成匹配，路径长度：" + fullPath.size()
                        + "，弧长段数：" + segmentArcLengths.size());
            }
            it.close();

            new NetworkWriter(network).write(outputNetworkPath);
            System.out.println("✅ 已写出融合后的 network: " + outputNetworkPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Link createDirectLink(Link fromLink, Link toLink) {
        try {
            // 获取起点和终点坐标
            Node fromNode = fromLink.getToNode();  // 从前一个链接的终点开始
            Node toNode = toLink.getFromNode();    // 连接到下一个链接的起点

            // 如果两个节点相同，不需要创建链接
            if (fromNode.getId().equals(toNode.getId())) {
                return null;
            }

            // 检查是否已存在相同链接
            for (Link existingLink : fromNode.getOutLinks().values()) {
                if (existingLink.getToNode().getId().equals(toNode.getId())) {
                    return existingLink;
                }
            }

            // 创建新链接ID
            Id<Link> newLinkId = Id.createLinkId("bus_conn_" + fromNode.getId() + "_to_" + toNode.getId());

            // 计算距离
            double distance = Math.sqrt(
                    Math.pow(fromNode.getCoord().getX() - toNode.getCoord().getX(), 2) +
                            Math.pow(fromNode.getCoord().getY() - toNode.getCoord().getY(), 2)
            );

            // 创建新链接
            Link newLink = network.getFactory().createLink(newLinkId, fromNode, toNode);
            newLink.setLength(distance);
            newLink.setFreespeed(12000.0 / 3600.0); // 12 km/h 公交速度
            // 设置通行能力为 1000.0
            newLink.setCapacity(1000.0);
            newLink.setNumberOfLanes(1.0);

            // 设置允许模式包括bus
            Set<String> modes = new HashSet<>(Arrays.asList("bus"));
            newLink.setAllowedModes(modes);

            return newLink;
        } catch (Exception e) {
            System.err.println("创建直连链接失败: " + e.getMessage());
            return null;
        }
    }


    /**
     * 创建带折线弧长的直连 link（用于 Dijkstra 失败时的 fallback）。
     * 长度使用 Shapefile 折线真实弧长，而非欧氏直线距离。
     */
    private Link createDirectLinkWithArcLength(Link fromLink, Link toLink, double arcLength,
                                               String lineId, int segIndex) {
        try {
            Node fromNode = fromLink.getToNode();
            Node toNode   = toLink.getFromNode();
            if (fromNode.getId().equals(toNode.getId())) return null;

            // 检查是否已存在相同链接
            for (Link existing : fromNode.getOutLinks().values()) {
                if (existing.getToNode().getId().equals(toNode.getId())) {
                    // 若已存在，用较大值更新长度（保守：取max，不缩短已有 link）
                    if (arcLength > existing.getLength()) {
                        existing.setLength(arcLength);
                    }
                    return existing;
                }
            }

            double dx1 = fromNode.getCoord().getX() - toNode.getCoord().getX();
            double dy1 = fromNode.getCoord().getY() - toNode.getCoord().getY();
            double length = arcLength > 1.0 ? arcLength : Math.sqrt(dx1 * dx1 + dy1 * dy1);
            Id<Link> newLinkId = Id.createLinkId("bus_arc_" + lineId + "_seg" + segIndex
                    + "_" + fromNode.getId() + "_" + toNode.getId());
            Link newLink = network.getFactory().createLink(newLinkId, fromNode, toNode);
            newLink.setLength(length);
            newLink.setFreespeed(12000.0 / 3600.0);
            newLink.setCapacity(1000.0);
            newLink.setNumberOfLanes(1.0);
            newLink.setAllowedModes(new HashSet<>(Arrays.asList("bus")));
            return newLink;
        } catch (Exception e) {
            System.err.println("创建弧长直连链接失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 创建虚拟 link，直接指定起止节点（保证连续虚拟 link 的节点连通性）。
     */
    private Link createVirtualLinkBetweenNodes(Node fromNode, Node toNode, double arcLength,
                                               String lineId, int segIndex) {
        try {
            // 节点相同时仍需自环（长度=arcLength）来给停靠站提供 link
            Id<Link> newLinkId = Id.createLinkId("bus_virt_" + lineId + "_seg" + segIndex
                    + "_" + fromNode.getId() + "_" + toNode.getId());

            if (network.getLinks().containsKey(newLinkId)) {
                return network.getLinks().get(newLinkId);
            }

            double dx2 = fromNode.getCoord().getX() - toNode.getCoord().getX();
            double dy2 = fromNode.getCoord().getY() - toNode.getCoord().getY();
            double length = arcLength > 1.0 ? arcLength : Math.max(Math.sqrt(dx2 * dx2 + dy2 * dy2), 1.0);
            Link newLink = network.getFactory().createLink(newLinkId, fromNode, toNode);
            newLink.setLength(length);
            newLink.setFreespeed(12000.0 / 3600.0);
            newLink.setCapacity(1000.0);
            newLink.setNumberOfLanes(1.0);
            newLink.setAllowedModes(new HashSet<>(Arrays.asList("bus")));
            System.out.println("🔧 [" + lineId + "] 创建虚拟link seg" + segIndex
                    + " " + fromNode.getId() + "->" + toNode.getId()
                    + " length=" + String.format("%.1f", length) + "m");
            return newLink;
        } catch (Exception e) {
            System.err.println("创建虚拟链接失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 为未匹配路网的站点创建虚拟 link，使用就近已匹配站点作为连接节点。
     * @deprecated 请使用 {@link #createVirtualLinkBetweenNodes} 以保证节点连通性
     */
    @Deprecated
    private Link createVirtualLink(Link fromLink, Link toLink, double arcLength,
                                   String lineId, int segIndex) {
        Node fromNode = (fromLink != null) ? fromLink.getToNode() : toLink.getFromNode();
        Node toNode   = (toLink   != null) ? toLink.getFromNode() : fromLink.getToNode();
        return createVirtualLinkBetweenNodes(fromNode, toNode, arcLength, lineId, segIndex);
    }

    /**
     * 计算 Geometry 折线上，从 distAlong=fromDist 到 toDist 之间的真实弧长（米）。
     * distAlong 对应 sortStopsAlongLineGeometry 中赋值的 segmentIndex + segmentFraction。
     */
    private static double computeArcLength(Geometry lineGeom, double fromDist, double toDist) {
        if (fromDist >= toDist) return 0.0;
        Coordinate[] coords = lineGeom.getCoordinates();
        if (coords.length < 2) return 0.0;

        int maxSeg   = coords.length - 2;
        int fromSeg  = Math.min((int) fromDist, maxSeg);
        double fromFrac = fromDist - (int) fromDist;
        int toSeg    = Math.min((int) toDist,   maxSeg);
        double toFrac   = toDist   - (int) toDist;

        if (fromSeg == toSeg) {
            double segLen = coords[fromSeg].distance(coords[fromSeg + 1]);
            return segLen * (toFrac - fromFrac);
        }

        double total = 0.0;
        // 起始段：从 fromFrac 到段末
        total += coords[fromSeg].distance(coords[fromSeg + 1]) * (1.0 - fromFrac);
        // 中间完整段
        for (int k = fromSeg + 1; k < toSeg; k++) {
            total += coords[k].distance(coords[k + 1]);
        }
        // 终止段：从段头到 toFrac
        total += coords[toSeg].distance(coords[toSeg + 1]) * toFrac;

        return total;
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
            org.geotools.api.referencing.crs.CoordinateReferenceSystem sourceCRS = features.getSchema().getCoordinateReferenceSystem();
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
        public List<String> stopIds; // 新增：记录站点 ID 序列
        /**
         * 每个站间段的真实 Shapefile 折线弧长（米）。
         * 键为 "fromStopId->toStopId"，与站点顺序无关，便于在 TransitScheduleWriter 中按站对查找。
         */
        public Map<String, Double> segmentArcLengths;

        public BusLinePathInfo(List<Id<Link>> fullPath, List<Integer> stopPositions,
                               List<String> stopIds, Map<String, Double> segmentArcLengths) {
            this.fullPath = fullPath;
            this.stopPositions = stopPositions;
            this.stopIds = stopIds;
            this.segmentArcLengths = segmentArcLengths;
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
