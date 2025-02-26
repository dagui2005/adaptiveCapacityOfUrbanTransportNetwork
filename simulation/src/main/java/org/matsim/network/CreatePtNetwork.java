package org.matsim.network;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2023年02月21日 16:18
 * @Description: Creating a pt network depending on a transitSchedule.xml. StopFacilities are considered as nodes, and the line in two stops are considered as links.
 */
public class CreatePtNetwork {
    public static Network run(TransitSchedule transitSchedule) {
        Network network = NetworkUtils.createNetwork();
        NetworkFactory networkFactory = network.getFactory();

        int k = 0;
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
//                identify nodes and links in this transitRoute
                ArrayList<Node> nodes = new ArrayList<>();
                ArrayList<Link> links = new ArrayList<>();
                for (TransitRouteStop stop : transitRoute.getStops()) {
                    // consider each stop as a node.
                    Id<Node> nodeId = Id.createNodeId(stop.getStopFacility().getId().toString());
                    Node thisNode = networkFactory.createNode(nodeId, stop.getStopFacility().getCoord());
                    nodes.add(thisNode);
                }
                for (int i = 1; i < nodes.size(); i++) {
                    // consider the line between two sequential node as a link.
                    // two sequential stops may exist in multiplex transitRoutes. But we want all. So we make all linkId different.
                    String linkId = transitRoute.getId().toString() + "-" + nodes.get(i - 1).getId().toString() + "-" + nodes.get(i).getId().toString();
                    Link thisLink = networkFactory.createLink(Id.createLinkId(linkId), nodes.get(i - 1), nodes.get(i));
                    Set<String> allowedModes = new HashSet<>();
                    allowedModes.add("pt");
                    thisLink.setAllowedModes(allowedModes);
                    links.add(thisLink);
                    k += 1;
                }

//                add nodes and links (not duplicated)
                for (Node node : nodes) {
                    // One stopFacility may exist in multiplex transitRoutes. We want only one.
                    if (!network.getNodes().containsKey(node.getId())) {
                        network.addNode(node);
                    }
                }
                for (Link link : links) {
                    // two sequential stops may exist in multiplex transitRoutes. But we want all.
                    network.addLink(link);
                }
            }
        }
        return network;
    }

    public static void main(String[] args) {
//        For nanjing baseline schedule.
//        String transitSchedulePath = "src/main/resources/nanjing/transitSchedule.xml";
//        String outputSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjingBasePtNetwork.xml";
//        TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule(transitSchedulePath);
//        Network network = run(transitSchedule);
//        new NetworkWriter(network).write(outputSchedulePath);
//        For nanjing flooding schedule.
        double[] threshold_list = new double[]{5.2, 5.4, 5.6, 5.8};
        for (double threshold : threshold_list) {
            String transitSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after floods damages\\xml\\residual transit schedule.rp100.thereshold" + threshold + "m.xml";
            String outputSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-pt network-operational failure-threshold" + threshold + "m.xml.gz";
            TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule(transitSchedulePath);
            Network network = run(transitSchedule);
            new NetworkWriter(network).write(outputSchedulePath);
        }
    }
}
