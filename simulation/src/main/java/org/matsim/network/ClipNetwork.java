package org.matsim.network;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.HashSet;

/**
 * @author: Chunhong li
 * @date: 2023年03月08日 14:42
 * @Description: clip the road network based on the rectangular scope or shp file (We will add the function later).
 */
public class ClipNetwork {

    public static void clipBasedOnRectScope(Network network, double minX, double minY, double maxX, double maxY) {
        System.out.println("Before clipping, link num: " + network.getLinks().size() + ", node num: " + network.getNodes().size());
        System.out.println("Clipping scope: minX " + minX + ", minY " + minY + ", maxX " + maxX + ", maxY " + maxY);
        HashSet<Node> nodesBeyondScope = new HashSet<>();
        for (Node node : network.getNodes().values()) {
            if (node.getCoord().getX() < minX) {
                nodesBeyondScope.add(node);
                continue;
            }
            if (node.getCoord().getX() > maxX) {
                nodesBeyondScope.add(node);
                continue;
            }
            if (node.getCoord().getY() < minY) {
                nodesBeyondScope.add(node);
                continue;
            }
            if (node.getCoord().getY() > maxY) {
                nodesBeyondScope.add(node);
            }
        }

        for (Node node : nodesBeyondScope) {
//            TODO: 20230308 We only clip the road network and save the pt network for LA scenario. Reason can be found in document 20230308.
//            identify the node is road node or pt node. If one inLink or outLink has mode "pt", we consider it as pt link.
            boolean isPt = false;
//            TODO: 20230310. Wang thinks I should clip the pt network together.
            for (Link link : node.getInLinks().values()) {
                if (link.getAllowedModes().contains("pt")) {
                    isPt = true;
                    break;
                }
            }
            for (Link link : node.getOutLinks().values()) {
                if (link.getAllowedModes().contains("pt")) {
                    isPt = true;
                    break;
                }
            }

//            if (!isPt) {
            network.removeNode(node.getId());

            for (Id<Link> linkId : node.getInLinks().keySet()) {
                network.removeLink(linkId);
            }
            for (Id<Link> linkId : node.getOutLinks().keySet()) {
                network.removeLink(linkId);
            }
//            }
        }
        System.out.println("After clipping, link num: " + network.getLinks().size() + ", node num: " + network.getNodes().size());
    }

    public static void main(String[] args) {
//        Network to be clipped.
        Network inputNetwork = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\los-angeles-v1.0-network_2019-12-10.xml.gz");
        String outputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-los-angeles-v1.0-network_2019-12-10.xml.gz";
//        Clip scope. (LA flood map)
        Coord minLonLat = new Coord(-118.69353628, 33.70592779);
        Coord maxLonLat = new Coord(-117.64637248, 34.41048562);
//        TODO: Los Angeles:3310
        CoordinateTransformation wgs84ToEpsg3310 = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "epsg:3310");
        Coord minXY = wgs84ToEpsg3310.transform(minLonLat);
        Coord maxXY = wgs84ToEpsg3310.transform(maxLonLat);
//        Clip the network
        clipBasedOnRectScope(inputNetwork, minXY.getX(), minXY.getY(), maxXY.getX(), maxXY.getY());

//        clean the unconnected nodes for road network and pt network separately.
        Network roadNetwork = SeparateRoadAndSubway.run4RoadNet(inputNetwork);  // allowed modes don't contain "pt". .// NOTE: This is effective for hamburg scenario and nanjing scenario.
        Network ptNetwork = SeparateRoadAndSubway.run4MetroNet(inputNetwork); // allowed modes contain "pt" and not "car".
        NetworkCleaner networkCleaner = new NetworkCleaner();
        networkCleaner.run(roadNetwork);
//            networkCleaner.run(ptNetwork);   // Todo: We don't clean the pt network for open-source scenarios because the pt network in baseline scenarios is not connected. Maybe we take the pt lines failure as the indirect failures.
        // add the metro network to the road network.
        if (ptNetwork.getLinks().size() != 0) {
            for (Link metroLink : ptNetwork.getLinks().values()) {
                roadNetwork.addNode(metroLink.getFromNode());
                roadNetwork.addNode(metroLink.getToNode());
                roadNetwork.addLink(metroLink);
            }
        }

        System.out.println("After deleting unconnected nodes, link num: " + roadNetwork.getLinks().size() + ", node num: " + roadNetwork.getNodes().size());

//        Export
        new NetworkWriter(roadNetwork).write(outputNetPath);

    }
}
