package org.matsim.network;


import org.gdal.gdal.Dataset;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.MyScheduleCleaner;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2023年03月20日 16:52
 * @Description: identify indirect failures based on network and direct failures. Here we define indirect failures in road network as unconnected nodes with GCC,
 * * but we define indirect failures in pt network as nodes in the failed transitLine.
 */
public class IdentifyIndirectFailures {
    /* input the original road network and direct failures, output: indirect failures in road network */
    public static Set<Id<Node>> identifyRoadIndirectFailures(Network roadNetwork, Set<Id<Node>> directFailedNodes) {
//        the original set.
        Set<Id<Node>> nodeIdsNoModification = roadNetwork.getNodes().keySet();
        HashSet<Id<Node>> nodeIds = new HashSet<>(nodeIdsNoModification);
//        remove the direct failures.
        for (Id<Node> directFailure : directFailedNodes) {
            roadNetwork.removeNode(directFailure);
        }
//        the giant connected component.
        NetworkCleaner networkCleaner = new NetworkCleaner();
        var nodeGCCIds = networkCleaner.searchBiggestCluster(roadNetwork).keySet();
//        the indirect failures
        nodeIds.removeAll(nodeGCCIds);
        return nodeIds;
    }

    /* input the original pt network and direct failures, output: indirect failures in pt network */
    public static Set<Id<Node>> identifyPtIndirectFailures(Network ptNetwork, Set<Id<Node>> directFailedNodes, TransitSchedule transitSchedule) {
//        the original set
        Set<Id<Node>> nodeIdsNoModification = ptNetwork.getNodes().keySet();
        var nodeIds = new HashSet<>(nodeIdsNoModification);
//        remove the direct failures.
        for (Id<Node> directFailure : directFailedNodes) {
            ptNetwork.removeNode(directFailure);
        }
//        the residual schedule
        MyScheduleCleaner.cleanScheduleWithNetwork(transitSchedule, ptNetwork);
//        the residual functional network
        DeleteUnusedPtLinks.run(ptNetwork, transitSchedule);
//        the indirect failures
        nodeIds.removeAll(ptNetwork.getNodes().keySet());
        return nodeIds;
    }

    public static void main(String[] args) {
//        validation in Hamburg.
//        Network preparation.
        Network network = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\hamburg\\code validation\\identify direct failures and repairs\\input\\hamburg-v3.0-network-with-pt.xml.gz");
        TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule("D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\hamburg\\code validation\\identify direct failures and repairs\\input\\hamburg-v3.0-transitSchedule.xml.gz");
        Network roadNet = SeparateRoadAndSubway.run4RoadNet(network);
        Network ptNet = SeparateRoadAndSubway.run4MetroNet(network); // the pt network of hamburg is just like metro network.
//        Todo: make sure the correct flood map. the global flood map
        String floodMapPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】洪水数据\\Joint research centre data\\floodMapGL_rp100y\\floodMapGL_rp100y.tif";
//        TODO: make sure the correct coord transformation system.
        CoordinateTransformation epsg25832To4326 = TransformationFactory.getCoordinateTransformation("epsg:25832", TransformationFactory.WGS84);
        Dataset dataset = NetworkAttackFlood.readTiff(floodMapPath);
        String outNetworkAfterDirect = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\hamburg\\code validation\\identify direct failures and repairs\\output\\networkAfterDirectFailures.xml.gz";
        String outNetworkAfterPt= "D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\hamburg\\code validation\\identify direct failures and repairs\\output\\networkAfterPtFailures.xml.gz";

//        Identify direct failures for road network.
        double threshold = 0.0;  // 0.3 m is more likely. Additional, set different inundation threshold for road network and pt network?
        Set<Id<Node>> directFailedNodes4Road = new HashSet<>();
        Set<Id<Link>> directFailedLinks4Road = new HashSet<>();
        for (Node node : roadNet.getNodes().values()) {
            // For hamburg.
            Coord coordWGS84 = epsg25832To4326.transform(node.getCoord());
            if (NetworkAttackFlood.getPixelValue(coordWGS84, dataset) > threshold) {
                directFailedNodes4Road.add(node.getId());
                for (Link link : node.getInLinks().values()) {
                    directFailedLinks4Road.add(link.getId());
                }
                for (Link link : node.getInLinks().values()) {
                    directFailedLinks4Road.add(link.getId());
                }
            }
        }
//        Identify the indirect failures for road network.
        Set<Id<Node>> indirectFailedNodes4Road = identifyRoadIndirectFailures(roadNet, directFailedNodes4Road);
        Set<Id<Link>> indirectFailedLinks4Road = new HashSet<>();
        for (Id<Node> nodeId : indirectFailedNodes4Road) {
            Node node = network.getNodes().get(nodeId);
            for (Link link : node.getInLinks().values()) {
                indirectFailedLinks4Road.add(link.getId());
            }
            for (Link link : node.getOutLinks().values()) {
                indirectFailedLinks4Road.add(link.getId());
            }
        }
//        Identify the direct failures for pt network.
        Set<Id<Node>> directFailedNodes4Pt = new HashSet<>();
        Set<Id<Link>> directFailedLinks4Pt = new HashSet<>();
        for (Node node : ptNet.getNodes().values()) {
            // For hamburg.
            Coord coordWGS84 = epsg25832To4326.transform(node.getCoord());
            if (NetworkAttackFlood.getPixelValue(coordWGS84, dataset) > threshold) {
                directFailedNodes4Pt.add(node.getId());
                for (Link link : node.getInLinks().values()) {
                    directFailedLinks4Pt.add(link.getId());
                }
                for (Link link : node.getInLinks().values()) {
                    directFailedLinks4Pt.add(link.getId());
                }
            }
        }
//        Identify the indirect failures for pt network.
        Set<Id<Node>> indirectFailedNodes4Pt = identifyPtIndirectFailures(ptNet, directFailedNodes4Pt, transitSchedule);
        Set<Id<Link>> indirectFailedLinks4Pt = new HashSet<>();
        for (Id<Node> nodeId : indirectFailedNodes4Pt) {
            Node node = network.getNodes().get(nodeId);
            for (Link link : node.getInLinks().values()) {
                indirectFailedLinks4Pt.add(link.getId());
            }
            for (Link link : node.getOutLinks().values()) {
                indirectFailedLinks4Pt.add(link.getId());
            }
        }

//        delete direct failures.
        for (Id<Node> nodeId : directFailedNodes4Road) {
            network.removeNode(nodeId);
        }
        for (Id<Link> linkId : directFailedLinks4Road) {
            network.removeLink(linkId);
        }
        for (Id<Node> nodeId : directFailedNodes4Pt) {
            network.removeNode(nodeId);
        }
        for (Id<Link> linkId : directFailedLinks4Pt) {
            network.removeLink(linkId);
        }
        NetworkUtils.writeNetwork(network, outNetworkAfterDirect);
//        delete indirect failures.
        for (Id<Node> nodeId : indirectFailedNodes4Road) {
            network.removeNode(nodeId);
        }
        for (Id<Link> linkId : indirectFailedLinks4Road) {
            network.removeLink(linkId);
        }
        for (Id<Node> nodeId : indirectFailedNodes4Pt) {
            network.removeNode(nodeId);
        }
        for (Id<Link> linkId : indirectFailedLinks4Pt) {
            network.removeLink(linkId);
        }
        NetworkUtils.writeNetwork(network, outNetworkAfterPt);
    }
}
