package org.matsim.network;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * MetroNetworkIntegrator
 * 功能：
 *  - 读取地铁线段 Shapefile (lines.shp)
 *  - 拼接成 node + link
 *  - 自动生成正反向 link
 *  - 写出 MATSim network.xml
 */
public class MetroNetworkIntegrator {

    private static class Node {
        String id;
        double x, y;

        Node(String id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    private static class Link {
        String id;
        String fromNode;
        String toNode;
        double length;

        Link(String id, String fromNode, String toNode, double length) {
            this.id = id;
            this.fromNode = fromNode;
            this.toNode = toNode;
            this.length = length;
        }
    }

    private Map<String, Node> nodes = new LinkedHashMap<>();
    private List<Link> links = new ArrayList<>();
    private int nodeCounter = 1;
    private int linkCounter = 1;

    public void buildNetworkFromShp(String shpPath, String outputPath) throws IOException {
        FileDataStore store = FileDataStoreFinder.getDataStore(new File(shpPath));
        try (SimpleFeatureIterator it = store.getFeatureSource().getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature feature = it.next();
                Geometry geometry = (Geometry) feature.getDefaultGeometry();

                if (!(geometry instanceof LineString) && !(geometry instanceof MultiLineString)) {
                    continue;
                }

                List<Coordinate> coords = new ArrayList<>();
                if (geometry instanceof LineString) {
                    coords.addAll(Arrays.asList(((LineString) geometry).getCoordinates()));
                } else if (geometry instanceof MultiLineString) {
                    MultiLineString mls = (MultiLineString) geometry;
                    for (int i = 0; i < mls.getNumGeometries(); i++) {
                        LineString ls = (LineString) mls.getGeometryN(i);
                        coords.addAll(Arrays.asList(ls.getCoordinates()));
                    }
                }

                // 逐点生成 node & link
                for (int i = 0; i < coords.size() - 1; i++) {
                    Coordinate c1 = coords.get(i);
                    Coordinate c2 = coords.get(i + 1);

                    String nodeId1 = getOrCreateNode(feature, c1);
                    String nodeId2 = getOrCreateNode(feature, c2);

                    double dist = c1.distance(c2);

                    // 正向
                    links.add(new Link("L" + (linkCounter++), nodeId1, nodeId2, dist));
                    // 反向
                    links.add(new Link("L" + (linkCounter++), nodeId2, nodeId1, dist));
                }
            }
        }

        writeNetworkXml(outputPath);
    }

    private String getOrCreateNode(SimpleFeature feature, Coordinate coord) {
        String nodeId;
        // 优先使用属性表字段
        Object ndpt = feature.getAttribute("NDPT");
        Object poiid = feature.getAttribute("POIID");
        if (ndpt != null && poiid != null) {
            nodeId = ndpt.toString() + "_" + poiid.toString();
        } else {
            nodeId = "NDPT_" + (nodeCounter++);
        }

        String key = nodeId + "_" + coord.x + "_" + coord.y;
        if (!nodes.containsKey(key)) {
            nodes.put(key, new Node(nodeId, coord.x, coord.y));
        }
        return nodeId;
    }

    private void writeNetworkXml(String outputPath) throws IOException {
        try (FileWriter fw = new FileWriter(outputPath)) {
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<network name=\"metro-network\">\n");
            fw.write("  <nodes>\n");
            for (Node n : nodes.values()) {
                fw.write(String.format("    <node id=\"%s\" x=\"%.3f\" y=\"%.3f\" />\n", n.id, n.x, n.y));
            }
            fw.write("  </nodes>\n");

            fw.write("  <links>\n");
            for (Link l : links) {
                fw.write(String.format(
                        "    <link id=\"%s\" from=\"%s\" to=\"%s\" length=\"%.1f\" capacity=\"9999\" freespeed=\"40\" permlanes=\"2\" />\n",
                        l.id, l.fromNode, l.toNode, l.length));
            }
            fw.write("  </links>\n");

            fw.write("</network>\n");
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("用法: java MetroNetworkIntegrator <lines.shp> <output network.xml>");
            return;
        }
        String shpPath = args[0];
        String outXml = args[1];
        MetroNetworkIntegrator builder = new MetroNetworkIntegrator();
        builder.buildNetworkFromShp(shpPath, outXml);
        System.out.println("完成: " + outXml);
    }
}
