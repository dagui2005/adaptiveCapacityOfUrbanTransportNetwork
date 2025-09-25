package org.matsim.network;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.opengis.feature.simple.SimpleFeature;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.api.core.v01.Coord;

import java.io.File;
import java.util.*;

public class OSMShp2Network {

    // fclass → 默认速度(m/s) + 车道数
    private static final Map<String, double[]> roadDefaults = new HashMap<>();
    static {
        roadDefaults.put("motorway", new double[]{33.3, 3});       // 120 km/h
        roadDefaults.put("motorway_link", new double[]{22.2, 2});
        roadDefaults.put("trunk", new double[]{27.8, 3});          // 100 km/h
        roadDefaults.put("trunk_link", new double[]{22.2, 2});
        roadDefaults.put("primary", new double[]{22.2, 2});        // 80 km/h
        roadDefaults.put("primary_link", new double[]{22.2, 2});
        roadDefaults.put("secondary", new double[]{16.7, 2});      // 60 km/h
        roadDefaults.put("secondary_link", new double[]{16.7, 2});
        roadDefaults.put("tertiary", new double[]{13.9, 2});       // 50 km/h
        roadDefaults.put("tertiary_link", new double[]{13.9, 2});
    }

    // 修改点：短编号生成器
    private static long globalNodeId = 1;
    private static long globalLinkId = 1;
    private static Map<String, Integer> linkSeqMap = new HashMap<>();

    // 修改点：全局坐标→节点映射表
    private static final Map<String, Id<Node>> coordNodeMap = new HashMap<>();

    // 修改点：坐标保留小数位数（可手动调节合并精度）
    private static final int precisionDigits = 3;

    // 需要排除的 fclass
    private static final Set<String> excludedClasses = new HashSet<>(Arrays.asList(
            "footway", "living_street", "residential", "service", "steps",
            "track", "track_grade1", "track_grade2", "track_grade4",
            "unclassified", "unknown"
    ));

    public static void main(String[] args) throws Exception {
        String shpFile = "D:\\Luan\\2025-09\\MATSim\\GuangzhouOSM\\RoadsOSM.shp";
        String outputFile = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\Network.xml";
        System.out.println("Converting " + shpFile + " → " + outputFile);

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        Network network = scenario.getNetwork();

        // 广州用 UTM48N (WGS84经纬度 → 米制坐标)
        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(
                TransformationFactory.WGS84, "EPSG:32649");

        // 修改点：在 network 属性中写入坐标系信息
        network.getAttributes().putAttribute("coordinateReferenceSystem", "EPSG:32649");

        // === 1. 读取 Shapefile ===
        Map<String, Object> map = new HashMap<>();
        map.put("url", new File(shpFile).toURI().toURL());
        DataStore dataStore = DataStoreFinder.getDataStore(map);
        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        try (var features = source.getFeatures().features()) {
            while (features.hasNext()) {
                SimpleFeature feature = features.next();

                String fclass = (String) feature.getAttribute("fclass");
                if (fclass == null) continue;
                fclass = fclass.toLowerCase();

                // 修改点：过滤掉无关道路
                if (excludedClasses.contains(fclass)) continue;

                int maxspeed = 0;
                Object maxspeedAttr = feature.getAttribute("maxspeed");
                if (maxspeedAttr != null) {
                    try {
                        String ms = maxspeedAttr.toString().trim().split(" ")[0];
                        maxspeed = Integer.parseInt(ms);
                    } catch (Exception e) {
                        maxspeed = 0;
                    }
                }
                double[] defaults = roadDefaults.getOrDefault(fclass, new double[]{13.9, 1});
                double freespeed = (maxspeed > 0 ? maxspeed / 3.6 : defaults[0]);
                int lanes = (int) defaults[1];

                String oneway = (String) feature.getAttribute("oneway");
                if (oneway == null) oneway = "B";

                String osmId = feature.getAttribute("osm_id").toString();

                Object geom = feature.getDefaultGeometry();
                if (geom instanceof MultiLineString) {
                    MultiLineString mls = (MultiLineString) geom;
                    for (int i = 0; i < mls.getNumGeometries(); i++) {
                        processLine((LineString) mls.getGeometryN(i), network, ct,
                                freespeed, lanes, oneway, osmId);
                    }
                } else if (geom instanceof LineString) {
                    processLine((LineString) geom, network, ct, freespeed, lanes, oneway, osmId);
                }
            }
        }
        dataStore.dispose();

        // === 2. 清理孤立子图 ===
        new NetworkCleaner().run(network);

        // === 3. 输出 MATSim Network.xml ===
        new NetworkWriter(network).write(outputFile);
        System.out.println("✅ MATSim Network 已生成: " + outputFile);
    }

    private static void processLine(LineString line, Network network, CoordinateTransformation ct,
                                    double freespeed, int lanes, String oneway, String osmId) {
        Coordinate[] coords = line.getCoordinates();

        // 修改点：在 LineString 内部按夹角简化 (5°)
        List<Coordinate> simplified = simplifyCoords(coords);
        if (simplified.size() < 2) return;

        for (int i = 0; i < simplified.size() - 1; i++) {
            Coord c1 = ct.transform(new Coord(simplified.get(i).x, simplified.get(i).y));
            Coord c2 = ct.transform(new Coord(simplified.get(i + 1).x, simplified.get(i + 1).y));

            Node fromNode = getOrCreateNode(c1, network);
            Node toNode = getOrCreateNode(c2, network);

            double length = org.matsim.core.utils.geometry.CoordUtils.calcEuclideanDistance(c1, c2);

            boolean isOneWay = false;
            if (oneway != null) {
                if (oneway.equalsIgnoreCase("yes") || oneway.equalsIgnoreCase("1") || oneway.equalsIgnoreCase("true")) {
                    isOneWay = true;
                }
                if (oneway.equalsIgnoreCase("F")) {
                    isOneWay = true;
                }
            }

            // 修改点：短编号 linkId
            Id<Link> linkIdFwd = createLinkId(osmId);
            Link link = network.getFactory().createLink(linkIdFwd, fromNode, toNode);
            link.setLength(length);
            link.setFreespeed(freespeed);
            link.setNumberOfLanes(lanes);
            link.setCapacity(2000 * lanes);
            network.addLink(link);

            if (!isOneWay) {
                Id<Link> linkIdBwd = createLinkId(osmId);
                Link backLink = network.getFactory().createLink(linkIdBwd, toNode, fromNode);
                backLink.setLength(length);
                backLink.setFreespeed(freespeed);
                backLink.setNumberOfLanes(lanes);
                backLink.setCapacity(2000 * lanes);
                network.addLink(backLink);
            }
        }
    }

    // 修改点：夹角简化 (5°) —— 合并近似直线形状点
    private static List<Coordinate> simplifyCoords(Coordinate[] coords) {
        List<Coordinate> result = new ArrayList<>();
        if (coords.length < 2) return result;

        // 永远保留起点
        result.add(coords[0]);

        for (int i = 1; i < coords.length - 1; i++) {
            Coordinate prev = coords[i - 1];
            Coordinate curr = coords[i];
            Coordinate next = coords[i + 1];

            double dx1 = curr.x - prev.x;
            double dy1 = curr.y - prev.y;
            double dx2 = next.x - curr.x;
            double dy2 = next.y - curr.y;

            double dot = dx1 * dx2 + dy1 * dy2;
            double norm1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
            double norm2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
            if (norm1 < 1e-6 || norm2 < 1e-6) {
                result.add(curr); // 避免零长度向量
                continue;
            }

            double cosTheta = dot / (norm1 * norm2);
            cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta)); // 防止溢出
            double angle = Math.acos(cosTheta);

            if (angle < Math.toRadians(15)) {
                continue; // 修改点：合并近似直线形状点
            }
            result.add(curr);
        }

        // 永远保留终点
        result.add(coords[coords.length - 1]);
        return result;
    }

    // === 修改点：根据坐标复用节点 ===
    private static Node getOrCreateNode(Coord coord, Network network) {
        String key = formatCoord(coord, precisionDigits);

        Id<Node> nodeId = coordNodeMap.get(key);
        if (nodeId != null) {
            return network.getNodes().get(nodeId);
        }

        nodeId = Id.createNodeId("ND" + String.format("%06d", globalNodeId++));
        Node node = network.getFactory().createNode(nodeId, coord);
        network.addNode(node);
        coordNodeMap.put(key, nodeId);
        return node;
    }

    // 修改点：格式化坐标为字符串 (保留指定小数位)
    private static String formatCoord(Coord coord, int precision) {
        return String.format("%." + precision + "f_%" + precision + "f", coord.getX(), coord.getY());
    }

    // === 短编号生成器 ===
    private static Id<Link> createLinkId(String osmId) {
        int seq = linkSeqMap.getOrDefault(osmId, 0) + 1;
        linkSeqMap.put(osmId, seq);
        return Id.createLinkId("LK" + osmId + "_" + String.format("%03d", seq));
    }
}
