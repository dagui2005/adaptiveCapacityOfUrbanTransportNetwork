package org.matsim.network;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
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

    public MetroNetworkIntegrator(int coordDecimal) {
        this.coordDecimal = Math.max(0, coordDecimal);
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

        // 2) read lines.shp and group by FlD_road
        Map<String, List<SimpleFeature>> groups = new LinkedHashMap<>();
        FileDataStore store = FileDataStoreFinder.getDataStore(new File(linesShp));
        try (SimpleFeatureIterator it = store.getFeatureSource().getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                Object fld = f.getAttribute("FlD_road");
                String key = (fld != null) ? fld.toString() : "NO_FLD";
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
        }

        System.out.println("Found FlD_road groups: " + groups.size());

        // 3) for each group, build segments and chain them
        for (Map.Entry<String, List<SimpleFeature>> entry : groups.entrySet()) {
            String fldKey = entry.getKey();
            List<SimpleFeature> feats = entry.getValue();
            if (feats.isEmpty()) continue;

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
                Coordinate a = ls.getCoordinateN(0);
                Coordinate b = ls.getCoordinateN(ls.getNumPoints() - 1);
                double shapeLen = parseDoubleProperty(f.getAttribute("Shape_Leng"), a.distance(b));
                segments.add(new SegmentRecord(a, b, shapeLen, f));
            }
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
                    // numberOfLanes may accept double via setNumberOfLanes(double) in some versions
                    try {
                        // setNumberOfLanes exists in many MATSim versions (double)
                        link.setNumberOfLanes(1.0);
                    } catch (Throwable ignored) { }
                    // store modes as attribute (safer across MATSim versions)
                    link.getAttributes().putAttribute("modes", "pt");
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
                    revLink.getAttributes().putAttribute("modes", "pt");
                    network.addLink(revLink);
                    // For reverse path order, we will add rev link ids at front so that rev list corresponds to reverse travel order
                    reverseIds.add(0, revIdObj);
                }

                // store in result map with keys naming the group + chain index
                String baseKey = "FLD_" + fldKey + "_chain" + chainIdx;
                result.put(baseKey + "_fwd", forwardIds);
                result.put(baseKey + "_rev", reverseIds);

                System.out.println("Added chain for group " + fldKey + " (chain#" + chainIdx + ") forwardLinks=" + forwardIds.size());
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
        try (SimpleFeatureIterator it = store.getFeatureSource().getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                Geometry g = (Geometry) f.getDefaultGeometry();
                if (!(g instanceof Point)) continue;
                Coordinate c = g.getCoordinate();
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
    }

    // sample main for quick test - writes network to disk after integration
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: MetroNetworkIntegrator <lines.shp> <station.shp> <outNetwork.xml>");
            return;
        }
        String lines = args[0];
        String stations = args[1];
        String out = args[2];

        // create an empty MATSim network and call integrator
        Network network = org.matsim.core.network.NetworkUtils.createNetwork();
        MetroNetworkIntegrator integrator = new MetroNetworkIntegrator(5);
        Map<String, List<Id<Link>>> map = integrator.buildNetworkFromShp(lines, stations, network);

        // write final network
        new org.matsim.core.network.io.NetworkWriter(network).write(out);
        System.out.println("Wrote network to " + out + ". Created metro line entries: " + map.keySet().size());
    }
}
