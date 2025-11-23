package org.matsim.network;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.referencing.CRS;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.*;
/**
 * MetroNetworkIntegrator - 集成版（将地铁写入传入的 MATSim Network 并返回每条链的 linkId 列表）
 *
 * 主要改动：
 *  - buildNetworkFromShp 返回 Map<String,List<Id<Link>>>（每条 chain 的 forward/reverse link id 列表）
 *  - 将节点直接添加到传入的 network 中（使用 NDPT+POIID 或 NDPT+自增）
 *  - 将 link 直接添加到 network 中（id 格式 LKPT + 11 位自增）
 *
 * 返回的 map key 约定： "FLD_<fld>_chain<k>_fwd" 和 "FLD_<fld>_chain<k>_rev"
 */
public class MetroNetworkIntegrator {

    private final int coordDecimal;
    private long ndptCounter = 1L;
    private long lkptCounter = 1L;

    // rounded coord -> POIID (from station.shp)
    private final Map<String, String> stationPoiidByCoordKey = new HashMap<>();
    private final Map<String, String> stationNameByPoiid = new HashMap<>();

    private String networkCRS = null;
    private MathTransform transformToNetworkCRS = null;

    // 在 MetroNetworkIntegrator 类中添加字段
    private Map<String, Id<Link>> ptToLink = new HashMap<>(); // 存储地铁站点到link的映射
    private List<SimpleFeature> metroStations = new ArrayList<>(); // 存储地铁站点数据

    public MetroNetworkIntegrator(int coordDecimal, String networkCRS) {
        this.coordDecimal = Math.max(0, coordDecimal);
        this.networkCRS = networkCRS;
    }

    /**
     * 主流程：读取 lines.shp、station.shp，把地铁节点与 links 写入 network。
     * 同时返回每一条 chain 的 forward / reverse link id 列表，供后续生成 schedule 使用。
     *
     * @param linesShp   地铁 lines.shp 路径（含 FlD_road, Shape_Leng 等属性）
     * @param stationShp 地铁 station.shp 路径（含 POIID, STATION_NA）
     * @param network    目标 MATSim Network（将直接被修改）
     * @return Map: key -> List<Id<Link>> （例如 "FLD_123_chain1_fwd"）
     * @throws IOException
     */
    public Map<String, List<Id<Link>>> buildNetworkFromShp(String linesShp, String stationShp, Network network) throws IOException {
        Map<String, List<Id<Link>>> result = new LinkedHashMap<>();

        // 1) load stations -> mapping roundedCoordKey -> POIID
        if (stationShp != null && !stationShp.isEmpty()) {
            loadStations(stationShp);
            System.out.println("Loaded station POI mappings: " + stationPoiidByCoordKey.size());
        }
        // 添加：加载地铁站点用于后续匹配
        loadMetroStations(stationShp);
        // 2) read lines.shp and group by FID_road
        Map<String, List<SimpleFeature>> groups = new LinkedHashMap<>();
        FileDataStore store = FileDataStoreFinder.getDataStore(new File(linesShp));
        SimpleFeatureSource featureSource = store.getFeatureSource();
        setupCoordinateTransform(featureSource); // 设置坐标转换

        try (SimpleFeatureIterator it = featureSource.getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                Object fld = f.getAttribute("FID_road");
                String key = (fld != null) ? fld.toString() : "NO_FID";
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
        }

        System.out.println("Found FID_road groups: " + groups.size());

        // 3) for each group, build segments and chain them
        for (Map.Entry<String, List<SimpleFeature>> entry : groups.entrySet()) {
            String fldKey = entry.getKey();
            List<SimpleFeature> feats = entry.getValue();
            if (feats.isEmpty()) continue;

            // 特殊处理 FID_road=0 的情况：每个线段作为独立线路处理
            if ("0".equals(fldKey)) {
                int chainIdx = 0;
                for (SimpleFeature f : feats) {
                    chainIdx++;

                    // 创建单个线段的特征列表
                    List<SimpleFeature> singleSegmentList = Collections.singletonList(f);
                    List<SegmentRecord> segments = createSegmentsFromFeatures(singleSegmentList);

                    if (!segments.isEmpty()) {
                        // 对于 FID_road=0，每个线段就是一个独立的 chain
                        List<Id<Link>> forwardIds = new ArrayList<>();
                        List<Id<Link>> reverseIds = new ArrayList<>();

                        SegmentRecord seg = segments.get(0);

                        // create or get node ids (and add nodes to network if absent)
                        String fromNodeIdStr = createOrGetNodeId(network, seg.a);
                        String toNodeIdStr = createOrGetNodeId(network, seg.b);

                        Id<Node> fromNodeId = Id.createNodeId(fromNodeIdStr);
                        Id<Node> toNodeId = Id.createNodeId(toNodeIdStr);

                        Node fromNode = network.getNodes().get(fromNodeId);
                        Node toNode = network.getNodes().get(toNodeId);

                        // forward link id
                        String lkId = createLkptId();
                        Id<Link> lkIdObj = Id.createLinkId(lkId);
                        org.matsim.api.core.v01.network.Link link =
                                network.getFactory().createLink(lkIdObj, fromNode, toNode);
                        link.setLength(seg.shapeLen);
                        link.setFreespeed(27.77777777777778);
                        link.setCapacity(9999.0);
                        try {
                            link.setNumberOfLanes(1.0);
                        } catch (Throwable ignored) { }
                        Set<String> modes = new HashSet<>();
                        modes.add("pt");
                        link.setAllowedModes(modes);
                        network.addLink(link);
                        forwardIds.add(lkIdObj);

                        // reverse link id
                        String revId = createLkptId();
                        Id<Link> revIdObj = Id.createLinkId(revId);
                        org.matsim.api.core.v01.network.Link revLink =
                                network.getFactory().createLink(revIdObj, toNode, fromNode);
                        revLink.setLength(seg.shapeLen);
                        revLink.setFreespeed(27.77777777777778);
                        revLink.setCapacity(9999.0);
                        try { revLink.setNumberOfLanes(1.0); } catch (Throwable ignored) { }
                        Set<String> revModes = new HashSet<>();
                        revModes.add("pt");
                        revLink.setAllowedModes(revModes);
                        network.addLink(revLink);
                        reverseIds.add(0, revIdObj);

                        String baseKey = "FLD_" + fldKey + "_chain" + chainIdx;
                        result.put(baseKey + "_fwd", forwardIds);
                        result.put(baseKey + "_rev", reverseIds);

                        // 添加地铁站点匹配逻辑
                        matchStationsToChain(Collections.singletonList(seg), forwardIds, fldKey, chainIdx);

                        System.out.println("Added independent segment for FID_road=0 (chain#" + chainIdx + ")");
                    }
                }
            } else {
                // 处理其他 FID_road 值的情况（正常合并逻辑）
                List<SegmentRecord> segments = createSegmentsFromFeatures(feats);
                if (segments.isEmpty()) continue;

                List<List<SegmentRecord>> chains = buildChainsFromSegments(segments);
                int chainIdx = 0;
                for (List<SegmentRecord> chain : chains) {
                    chainIdx++;

                    // prepare containers of link ids for this chain
                    List<Id<Link>> forwardIds = new ArrayList<>();
                    List<Id<Link>> reverseIds = new ArrayList<>();

                    // create links for each segment (forward and reverse)
                    for (SegmentRecord seg : chain) {
                        // create or get node ids (and add nodes to network if absent)
                        String fromNodeIdStr = createOrGetNodeId(network, seg.a);
                        String toNodeIdStr = createOrGetNodeId(network, seg.b);

                        Id<Node> fromNodeId = Id.createNodeId(fromNodeIdStr);
                        Id<Node> toNodeId = Id.createNodeId(toNodeIdStr);

                        Node fromNode = network.getNodes().get(fromNodeId);
                        Node toNode = network.getNodes().get(toNodeId);

                        // forward link id
                        String lkId = createLkptId();
                        Id<Link> lkIdObj = Id.createLinkId(lkId);
                        org.matsim.api.core.v01.network.Link link =
                                network.getFactory().createLink(lkIdObj, fromNode, toNode);
                        link.setLength(seg.shapeLen);
                        link.setFreespeed(27.77777777777778);
                        link.setCapacity(9999.0);
                        try {
                            link.setNumberOfLanes(1.0);
                        } catch (Throwable ignored) { }
                        Set<String> modes = new HashSet<>();
                        modes.add("pt");
                        link.setAllowedModes(modes);
                        network.addLink(link);
                        forwardIds.add(lkIdObj);

                        // reverse link id
                        String revId = createLkptId();
                        Id<Link> revIdObj = Id.createLinkId(revId);
                        org.matsim.api.core.v01.network.Link revLink =
                                network.getFactory().createLink(revIdObj, toNode, fromNode);
                        revLink.setLength(seg.shapeLen);
                        revLink.setFreespeed(27.77777777777778);
                        revLink.setCapacity(9999.0);
                        try { revLink.setNumberOfLanes(1.0); } catch (Throwable ignored) { }
                        Set<String> revModes = new HashSet<>();
                        revModes.add("pt");
                        revLink.setAllowedModes(revModes);
                        network.addLink(revLink);
                        reverseIds.add(0, revIdObj);
                    }

                    String baseKey = "FLD_" + fldKey + "_chain" + chainIdx;
                    result.put(baseKey + "_fwd", forwardIds);
                    result.put(baseKey + "_rev", reverseIds);

                    // 添加地铁站点匹配逻辑（正向）
                    matchStationsToChain(chain, forwardIds, fldKey, chainIdx);
                    // 添加地铁站点匹配逻辑（反向）
                    matchStationsToChainReverse(chain, reverseIds, fldKey, chainIdx);

                    System.out.println("Added chain for group " + fldKey + " (chain#" + chainIdx + ") forwardLinks=" + forwardIds.size());
                }
            }
        }

        return result;
    }

    // --- helper classes and methods ---

    private static class SegmentRecord {
        final Coordinate a, b;
        final double shapeLen;
        final SimpleFeature feature;
        SegmentRecord(Coordinate a, Coordinate b, double shapeLen, SimpleFeature feature) {
            this.a = a;
            this.b = b;
            this.shapeLen = shapeLen;
            this.feature = feature;
        }
    }

    // 在 MetroNetworkIntegrator 类中添加方法
    private void matchStationsToChain(List<SegmentRecord> chain, List<Id<Link>> forwardIds, String fldKey, int chainIdx) {
        if (chain.isEmpty() || forwardIds.isEmpty() || metroStations.isEmpty()) return;

        // 获取链的起点和终点
        Coordinate chainStart = chain.get(0).a;
        Coordinate chainEnd = chain.get(chain.size() - 1).b;

        // 为每个地铁站点检查是否匹配到链的端点
        for (SimpleFeature station : metroStations) {
            Geometry geom = (Geometry) station.getDefaultGeometry();
            if (!(geom instanceof Point)) continue;

            Coordinate stationCoord = transformCoordinate(geom.getCoordinate());
            Object poiidObj = station.getAttribute("POIID");
            if (poiidObj == null) continue;

            String poiid = poiidObj.toString();
            boolean alreadyMatched = false; // 添加标志避免重复匹配

            // 检查是否匹配起点（5米阈值）
            if (stationCoord.distance(chainStart) <= 5.0 && !alreadyMatched) {
                String ptId = poiid + "X" + fldKey + "_" + chainIdx + "_start";
                if (!ptToLink.containsKey(ptId) && !forwardIds.isEmpty()) {
                    ptToLink.put(ptId, forwardIds.get(0));
                    System.out.println("Matched station " + poiid + " to chain start, link: " + forwardIds.get(0));
                    alreadyMatched = true;
                }
            }

            // 检查是否匹配终点（5米阈值）
            if (stationCoord.distance(chainEnd) <= 5.0 && !alreadyMatched) {
                String ptId = poiid + "X" + fldKey + "_" + chainIdx + "_end";
                if (!ptToLink.containsKey(ptId) && !forwardIds.isEmpty()) {
                    ptToLink.put(ptId, forwardIds.get(forwardIds.size() - 1));
                    System.out.println("Matched station " + poiid + " to chain end, link: " + forwardIds.get(forwardIds.size() - 1));
                    alreadyMatched = true;
                }
            }

            // 检查是否匹配中间的任何链接点（仅当尚未匹配时）
            if (!alreadyMatched) {
                for (int i = 0; i < chain.size(); i++) {
                    Coordinate segmentStart = chain.get(i).a;
                    Coordinate segmentEnd = chain.get(i).b;

                    // 检查站点是否在线段附近
                    LineSegment segment = new LineSegment(segmentStart, segmentEnd);
                    if (segment.distance(stationCoord) <= 5.0) {
                        String ptId = poiid + "X" + fldKey + "_" + chainIdx + "_mid_" + i;
                        // 找到该线段对应的链接
                        if (i < forwardIds.size()) {
                            Id<Link> linkId = forwardIds.get(i);
                            if (!ptToLink.containsKey(ptId)) {
                                ptToLink.put(ptId, linkId);
                                System.out.println("Matched station " + poiid + " to chain segment " + i + ", link: " + linkId);
                            }
                        }
                        break; // 找到匹配就退出，避免重复匹配
                    }
                }
            }
        }
    }

    // 添加反向线路的站点匹配方法
    private void matchStationsToChainReverse(List<SegmentRecord> chain, List<Id<Link>> reverseIds, String fldKey, int chainIdx) {
        if (chain.isEmpty() || reverseIds.isEmpty() || metroStations.isEmpty()) return;

        // 获取链的起点和终点（注意：反向线路的起点和终点与正向相反）
        Coordinate chainStart = chain.get(chain.size() - 1).b; // 反向线路的起点是正向线路的终点
        Coordinate chainEnd = chain.get(0).a; // 反向线路的终点是正向线路的起点

        // 为每个地铁站点检查是否匹配到链的端点
        for (SimpleFeature station : metroStations) {
            Geometry geom = (Geometry) station.getDefaultGeometry();
            if (!(geom instanceof Point)) continue;

            Coordinate stationCoord = transformCoordinate(geom.getCoordinate());
            Object poiidObj = station.getAttribute("POIID");
            if (poiidObj == null) continue;

            String poiid = poiidObj.toString();
            boolean alreadyMatched = false; // 添加标志避免重复匹配

            // 检查是否匹配起点（5米阈值）
            if (stationCoord.distance(chainStart) <= 5.0 && !alreadyMatched) {
                String ptId = poiid + "X" + fldKey + "_" + chainIdx + "_rev_start";
                if (!ptToLink.containsKey(ptId) && !reverseIds.isEmpty()) {
                    ptToLink.put(ptId, reverseIds.get(0));
                    System.out.println("Matched station " + poiid + " to reverse chain start, link: " + reverseIds.get(0));
                    alreadyMatched = true;
                }
            }

            // 检查是否匹配终点（5米阈值）
            if (stationCoord.distance(chainEnd) <= 5.0 && !alreadyMatched) {
                String ptId = poiid + "X" + fldKey + "_" + chainIdx + "_rev_end";
                if (!ptToLink.containsKey(ptId) && !reverseIds.isEmpty()) {
                    ptToLink.put(ptId, reverseIds.get(reverseIds.size() - 1));
                    System.out.println("Matched station " + poiid + " to reverse chain end, link: " + reverseIds.get(reverseIds.size() - 1));
                    alreadyMatched = true;
                }
            }
            // 添加中间站点匹配逻辑
            if (!alreadyMatched) {
                for (int i = 0; i < chain.size(); i++) {
                    Coordinate segmentStart = chain.get(i).a;
                    Coordinate segmentEnd = chain.get(i).b;

                    // 检查站点是否在线段附近
                    LineSegment segment = new LineSegment(segmentStart, segmentEnd);
                    if (segment.distance(stationCoord) <= 5.0) {
                        String ptId = poiid + "X" + fldKey + "_" + chainIdx + "_rev_mid_" + i;
                        // 找到该线段对应的链接（注意反向线路的链接顺序）
                        int reverseIndex = reverseIds.size() - 1 - i;
                        if (reverseIndex >= 0 && reverseIndex < reverseIds.size()) {
                            Id<Link> linkId = reverseIds.get(reverseIndex);
                            if (!ptToLink.containsKey(ptId)) {
                                ptToLink.put(ptId, linkId);
                                System.out.println("Matched station " + poiid + " to reverse chain segment " + i + ", link: " + linkId);
                            }
                        }
                        break; // 找到匹配就退出，避免重复匹配
                    }
                }
            }
        }
    }

    private List<SegmentRecord> createSegmentsFromFeatures(List<SimpleFeature> feats) {
        List<SegmentRecord> segments = new ArrayList<>();
        for (SimpleFeature f : feats) {
            Geometry g = (Geometry) f.getDefaultGeometry();
            LineString ls = null;
            if (g instanceof LineString) ls = (LineString) g;
            else if (g instanceof MultiLineString) {
                MultiLineString mls = (MultiLineString) g;
                if (mls.getNumGeometries() > 0) ls = (LineString) mls.getGeometryN(0);
            }
            if (ls == null) continue;
            Coordinate a = transformCoordinate(ls.getCoordinateN(0)); // 应用坐标转换
            Coordinate b = transformCoordinate(ls.getCoordinateN(ls.getNumPoints() - 1)); // 应用坐标转换
            double shapeLen = parseDoubleProperty(f.getAttribute("Shape_Le_1"), a.distance(b));
            segments.add(new SegmentRecord(a, b, shapeLen, f));
        }
        return segments;
    }

    private List<List<SegmentRecord>> buildChainsFromSegments(List<SegmentRecord> segments) {
        Map<String, List<Integer>> endpointMap = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            SegmentRecord s = segments.get(i);
            endpointMap.computeIfAbsent(coordKey(s.a), k -> new ArrayList<>()).add(i);
            endpointMap.computeIfAbsent(coordKey(s.b), k -> new ArrayList<>()).add(i);
        }

        Set<Integer> used = new HashSet<>();
        List<List<SegmentRecord>> chains = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            if (used.contains(i)) continue;
            LinkedList<SegmentRecord> chain = new LinkedList<>();
            chain.add(segments.get(i));
            used.add(i);

            boolean extended;
            // extend forward
            do {
                extended = false;
                String tailKey = coordKey(chain.getLast().b);
                List<Integer> candidates = endpointMap.getOrDefault(tailKey, Collections.emptyList());
                for (int idx : candidates) {
                    if (used.contains(idx)) continue;
                    SegmentRecord cand = segments.get(idx);
                    if (coordEq(cand.a, chain.getLast().b)) {
                        chain.add(cand);
                        used.add(idx);
                        extended = true;
                        break;
                    } else if (coordEq(cand.b, chain.getLast().b)) {
                        // append reversed
                        chain.add(new SegmentRecord(cand.b, cand.a, cand.shapeLen, cand.feature));
                        used.add(idx);
                        extended = true;
                        break;
                    }
                }
            } while (extended);

            // extend backward
            do {
                extended = false;
                String headKey = coordKey(chain.getFirst().a);
                List<Integer> candidates = endpointMap.getOrDefault(headKey, Collections.emptyList());
                for (int idx : candidates) {
                    if (used.contains(idx)) continue;
                    SegmentRecord cand = segments.get(idx);
                    if (coordEq(cand.b, chain.getFirst().a)) {
                        chain.addFirst(cand);
                        used.add(idx);
                        extended = true;
                        break;
                    } else if (coordEq(cand.a, chain.getFirst().a)) {
                        chain.addFirst(new SegmentRecord(cand.b, cand.a, cand.shapeLen, cand.feature));
                        used.add(idx);
                        extended = true;
                        break;
                    }
                }
            } while (extended);

            chains.add(new ArrayList<>(chain));
        }

        return chains;
    }

    // 添加加载地铁站点的方法
    private void loadMetroStations(String stationShp) throws IOException {
        if (stationShp == null || stationShp.isEmpty()) return;

        FileDataStore store = FileDataStoreFinder.getDataStore(new File(stationShp));
        SimpleFeatureSource featureSource = store.getFeatureSource();

        try (SimpleFeatureIterator it = featureSource.getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                metroStations.add(f);
            }
        }

        System.out.println("Loaded metro stations: " + metroStations.size());
    }

    // create or reuse node in network; node id uses NDPT + POIID if matched, else NDPT + counter
    private String createOrGetNodeId(Network network, Coordinate c) {
        String key = coordKey(c);
        String idStr;
        if (stationPoiidByCoordKey.containsKey(key)) {
            String poiid = stationPoiidByCoordKey.get(key);
            idStr = "NDPT" + poiid;
        } else {
            idStr = "NDPT" + String.format("%011d", ndptCounter++);
        }
        Id<Node> nodeId = Id.createNodeId(idStr);
        if (!network.getNodes().containsKey(nodeId)) {
            // create node (MATSim Coord class availability assumed; if your MATSim version uses a different Coord impl, adjust)
            Coord coord = new org.matsim.api.core.v01.Coord(c.x, c.y);
            Node node = network.getFactory().createNode(nodeId, coord);
            network.addNode(node);
        }
        return idStr;
    }

    private String createLkptId() {
        return "LKPT" + String.format("%011d", lkptCounter++);
    }

    private String coordKey(Coordinate c) {
        double scale = Math.pow(10.0, coordDecimal);
        double x = Math.round(c.x * scale) / scale;
        double y = Math.round(c.y * scale) / scale;
        // use coordDecimal to control rounding; format with enough decimals to be stable
        return String.format(Locale.ROOT, "%." + Math.max(0, coordDecimal) + "f_%" + (1 + coordDecimal) + "f", x, y).replace(',', '.');
    }

    private boolean coordEq(Coordinate a, Coordinate b) {
        return coordKey(a).equals(coordKey(b));
    }

    private double parseDoubleProperty(Object obj, double fallback) {
        if (obj == null) return fallback;
        try {
            if (obj instanceof Number) return ((Number) obj).doubleValue();
            return Double.parseDouble(obj.toString());
        } catch (Exception ex) {
            return fallback;
        }
    }

    // load station shp to build mapping: roundedCoord -> POIID and POIID->STATION_NA
    private void loadStations(String stationShp) throws IOException {
        FileDataStore store = FileDataStoreFinder.getDataStore(new File(stationShp));
        SimpleFeatureSource featureSource = store.getFeatureSource();
        MathTransform originalTransform = this.transformToNetworkCRS; // 保存原始转换
        setupCoordinateTransform(featureSource); // 设置新的坐标转换

        try (SimpleFeatureIterator it = featureSource.getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                Geometry g = (Geometry) f.getDefaultGeometry();
                if (!(g instanceof Point)) continue;
                Coordinate c = transformCoordinate(g.getCoordinate()); // 应用坐标转换
                Object poiidObj = f.getAttribute("POIID");
                Object nameObj = f.getAttribute("STATION_NA");
                if (poiidObj == null) continue;
                String poiid = poiidObj.toString();
                String name = (nameObj != null) ? nameObj.toString() : poiid;
                String key = coordKey(c);
                stationPoiidByCoordKey.put(key, poiid);
                stationNameByPoiid.put(poiid, name);
            }
        }

        this.transformToNetworkCRS = originalTransform; // 恢复原始转换
    }
    // 添加坐标转换方法
    private void setupCoordinateTransform(SimpleFeatureSource featureSource) {
        if (networkCRS == null || networkCRS.isEmpty()) {
            return;
        }

        try {
            // 获取 shapefile 的 CRS
            SimpleFeatureCollection features = featureSource.getFeatures();
            CoordinateReferenceSystem sourceCRS = features.getSchema().getCoordinateReferenceSystem();

            if (sourceCRS == null) {
                // 如果 shapefile 没有 CRS，则假定为 WGS84 (经纬度)
                sourceCRS = CRS.decode("EPSG:4326");
            }

            CoordinateReferenceSystem targetCRS = CRS.decode(networkCRS);
            this.transformToNetworkCRS = CRS.findMathTransform(sourceCRS, targetCRS, true);
        } catch (Exception e) {
            System.err.println("Warning: Failed to setup CRS transformation: " + e.getMessage());
        }
    }

    // 添加坐标转换方法
    private Coordinate transformCoordinate(Coordinate coord) {
        if (transformToNetworkCRS != null) {
            try {
                org.locationtech.jts.geom.Coordinate sourceCoord = new org.locationtech.jts.geom.Coordinate(coord.x, coord.y);
                org.locationtech.jts.geom.Coordinate targetCoord = JTS.transform(sourceCoord, null, transformToNetworkCRS);
                return new Coordinate(targetCoord.x, targetCoord.y);
            } catch (Exception e) {
                System.err.println("Warning: Failed to transform coordinate: " + e.getMessage());
            }
        }
        return coord;
    }

    // 在 MetroNetworkIntegrator 类中添加公共方法
    public Map<String, Id<Link>> getPtToLinkMapping() {
        return ptToLink;
    }
}
