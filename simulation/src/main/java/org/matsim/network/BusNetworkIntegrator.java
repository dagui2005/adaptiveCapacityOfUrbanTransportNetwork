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
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.linearref.LinearLocation;
import org.locationtech.jts.linearref.LocationIndexedLine;
import org.locationtech.jts.operation.linemerge.LineMerger;
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

    /** 公交不可走的 fclass 道路类型 */
    private static final Set<String> BUS_FORBIDDEN_CLASSES = new HashSet<>(Arrays.asList(
            "motorway", "motorway_link",       // 高速公路及匝道
            "footway", "path", "pedestrian", "steps", "bridleway", "cycleway",  // 步行/非机动车
            "track", "track_grade1", "track_grade2", "track_grade4",           // 野外/简易路
            "living_street", "service"          // 居民区/服务道路
    ));

    /** 公交 link 的默认 freespeed (m/s)，与 TransitScheduleWriter offset 速度一致 */
    private static final double BUS_FREESPEED = 18000.0 / 3600.0; // 18 km/h（Layer 5：与 TransitScheduleWriter 的 schedule offset 18km/h 对齐，让虚拟 link 的公交速度与计划速度一致）
    /** 公交 link 的默认容量 */
    private static final double BUS_CAPACITY = 1000.0;

    /** 欧氏距离（MATSim 的 Coord 类不带 distance 方法） */
    private static double euclidDist(Coord a, Coord b) {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }
    /** 公交 link 的默认车道数 */
    private static final double BUS_LANES = 1.0;

    private final Network network;
    private final double angleThresholdDeg;
    private final double distanceThresholdMeter;

    // 第1层多级回退参数
    private final double angleThresholdRelaxedDeg;
    private final double radiusRelaxed;
    private final boolean enableFclassFallback;
    // 第2层子段二分参数
    private final boolean enableSubSplitFallback;

    /** fclass 多级回退时使用的"宽松禁行集合"（仅排除纯步行/纯非机动车） */
    private static final Set<String> BUS_FORBIDDEN_CLASSES_RELAXED = new HashSet<>(Arrays.asList(
            "footway", "path", "pedestrian", "steps", "bridleway", "cycleway"
            // 不再排除 motorway / track / living_street / service
    ));

    /** 子段二分递归最大深度（深度3 = 最多 8 子段） */
    private static final int MAX_RECURSION_DEPTH = 3;
    /** 子段二分最小长度（米）：子段短于此长度时停止递归 */
    private static final double MIN_SUBSEGMENT_LENGTH = 100.0;

//    private final Map<String, List<Id<Link>>> lineLinkPaths = new HashMap<>();
    private final Map<String, Id<Link>> stopToLinkMapping = new HashMap<>();
    private final Map<String, BusLinePathInfo> linePathInfos = new HashMap<>();

    // 多级回退诊断统计
    private int matchLevel1Count = 0;
    private int matchLevel2Count = 0;
    private int matchLevel3Count = 0;
    private int matchFailedCount = 0;
    // 子段切分诊断统计
    private int subSplitSegments = 0;
    private int subSplitRealLinks = 0;
    private int subSplitVirtualLinks = 0;

    private String networkCRS;
    private org.geotools.api.referencing.operation.MathTransform transformToNetworkCRS;

    /** 路网 link 空间索引（buildSegmentRecursive 递归访问需要） */
    private STRtree linkIndex;

    public BusNetworkIntegrator(Network network, double angleThresholdDeg, double distanceThresholdMeter, String networkCRS) {
        this(network, angleThresholdDeg, distanceThresholdMeter, networkCRS,
                75.0, 200.0, true, true);
    }

    public BusNetworkIntegrator(Network network, double angleThresholdDeg, double distanceThresholdMeter, String networkCRS,
                                double angleThresholdRelaxedDeg, double radiusRelaxed,
                                boolean enableFclassFallback, boolean enableSubSplitFallback) {
        this.network = network;
        this.angleThresholdDeg = angleThresholdDeg;
        this.distanceThresholdMeter = distanceThresholdMeter;
        this.networkCRS = networkCRS;
        this.angleThresholdRelaxedDeg = angleThresholdRelaxedDeg;
        this.radiusRelaxed = radiusRelaxed;
        this.enableFclassFallback = enableFclassFallback;
        this.enableSubSplitFallback = enableSubSplitFallback;
    }

    /**
     * 执行公交线路融合流程，将公交线路与网络进行匹配并生成线路路径。
     *
     * @param busStopShp       公交站点 Shapefile 文件路径，用于读取各线路的站点信息。
     * @param busLineShp       公交线路 Shapefile 文件路径，包含线路几何信息。
     * @param outputNetworkPath 输出网络文件路径。
     */
    public void integrateBusLines(String busStopShp, String busLineShp, String outputNetworkPath) {
        try {
            System.out.println("=== [BusNetworkIntegrator] 公交线融合流程启动 ===");
            System.out.println("[1] 加载公交线 shapefile: " + busLineShp);

            // === 修复 Bug 1/3: 统一 CRS 变换 — 先从线路 Shapefile 建立变换，站点读取复用同一变换 ===
            SimpleFeatureSource lineFeatureSource = FileDataStoreFinder.getDataStore(new File(busLineShp)).getFeatureSource();
            setupCoordinateTransform(lineFeatureSource);

            // 按线路分组读取公交站点数据（复用已建立的 CRS 变换，不再内部重置）
            Map<String, List<BusStop>> stopsByLine = readBusStopsGroupedByLine(busStopShp);

            // === 构建空间索引 STRtree，加速 findNearestLink ===
            System.out.println("[2] 构建 link 空间索引 (STRtree)...");
            STRtree linkIndex = new STRtree();
            this.linkIndex = linkIndex;
            for (Link l : network.getLinks().values()) {
                Coordinate a = toCoord(l.getFromNode());
                Coordinate b = toCoord(l.getToNode());
                Envelope env = new Envelope(a, b);
                env.expandBy(distanceThresholdMeter);
                linkIndex.insert(env, l);
            }
            linkIndex.build();
            System.out.println("    空间索引构建完成，link 总数: " + network.getLinks().size());

            // === 全局只建一次 SpeedyGraph + Dijkstra ===
            System.out.println("[3] 构建 SpeedyGraph...");
            TravelTime tt = (link, t, p, v) -> link.getLength() / Math.max(link.getFreespeed(), 0.1);
            TravelDisutility td = new OnlyTimeDependentTravelDisutility(tt);
            SpeedyGraph speedyGraph = new SpeedyGraphBuilder().build(network);
            LeastCostPathCalculator router = new SpeedyDijkstra(speedyGraph, tt, td);

            // === 统计计数器 ===
            int totalLines = 0, totalStops = 0, matchedStops = 0;
            int dijkstraPaths = 0, virtualLinks = 0, directLinks = 0, failedSegments = 0, transitLinks = 0;

            SimpleFeatureIterator it = lineFeatureSource.getFeatures().features();

            while (it.hasNext()) {
                org.geotools.api.feature.simple.SimpleFeature f = it.next();
                String lineId = String.valueOf(f.getAttribute("line_name"));
                Geometry geom = (Geometry) f.getDefaultGeometry();

                // === 修复 Bug 26: 检查几何为 null ===
                if (geom == null || geom.isEmpty()) {
                    System.err.println("⚠️ [" + lineId + "] 几何为空，跳过");
                    continue;
                }

                // CRS 变换
                if (transformToNetworkCRS != null) {
                    try {
                        geom = JTS.transform(geom, transformToNetworkCRS);
                    } catch (Exception ex) {
                        System.err.println("[Warning] CRS transform failed for " + lineId + ": " + ex.getMessage());
                    }
                }

                // === 修复 问题18/19: 处理 LineString 和 MultiLineString，合并所有段 ===
                Geometry lineGeom;
                if (geom instanceof MultiLineString) {
                    MultiLineString multiLine = (MultiLineString) geom;
                    LineMerger merger = new LineMerger();
                    for (int g = 0; g < multiLine.getNumGeometries(); g++) {
                        merger.add(multiLine.getGeometryN(g));
                    }
                    @SuppressWarnings("unchecked")
                    java.util.Collection<LineString> mergedLines = merger.getMergedLineStrings();
                    if (mergedLines.size() == 1) {
                        lineGeom = mergedLines.iterator().next();
                    } else if (mergedLines.size() > 1) {
                        // 合并后仍有多段（不连通），取最长段作为主几何
                        lineGeom = mergedLines.iterator().next();
                        for (LineString ls : mergedLines) {
                            if (ls.getLength() > lineGeom.getLength()) {
                                lineGeom = ls;
                            }
                        }
                        System.err.println("⚠️ [" + lineId + "] 线路几何不连通(" + mergedLines.size()
                                + "段)，取最长段");
                    } else {
                        // 合并失败，回退到第一段
                        lineGeom = multiLine.getGeometryN(0);
                    }
                } else if (geom instanceof LineString) {
                    lineGeom = geom;
                } else {
                    System.err.println("⚠️ [" + lineId + "] 几何类型不支持: " + geom.getClass().getSimpleName());
                    continue;
                }

                if (lineGeom.getCoordinates().length < 2) {
                    System.err.println("⚠️ [" + lineId + "] 线路几何坐标点不足，跳过");
                    continue;
                }

                // === 修复 问题9: 站点列表为空时跳过 ===
                List<BusStop> stops = stopsByLine.get(lineId);
                if (stops == null || stops.isEmpty()) {
                    System.err.println("⚠️ [" + lineId + "] 无站点数据，跳过");
                    continue;
                }

                totalLines++;
                totalStops += stops.size();

                // 按顺序匹配站点到线路几何, 并保存方向角
                sortStopsAlongLineGeometry(lineId, lineGeom, stopsByLine);

                // 匹配站点到最近 link（使用空间索引 + fclass 过滤）
                for (BusStop stop : stops) {
                    Link nearestLink = findNearestLink(stop.coord, distanceThresholdMeter,
                            stop.directionDeg, angleThresholdDeg, linkIndex);
                    if (nearestLink != null) {
                        stop.nearestLink = nearestLink;
                        // 保留原有 key 格式 (stop.id)，TransitScheduleWriter 依赖此格式
                        // (stop.id 格式为 POIID + "X" + 方向后缀，本身已含线路方向信息)
                        stopToLinkMapping.put(stop.id, nearestLink.getId());
                        matchedStops++;
                    }
                }

                // 记录站点 ID
                List<String> stopIds = new ArrayList<>();
                for (BusStop stop : stops) stopIds.add(stop.id);

                List<Id<Link>> fullPath = new ArrayList<>();
                List<Integer> stopLinkPositions = new ArrayList<>();
                Map<String, Double> segmentArcLengths = new HashMap<>();

                // 记录起始站点位置
                stopLinkPositions.add(fullPath.size());

                Node lastPathEndNode = null;

                for (int i = 0; i < stops.size() - 1; i++) {
                    BusStop currentStop = stops.get(i);
                    BusStop nextStop    = stops.get(i + 1);

                    // 计算本段真实折线弧长
                    double arcLength = computeArcLength(lineGeom, currentStop.distAlong, nextStop.distAlong);
                    // 修复 Bug 5: 零弧长保护
                    if (arcLength < 1.0) arcLength = 1.0;
                    segmentArcLengths.put(currentStop.id + "->" + nextStop.id, arcLength);

                    List<Id<Link>> linkPath = new ArrayList<>();

                    if (currentStop.nearestLink == null || nextStop.nearestLink == null) {
                        // 至少一个端点未匹配 → 创建虚拟 link
                        virtualLinks++;
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
                                failedSegments++;
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

                        Node anchorFrom = (fromLink != null) ? fromLink.getToNode()
                                        : (toLink  != null) ? toLink.getFromNode() : null;
                        Node anchorTo   = (toLink   != null) ? toLink.getFromNode()
                                        : (fromLink != null) ? fromLink.getToNode() : null;
                        if (anchorFrom == null && lastPathEndNode == null) {
                            stopLinkPositions.add(fullPath.size());
                            failedSegments++;
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
                            failedSegments++;
                            continue;
                        }
                    } else {
                        // 两端均已匹配，走 Dijkstra 最短路（传入全局 router）
                        linkPath = dijkstraPath(currentStop.nearestLink, nextStop.nearestLink, router);
                        if (linkPath.isEmpty()) {
                            // Dijkstra 失败 → 优先尝试第2层子段二分递归
                            boolean usedSubSplit = false;
                            if (enableSubSplitFallback) {
                                List<Id<Link>> subPath = buildSegmentRecursive(
                                        lineGeom, currentStop.coord, nextStop.coord,
                                        currentStop.distAlong, nextStop.distAlong, 0,
                                        currentStop.directionDeg, lineId, i, router);
                                if (!subPath.isEmpty()) {
                                    // 兜底：确保所有 link 都在 network 里
                                    // (createSubSegmentVirtualLink 已 addLink，但 createVirtualLinkBetweenNodes
                                    //  可能返回已有的 link，此处不再重复 add)
                                    for (Id<Link> linkId : subPath) {
                                        if (!network.getLinks().containsKey(linkId)) {
                                            System.err.println("⚠️ [subSplit] link " + linkId + " not in network, skipping");
                                        }
                                    }
                                    linkPath = new ArrayList<>(subPath);
                                    usedSubSplit = true;
                                }
                            }

                            if (!usedSubSplit) {
                                // Dijkstra + 子段都失败 → 用弧长直连 link 回退
                                directLinks++;
                                Link newLink = createDirectLinkWithArcLength(
                                        currentStop.nearestLink, nextStop.nearestLink, arcLength, lineId, i);
                                if (newLink != null) {
                                    if (!network.getLinks().containsKey(newLink.getId())) {
                                        network.addLink(newLink);
                                    }
                                    linkPath.add(newLink.getId());
                                }
                            }
                        } else {
                            dijkstraPaths++;
                            // [ABLATION] prepend-anchor block removed
                            Id<Link> lastLinkId = linkPath.get(linkPath.size() - 1);
                            if (lastLinkId != null && !lastLinkId.equals(nextStop.nearestLink.getId())) {
                                Link lastLink = network.getLinks().get(lastLinkId);
                                Link newLink = createDirectLink(lastLink, nextStop.nearestLink);
                                if (newLink != null) {
                                    if (!network.getLinks().containsKey(newLink.getId())) network.addLink(newLink);
                                    linkPath.add(newLink.getId());
                                    linkPath.add(nextStop.nearestLink.getId());
                                } else {
                                    linkPath.add(nextStop.nearestLink.getId());
                                }
                            } else if (lastLinkId != null && lastLinkId.equals(nextStop.nearestLink.getId())) {
                                // 路径已以 nextStop.nearestLink 结尾，无需修正
                                // no-op
                            }
                        }
                    }

                    if (linkPath.isEmpty()) {
                        stopLinkPositions.add(fullPath.size());
                        failedSegments++;
                        continue;
                    }

                    // ===== 连通性校验：段间节点不连续时插入过渡 link =====
                    if (lastPathEndNode != null) {
                        Node segFirstNode = network.getLinks().get(linkPath.get(0)).getFromNode();
                        if (!lastPathEndNode.getId().equals(segFirstNode.getId())) {
                            transitLinks++;
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

                    // [ABLATION] tail-bridge block removed
                    fullPath.addAll(linkPath);
                    lastPathEndNode = network.getLinks().get(linkPath.get(linkPath.size() - 1)).getToNode();
                    stopLinkPositions.add(fullPath.size());
                }

                linePathInfos.put(lineId, new BusLinePathInfo(fullPath, stopLinkPositions, stopIds, segmentArcLengths));
                System.out.println("[Bus] Line " + lineId + " 完成，路径link数：" + fullPath.size()
                        + "，站点数：" + stops.size() + "，弧长段数：" + segmentArcLengths.size());
            }
            it.close();

            // === 全局匹配质量统计 ===
            System.out.println("\n=== [BusNetworkIntegrator] 匹配统计 ===");
            System.out.printf("  总线路数: %d\n", totalLines);
            System.out.printf("  总站点数: %d，匹配成功: %d (%.1f%%)\n",
                    totalStops, matchedStops, totalStops > 0 ? 100.0 * matchedStops / totalStops : 0);
            System.out.printf("  Dijkstra路径: %d，虚拟link: %d，直连link: %d，过渡link: %d\n",
                    dijkstraPaths, virtualLinks, directLinks, transitLinks);
            System.out.printf("  失败段数: %d\n", failedSegments);

            // === 第1层多级回退诊断 ===
            int totalSiteQueries = matchLevel1Count + matchLevel2Count + matchLevel3Count + matchFailedCount;
            if (totalSiteQueries > 0) {
                System.out.println("\n=== [第1层] 多级回退诊断 ===");
                System.out.printf("  Level1 (严格fclass+radius+角度): %d (%.1f%%)\n",
                        matchLevel1Count, 100.0 * matchLevel1Count / totalSiteQueries);
                System.out.printf("  Level2 (严格fclass+放大radius+角度放宽): %d (%.1f%%)\n",
                        matchLevel2Count, 100.0 * matchLevel2Count / totalSiteQueries);
                System.out.printf("  Level3 (放宽fclass+最大radius+角度放宽): %d (%.1f%%)\n",
                        matchLevel3Count, 100.0 * matchLevel3Count / totalSiteQueries);
                System.out.printf("  Failed: %d (%.1f%%)\n",
                        matchFailedCount, 100.0 * matchFailedCount / totalSiteQueries);
            }

            // === 第2层子段切分诊断 ===
            if (subSplitSegments > 0) {
                System.out.println("\n=== [第2层] 子段二分诊断 ===");
                System.out.printf("  切分后的子段数: %d\n", subSplitSegments);
                System.out.printf("  其中走真实 link: %d\n", subSplitRealLinks);
                System.out.printf("  其中走虚拟 link: %d\n", subSplitVirtualLinks);
            }

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
            newLink.setFreespeed(BUS_FREESPEED);
            newLink.setCapacity(BUS_CAPACITY);
            newLink.setNumberOfLanes(BUS_LANES);

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
            newLink.setFreespeed(BUS_FREESPEED);
            newLink.setCapacity(BUS_CAPACITY);
            newLink.setNumberOfLanes(BUS_LANES);
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
            // 修复 Bug 7: 自环检查 — Dijkstra 不走自环 link，且易导致路径异常
            if (fromNode.getId().equals(toNode.getId())) {
                // 自环时返回 null，调用方应走其他路径或跳过
                return null;
            }

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
            newLink.setFreespeed(BUS_FREESPEED);
            newLink.setCapacity(BUS_CAPACITY);
            newLink.setNumberOfLanes(BUS_LANES);
            newLink.setAllowedModes(new HashSet<>(Arrays.asList("bus")));
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
     * 沿公交线路折线插值出 distAlong 处的坐标。
     */
    private static Coordinate interpolateAlongLine(Coordinate[] coords, double distAlong) {
        if (coords == null || coords.length == 0) return null;
        if (distAlong <= 0) return coords[0];
        if (distAlong >= coords.length - 1) return coords[coords.length - 1];
        int idx = (int) Math.floor(distAlong);
        double frac = distAlong - idx;
        if (idx + 1 >= coords.length) return coords[coords.length - 1];
        double x = coords[idx].x + frac * (coords[idx + 1].x - coords[idx].x);
        double y = coords[idx].y + frac * (coords[idx + 1].y - coords[idx].y);
        return new Coordinate(x, y);
    }

    /**
     * 沿公交线路折线计算 distAlong 处的切线方向（度）。
     */
    private static double computeTangentDirection(Coordinate[] coords, double distAlong) {
        if (coords == null || coords.length < 2) return 0;
        int idx = (int) Math.floor(distAlong);
        double frac = distAlong - idx;
        if (idx + 1 >= coords.length) idx = coords.length - 2;
        double dx = coords[idx + 1].x - coords[idx].x;
        double dy = coords[idx + 1].y - coords[idx].y;
        // 切线方向 + 段内偏移
        double tangentDeg = Math.toDegrees(Math.atan2(dy, dx));
        if (frac > 0 && idx + 2 < coords.length) {
            double dx2 = coords[idx + 2].x - coords[idx + 1].x;
            double dy2 = coords[idx + 2].y - coords[idx + 1].y;
            double tangent2 = Math.toDegrees(Math.atan2(dy2, dx2));
            // 加权平均
            tangentDeg = tangentDeg * (1 - frac) + tangent2 * frac;
        }
        return tangentDeg;
    }

    /**
     * 二分回退式子段匹配：把失败段沿线几何切分成多个子段，
     * 每个子段独立尝试 findNearestLink + Dijkstra；仍未匹配的子段用虚拟 link 兜底。
     */
    private List<Id<Link>> buildSegmentRecursive(
            Geometry lineGeom, Coordinate fromCoord, Coordinate toCoord,
            double fromDist, double toDist, int depth,
            double directionDeg, String lineId, int segIndex,
            LeastCostPathCalculator router) {
        List<Id<Link>> result = new ArrayList<>();
        double subSegLength = toDist - fromDist;

        // 终止条件：递归深度耗尽或子段太短
        if (depth >= MAX_RECURSION_DEPTH || subSegLength < MIN_SUBSEGMENT_LENGTH) {
            // 兜底：从子段端点找最近 link（即便禁行也接受），创建虚拟 link
            Link virtLink = createSubSegmentVirtualLink(fromCoord, toCoord, subSegLength, lineId, segIndex, depth);
            if (virtLink != null) result.add(virtLink.getId());
            subSplitVirtualLinks++;
            subSplitSegments++;
            return result;
        }

        // 1. 尝试子段两端各 findNearestLink + Dijkstra
        Link fromLink = findNearestLink(fromCoord, distanceThresholdMeter, directionDeg,
                angleThresholdDeg, linkIndex);
        Link toLink   = findNearestLink(toCoord,   distanceThresholdMeter, directionDeg,
                angleThresholdDeg, linkIndex);

        if (fromLink != null && toLink != null) {
            List<Id<Link>> path = dijkstraPath(fromLink, toLink, router);
            if (!path.isEmpty()) {
                // === Layer 5 修复：子段内 Real→Virt 桥接 ===
                // Dijkstra 路径末尾 link 的 toNode 不一定等于 toLink.fromNode
                // （因为 toLink 是 toCoord 的就近 anchor，Dijkstra 可能沿不同路线到达附近节点）
                // 此时若不桥接，公交车从真实 link 直接转虚拟 link 时会触发
                // "Cannot move vehicle from <real_link> to bus_virt_*_ND..."。
                // 修复：检查末尾与 toLink 起点节点是否一致，不一致则插入桥接虚拟 link。
                Link lastLinkInPath = network.getLinks().get(path.get(path.size() - 1));
                if (lastLinkInPath != null
                        && !lastLinkInPath.getToNode().getId().equals(toLink.getFromNode().getId())) {
                    double bridgeLen = Math.max(euclidDist(
                            lastLinkInPath.getToNode().getCoord(),
                            toLink.getFromNode().getCoord()), 1.0);
                    // bridgeSegIndex 用 depth * 1000 + 998，与合并处 bridge (depth*1000+999) 区分
                    int bridgeSegIndex = segIndex * 100000 + depth * 1000 + 998;
                    Link bridge = createVirtualLinkBetweenNodes(
                            lastLinkInPath.getToNode(), toLink.getFromNode(),
                            bridgeLen, lineId, bridgeSegIndex);
                    if (bridge != null && !network.getLinks().containsKey(bridge.getId())) {
                        network.addLink(bridge);
                        path.add(bridge.getId());
                    }
                }
                result.addAll(path);
                subSplitRealLinks++;
                subSplitSegments++;
                return result;
            }
        }

        // 2. 子段整段失败 → 二分递归
        double midDist = (fromDist + toDist) / 2.0;
        Coordinate[] coords = lineGeom.getCoordinates();
        Coordinate midCoord = interpolateAlongLine(coords, midDist);
        if (midCoord == null) {
            // 兜底：退化
            Link virtLink = createSubSegmentVirtualLink(fromCoord, toCoord, subSegLength, lineId, segIndex, depth);
            if (virtLink != null) result.add(virtLink.getId());
            subSplitVirtualLinks++;
            subSplitSegments++;
            return result;
        }
        double midDirDeg = computeTangentDirection(coords, midDist);

        // 递归左右两半
        List<Id<Link>> left = buildSegmentRecursive(
                lineGeom, fromCoord, midCoord, fromDist, midDist, depth + 1,
                directionDeg, lineId, segIndex, router);
        List<Id<Link>> right = buildSegmentRecursive(
                lineGeom, midCoord, toCoord, midDist, toDist, depth + 1,
                midDirDeg, lineId, segIndex, router);

        // === Layer 5 修复：虚拟 link 桥接 ===
        // 递归的左/右半段各自独立创建虚拟 link 时，fromNode/toNode 锚定到不同 real-link anchor，
        // 导致 left.last.toNode ≠ right.first.fromNode → DefaultTurnAcceptanceLogic 报
        // "Cannot move vehicle from bus_virt_X_ND006554 to bus_virt_Y_ND006577"。
        // 修复：仅当 left/right 末端是虚拟 link 且两端节点不一致时，插入一个桥接虚拟 link 串接。
        if (!left.isEmpty() && !right.isEmpty()) {
            Link leftLast = network.getLinks().get(left.get(left.size() - 1));
            Link rightFirst = network.getLinks().get(right.get(0));
            if (leftLast != null && rightFirst != null) {
                String leftId = leftLast.getId().toString();
                String rightId = rightFirst.getId().toString();
                boolean leftIsVirt = leftId.startsWith("bus_virt");
                boolean rightIsVirt = rightId.startsWith("bus_virt");
                Node bridgeFrom = leftLast.getToNode();
                Node bridgeTo = rightFirst.getFromNode();
                // === Layer 5b 修复：放宽桥接条件（原 leftIsVirt && rightIsVirt 太严格） ===
                // 任何 left.last.toNode ≠ right.first.fromNode 都插入桥接，包括 Real→Real、Real→Virt、Virt→Real、Virt→Virt
                if (!bridgeFrom.getId().equals(bridgeTo.getId())) {
                    double bridgeLen = Math.max(euclidDist(bridgeFrom.getCoord(), bridgeTo.getCoord()), 1.0);
                    // 使用足够分散的 segIndex：主段号 * 100000 + depth * 1000 + 999，避免与其它虚拟 link 冲突
                    int bridgeSegIndex = segIndex * 100000 + depth * 1000 + 999;
                    Link bridge = createVirtualLinkBetweenNodes(
                            bridgeFrom, bridgeTo, bridgeLen, lineId, bridgeSegIndex);
                    if (bridge != null && !network.getLinks().containsKey(bridge.getId())) {
                        network.addLink(bridge);
                        // 把桥接 link 插到 right 路径最前面
                        right.add(0, bridge.getId());
                    }
                }
            }
        }

        result.addAll(left);
        result.addAll(right);
        subSplitSegments++;
        return result;
    }

    /**
     * 子段虚拟 link 兜底：把 fromCoord/toCoord 各自投影到最近 link（放宽过滤），
     * 再用 createVirtualLinkBetweenNodes 创建桥接，并把 link 加入 network。
     */
    private Link createSubSegmentVirtualLink(Coordinate fromCoord, Coordinate toCoord,
                                             double arcLength, String lineId, int segIndex, int depth) {
        Link fromAnchor = findNearestLink(fromCoord, distanceThresholdMeter, 0, 180.0, linkIndex);
        Link toAnchor   = findNearestLink(toCoord,   distanceThresholdMeter, 0, 180.0, linkIndex);

        // 用放宽版兜底（最宽松搜索）
        if (fromAnchor == null) {
            fromAnchor = findNearestLinkStrict(fromCoord, radiusRelaxed * 2.0, 0, 180.0, linkIndex,
                    new HashSet<>()); // 无 fclass 排除
        }
        if (toAnchor == null) {
            toAnchor = findNearestLinkStrict(toCoord, radiusRelaxed * 2.0, 0, 180.0, linkIndex,
                    new HashSet<>());
        }
        if (fromAnchor == null || toAnchor == null) return null;

        Node fromNode = fromAnchor.getToNode();
        Node toNode   = toAnchor.getFromNode();
        Link newLink = createVirtualLinkBetweenNodes(fromNode, toNode, arcLength,
                lineId, segIndex * 1000 + depth);
        // 关键：把子段虚拟 link 加入 network，否则后续 segment 找不到这个 link
        if (newLink != null && !network.getLinks().containsKey(newLink.getId())) {
            network.addLink(newLink);
        }
        return newLink;
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
        List<BusStop> stops = stopsByLine.get(lineId);
        if (stops == null || stops.isEmpty()) return;

        // 修复 问题15: LocationIndexedLine 只构造一次
        LocationIndexedLine lil = new LocationIndexedLine(geom);

        // 投影站点到线路几何，计算 distAlong
        for (BusStop stop : stops) {
            LinearLocation projectedLoc = lil.project(stop.coord);
            stop.distAlong = projectedLoc.getSegmentIndex() + projectedLoc.getSegmentFraction();
        }

        // 按 distAlong 排序站点
        stops.sort(Comparator.comparingDouble(s -> s.distAlong));

        // 计算每个公交站的方向角
        Coordinate[] coords = geom.getCoordinates();
        for (BusStop stop : stops) {
            try {
                LinearLocation loc = lil.project(stop.coord);
                if (coords.length >= 2) {
                    int segmentIndex = loc.getSegmentIndex();
                    if (segmentIndex < coords.length - 1) {
                        Coordinate c1 = coords[segmentIndex];
                        Coordinate c2 = coords[segmentIndex + 1];
                        stop.directionDeg = Math.toDegrees(Math.atan2(c2.y - c1.y, c2.x - c1.x));
                    } else {
                        Coordinate c1 = coords[coords.length - 2];
                        Coordinate c2 = coords[coords.length - 1];
                        stop.directionDeg = Math.toDegrees(Math.atan2(c2.y - c1.y, c2.x - c1.x));
                    }
                }
            } catch (IllegalStateException e) {
                if (coords.length >= 2) {
                    Coordinate first = coords[0];
                    Coordinate last = coords[coords.length - 1];
                    stop.directionDeg = Math.toDegrees(Math.atan2(last.y - first.y, last.x - first.x));
                } else {
                    stop.directionDeg = 0.0;
                }
            }
        }
    }



    /**
     * Dijkstra 路径计算（使用全局 router，fclass 过滤，正确起止节点）。
     * 路由从 startLink.getToNode() 到 endLink.getFromNode()，
     * 不包含 startLink 和 endLink 本身。
     */
    private List<Id<Link>> dijkstraPath(Link startLink, Link endLink, LeastCostPathCalculator router) {
        List<Id<Link>> bestPath = new ArrayList<>();

        // 修复 问题20: 起止节点修正
        // 公交从 startLink 驶向 endLink，路径应为 startLink.toNode → endLink.fromNode
        LeastCostPathCalculator.Path path = router.calcLeastCostPath(
                startLink.getToNode(), endLink.getFromNode(), 0, null, null);

        if (path == null || path.links == null || path.links.isEmpty()) {
            return Collections.emptyList();
        }

        for (Link l : path.links) {
            // fclass 过滤：跳过公交禁行道路（理论上 Dijkstra 的 TravelDisutility 已避免，
            // 但此处仍做检查以防止边界情况）
            String fclass = (String) l.getAttributes().getAttribute("fclass");
            if (fclass != null && BUS_FORBIDDEN_CLASSES.contains(fclass)) {
                // 路径穿越禁行道路 — 放弃，调用方应改走直连 link 回退
                return new ArrayList<>();
            }
            bestPath.add(l.getId());
            // 给路径上的 link 增加 bus 模式
            if (!l.getAllowedModes().contains("bus")) {
                Set<String> modes = new HashSet<>(l.getAllowedModes());
                modes.add("bus");
                l.setAllowedModes(modes);
            }
        }
        return bestPath;
    }

    /**
     * 匹配最近 link（使用 STRtree 空间索引 + fclass 过滤 + 方向约束）。
     * 多级回退：Level1 严格 fclass + radius + 角度；Level2 严格 fclass + 放大 radius + 角度放宽；
     * Level3 放宽 fclass + 最大 radius + 角度放宽。
     */
    private Link findNearestLink(Coordinate coord, double radius, double stopDirDeg,
                                 double angleThresholdDeg, STRtree linkIndex) {
        // 第1级：严格 fclass + radius + 角度
        Link nearest = findNearestLinkStrict(coord, radius, stopDirDeg, angleThresholdDeg, linkIndex,
                BUS_FORBIDDEN_CLASSES);
        if (nearest != null) {
            matchLevel1Count++;
            return nearest;
        }

        if (!enableFclassFallback) {
            matchFailedCount++;
            return null;
        }

        // 第2级：严格 fclass + 放大 radius + 角度放宽
        nearest = findNearestLinkStrict(coord, radiusRelaxed, stopDirDeg,
                angleThresholdRelaxedDeg, linkIndex, BUS_FORBIDDEN_CLASSES);
        if (nearest != null) {
            matchLevel2Count++;
            return nearest;
        }

        // 第3级：放宽 fclass（仅排除纯步行/非机动车）+ 最大 radius + 角度放宽
        nearest = findNearestLinkStrict(coord, radiusRelaxed * 1.5, stopDirDeg,
                angleThresholdRelaxedDeg, linkIndex, BUS_FORBIDDEN_CLASSES_RELAXED);
        if (nearest != null) {
            matchLevel3Count++;
            return nearest;
        }

        matchFailedCount++;
        return null;
    }

    /**
     * 单次最近 link 查询（带 fclass 集合过滤）。
     */
    private Link findNearestLinkStrict(Coordinate coord, double radius, double stopDirDeg,
                                       double angleThresholdDeg, STRtree linkIndex,
                                       Set<String> forbiddenClasses) {
        Link nearest = null;
        double bestScore = Double.MAX_VALUE;

        // 使用空间索引查询半径内的候选 link
        Envelope query = new Envelope(coord);
        query.expandBy(radius);
        @SuppressWarnings("unchecked")
        List<Link> candidates = linkIndex.query(query);

        for (Link l : candidates) {
            // fclass 过滤
            String fclass = (String) l.getAttributes().getAttribute("fclass");
            if (fclass != null && forbiddenClasses.contains(fclass)) continue;

            Coordinate a = toCoord(l.getFromNode());
            Coordinate b = toCoord(l.getToNode());
            double dist = new LineSegment(a, b).distance(coord);
            if (dist > radius) continue;

            double linkDirDeg = Math.toDegrees(Math.atan2(b.y - a.y, b.x - a.x));
            double angleDiff = Math.abs(linkDirDeg - stopDirDeg);
            if (angleDiff > 180) angleDiff = 360 - angleDiff;
            if (angleDiff > angleThresholdDeg) continue;

            double score = dist + angleDiff * 5;
            if (score < bestScore) {
                bestScore = score;
                nearest = l;
            }
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

        // 修复 Bug 1/3: 不再重新 setupCoordinateTransform，复用主流程已建立的 CRS 变换
        // 如果站点 Shapefile CRS 与线路 Shapefile 不同，需要在此处单独处理
        // 但通常两个 Shapefile 使用相同 CRS

        SimpleFeatureIterator it = featureSource.getFeatures().features();
        while (it.hasNext()) {
            SimpleFeature f = it.next();
            String lineName = String.valueOf(f.getAttribute("line_name"));
            String stopId = String.valueOf(f.getAttribute("id"));
            // 修复 Bug 2: 安全类型转换，避免 String 类型强转 double 抛异常
            double lng = ((Number) f.getAttribute("lng")).doubleValue();
            double lat = ((Number) f.getAttribute("lat")).doubleValue();
            Coordinate transformedCoord = transformCoordinate(new Coordinate(lng, lat));
            BusStop stop = new BusStop(stopId, transformedCoord);
            result.computeIfAbsent(lineName, k -> new ArrayList<>()).add(stop);
        }
        it.close();
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
