package org.matsim.analysis.network;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;

import java.util.*;

/**
 * @author: Chunhong li
 * @date: 2022年01月15日 10:56
 * @Description:
 */
public class SimpleAnalyzer {
    public static void main(String[] args) {
//        hamburg network
//        Network network = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-v3.0-network-with-pt.xml.gz");
//        Los Angeles network
        double threshold = 0.001;
        ArrayList<HashMap<Set<String>, Integer>> linkCountAll = new ArrayList<>();
        while (threshold <= 0.01) {
            Network network = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-direct failure-threshold" + threshold + "m.xml.gz");
            SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
//        simpleAnalyzer.modeAnalysis(network);
            System.out.println("threshold: " + threshold);
            HashMap<Set<String>, Integer> linkCount = simpleAnalyzer.linkCount(network);
            linkCountAll.add(linkCount);
            threshold += 0.001;
        }

        threshold = 0.001;
        for (HashMap<Set<String>, Integer> linkCount : linkCountAll) {
            System.out.println("threshold: " + threshold);
            System.out.println(linkCount);
            threshold += 0.001;
        }
//        print the scope of the network. Todo: be careful about the coordination transformation.
//        CoordinateTransformation epsg3310ToWGS84 = TransformationFactory.getCoordinateTransformation("epsg:3310", TransformationFactory.WGS84);
//        simpleAnalyzer.printScopeOfNetwork(network, epsg3310ToWGS84);

//
    }

    /* print unduplicated available modes for each link  */
    public void modeAnalysis(Network network) {
        var allModes = new HashSet<Set<String>>();
        for (Link link : network.getLinks().values()) {
            allModes.add(link.getAllowedModes());
        }
        System.out.println("Sum of available modes combination: " + allModes.size());
        System.out.println(allModes);
    }

    /* return the number of link for each kind */
    public HashMap<Set<String>, Integer> linkCount(Network network) {
        System.out.println("the number of nodes: " + network.getNodes().size());
        System.out.println("the number of links: " + network.getLinks().size());

        var linkCountMap = new HashMap<Set<String>, Integer>();
        for (Link link : network.getLinks().values()) {
            if (!linkCountMap.containsKey(link.getAllowedModes())) {
                linkCountMap.put(link.getAllowedModes(), 1);
            } else {
                linkCountMap.put(link.getAllowedModes(), linkCountMap.get(link.getAllowedModes()) + 1);
            }
        }
        return linkCountMap;
    }

    /* return the number of link for allowedModes */
    public int linkCount4SpecificModes(Network network, Set<String> allowedModes) {
        int num = 0;
        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().equals(allowedModes)) {
                num += 1;
            }
        }
        return num;
    }

    /* print the scope of the nodes */
    public void printScopeOfNetwork(Network network, CoordinateTransformation xxToWGS84) {
//        We set the default value using the first node.
        Iterator<? extends Node> iterator = network.getNodes().values().iterator();
        Node node = iterator.next();
        double xMin = node.getCoord().getX();
        double xMax = node.getCoord().getX();
        double yMin = node.getCoord().getY();
        double yMax = node.getCoord().getY();

//        other nodes
        while (iterator.hasNext()) {
            node = iterator.next();
            if (node.getCoord().getX() < xMin) {
                xMin = node.getCoord().getX();
            }
            if (node.getCoord().getX() > xMax) {
                xMax = node.getCoord().getX();
            }
            if (node.getCoord().getY() < yMin) {
                yMin = node.getCoord().getY();
            }
            if (node.getCoord().getY() > yMax) {
                xMax = node.getCoord().getY();
            }
        }

        Coord minXY = new Coord(xMin, yMin);
        Coord maxXY = new Coord(yMin, yMax);

//        TODO: check the coordination system transformation
        System.out.println("min longitude: " + xxToWGS84.transform(minXY).getX() + ".   min latitude: " + xxToWGS84.transform(minXY).getY());
        System.out.println("max longitude: " + xxToWGS84.transform(maxXY).getX() + ".   max latitude: " + xxToWGS84.transform(maxXY).getY());
    }
}
