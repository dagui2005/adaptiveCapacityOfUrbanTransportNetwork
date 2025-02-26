package org.matsim.network;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.HashSet;

/**
 * @author: Chunhong li
 * @date: 2023年02月19日 11:29
 * @Description: This code is used to delete pt links and pt nodes not in schedule file.
 * * For open-source scenarios,the indirect failures for car network are not flooded links which not in GCC.
 * * However, the indirect failures for pt network is pt line failures (operational failures). Because the pt network is not GCC in the baseline scenario.
 * So, we want a network.xml whose road network is GCC and pt network is set of the nodes and links used in pt schedule.
 * *
 */
public class DeleteUnusedPtLinks {
    public static void run(Network network, TransitSchedule transitSchedule) {
        // the used pt links in the schedule.
        HashSet<Id<Link>> usedPtLinkIds = new HashSet<>();
        for (TransitStopFacility transitStopFacility : transitSchedule.getFacilities().values()) {
            usedPtLinkIds.add(transitStopFacility.getLinkId());
        }
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                usedPtLinkIds.addAll(transitRoute.getRoute().getLinkIds());
            }
        }

        // identify the unused pt links.
        HashSet<Id<Link>> allPtLinkIds = new HashSet<>();
        for (Link link : network.getLinks().values()) {
//            System.out.println(link.getAllowedModes());
            if (link.getAllowedModes().contains("pt")) {   //NOTE: the return of getAllowedModes() is set<String>!
                allPtLinkIds.add(link.getId());
            }
        }
        allPtLinkIds.removeAll(usedPtLinkIds);

        // delete the unused pt links.
        HashSet<Id<Node>> maybeIsolatedPtNodeIds = new HashSet<>();
        for (Id<Link> unusedPtLinkId : allPtLinkIds) {
            // First extract the fromNode and toNode before your deleting links. Otherwise, you can't find these nodes.
            Node fromNode = network.getLinks().get(unusedPtLinkId).getFromNode();
            Node toNode = network.getLinks().get(unusedPtLinkId).getToNode();
            maybeIsolatedPtNodeIds.add(fromNode.getId());
            maybeIsolatedPtNodeIds.add(toNode.getId());
            network.removeLink(unusedPtLinkId);
        }
        for (Id<Node> ptNodeId : maybeIsolatedPtNodeIds) {   // use NodeId not node! because the node will be changed after deleting some links.
            // If the node has no link, we then delete it.
            Node node = network.getNodes().get(ptNodeId);
            if ((node.getInLinks().size()) == 0 && (node.getOutLinks().size() == 0)) {
                network.removeNode(ptNodeId);
            }
        }
    }

    public static void main(String[] args) {
        double[] thresholds = new double[]{0.003, 0.007, 0.01, 0.02, 0.07};
//        double[] thresholds = new double[]{0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0};
        for (double threshold : thresholds) {
            // TODO: for hamburg scenario.
//            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-indirect failure-threshold" + threshold + "m.xml.gz";
//            String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\schedule\\xml\\hamburg-transitSchedule-threshold" + threshold + "m.xml.gz";
//            String outNetworkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-pt failure-threshold" + threshold + "m.xml.gz";

            // Todo: for nanjing scenario
//            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-pt network-direct failure-threshold" + threshold + "m.xml.gz";
//            String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after floods damages\\xml\\residual transit schedule.rp100.thereshold" + threshold + "m.xml";
//            String outNetworkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-pt network-operational failure-threshold" + threshold + "m.xml.gz";

            // TODO: for los angeles.
            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-indirect failure-threshold" + threshold + "m.xml.gz";
            String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-50th-los-angeles-transitSchedule-threshold" + threshold + "m.xml.gz";
            String outNetworkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-pt failure-threshold" + threshold + "m.xml.gz";

            // TODO: for baseline scenario. Watch out you need one iteration.
//            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-los-angeles-v1.0-network_2019-12-10.xml.gz";
//            String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-los-angeles-v1.0-transitSchedule_2019-12-18.xml.gz";
//            String outNetworkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-los-angeles-v1.0-network_2019-12-10.xml.gz";

            Network network = NetworkUtils.readNetwork(networkPath);
            TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule(schedulePath);
            run(network, transitSchedule);
            new NetworkWriter(network).write(outNetworkPath) ;
        }
    }
}
