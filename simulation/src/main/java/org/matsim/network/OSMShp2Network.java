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
        // 其它保留，过滤掉无关道路
    }
    private static long globalLinkId = 1;

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
                TransformationFactory.WGS84, "EPSG:32649"); // WGS84 -> UTM Zone 49N

        // === 1. 读取 Shapefile ===
        Map<String, Object> map = new HashMap<>();
        map.put("url", new File(shpFile).toURI().toURL());
        DataStore dataStore = DataStoreFinder.getDataStore(map);
        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        try (var features = source.getFeatures().features()) {
            long linkIdCounter = 1;

            while (features.hasNext()) {
                SimpleFeature feature = features.next();

                String fclass = (String) feature.getAttribute("fclass");
                if (fclass == null) continue;
                fclass = fclass.toLowerCase();

                if (excludedClasses.contains(fclass)) continue;

                int maxspeed = 0;
                Object maxspeedAttr = feature.getAttribute("maxspeed");
                if (maxspeedAttr != null) {
                    try {
                        String ms = maxspeedAttr.toString().trim().split(" ")[0]; // 去掉 "km/h"
                        maxspeed = Integer.parseInt(ms);
                    } catch (Exception e) {
                        maxspeed = 0; // 解析失败用默认
                    }
                }
                double[] defaults = roadDefaults.getOrDefault(fclass, new double[]{13.9, 1});
                double freespeed = (maxspeed > 0 ? maxspeed / 3.6 : defaults[0]);
                int lanes = (int) defaults[1];

                String oneway = (String) feature.getAttribute("oneway");
                if (oneway == null) oneway = "B";

                Object geom = feature.getDefaultGeometry();
                if (geom instanceof MultiLineString) {
                    MultiLineString mls = (MultiLineString) geom;
                    for (int i = 0; i < mls.getNumGeometries(); i++) {
                        processLine((LineString) mls.getGeometryN(i), network, ct,
                                freespeed, lanes, oneway);
                    }
                } else if (geom instanceof LineString) {
                    LineString line = (LineString) geom;
                    processLine(line, network, ct, freespeed, lanes, oneway);
                }
                System.out.println("Processing fclass=" + fclass
                        + ", oneway=" + oneway
                        + ", maxspeed=" + feature.getAttribute("maxspeed"));

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
                                    double freespeed, int lanes, String oneway) {
        Coordinate[] coords = line.getCoordinates();
        if (coords.length < 2) return;

        for (int i = 0; i < coords.length - 1; i++) {
            long id = globalLinkId++;

            org.matsim.api.core.v01.Coord matSimCoord1 = new org.matsim.api.core.v01.Coord(coords[i].x, coords[i].y);
            org.matsim.api.core.v01.Coord matSimCoord2 = new org.matsim.api.core.v01.Coord(coords[i + 1].x, coords[i + 1].y);

            org.matsim.api.core.v01.Coord c1 = ct.transform(matSimCoord1);
            org.matsim.api.core.v01.Coord c2 = ct.transform(matSimCoord2);


            // 修复后代码片段
            Id<Node> fromNodeId = Id.createNodeId(c1.getX() + "_" + c1.getY());
            Id<Node> toNodeId = Id.createNodeId(c2.getX() + "_" + c2.getY());

            Node fromNode;
            if (network.getNodes().containsKey(fromNodeId)) {
                fromNode = network.getNodes().get(fromNodeId);
            } else {
                fromNode = network.getFactory().createNode(fromNodeId, new org.matsim.api.core.v01.Coord(c1.getX(), c1.getY()));
                network.addNode(fromNode);
            }

            Node toNode;
            if (network.getNodes().containsKey(toNodeId)) {
                toNode = network.getNodes().get(toNodeId);
            } else {
                toNode = network.getFactory().createNode(toNodeId, new org.matsim.api.core.v01.Coord(c2.getX(), c2.getY()));
                network.addNode(toNode);
            }

            double length = org.matsim.core.utils.geometry.CoordUtils.calcEuclideanDistance(c1, c2);
            boolean isOneWay = false;
            if (oneway != null) {
                if (oneway.equalsIgnoreCase("yes") || oneway.equalsIgnoreCase("1") || oneway.equalsIgnoreCase("true")) {
                    isOneWay = true;
                }
                if (oneway.equalsIgnoreCase("F")) { // 如果你的shp导出成F/B模式
                    isOneWay = true;
                }
            }

            if (!isOneWay) { // 双向
                Link link = network.getFactory().createLink(Id.createLinkId("L" + id + "_f"), fromNode, toNode);
                link.setLength(length);
                link.setFreespeed(freespeed);
                link.setNumberOfLanes(lanes);
                link.setCapacity(2000 * lanes);
                network.addLink(link);

                Link backLink = network.getFactory().createLink(Id.createLinkId("L" + id + "_b"), toNode, fromNode);
                backLink.setLength(length);
                backLink.setFreespeed(freespeed);
                backLink.setNumberOfLanes(lanes);
                backLink.setCapacity(2000 * lanes);
                network.addLink(backLink);
            } else { // 单向
                Link link = network.getFactory().createLink(Id.createLinkId("L" + id), fromNode, toNode);
                link.setLength(length);
                link.setFreespeed(freespeed);
                link.setNumberOfLanes(lanes);
                link.setCapacity(2000 * lanes);
                network.addLink(link);
            }
        }
    }
}
