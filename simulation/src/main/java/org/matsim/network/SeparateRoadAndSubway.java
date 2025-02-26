package org.matsim.network;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;

/**
 * @author: Chunhong li
 * @date: 2022年05月23日 17:09
 * @Description:
 */
public class SeparateRoadAndSubway {

    public static void main(String[] args) {
        double threshold = 0.0;
        while (threshold <= 15) {
            //        input network path
//        NOTE: when runoff = 1, 2, 3, you should add "s" to the end of file name.
            String coupledNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m.s.xml";
//        output network path
            String roadNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\双模式-有洪水\\network\\residual.road network.threshold" + threshold + "m.s.xml";

            Network coupledNet = NetworkUtils.readNetwork(coupledNetPath);
            var roadNet = run4RoadNet(coupledNet);
            new NetworkWriter(roadNet).writeFileV2(roadNetPath);
            threshold += 0.5;
        }
    }

    public static Network run4RoadNet(Network coupledNet) {
        Network roadNet = NetworkUtils.createNetwork();
        for (Link link : coupledNet.getLinks().values()) {
            if (!link.getAllowedModes().contains("pt")) {
//                bus link 的 mode 是 "bus" for Nanjing
                roadNet.addNode(link.getFromNode());
                roadNet.addNode(link.getToNode());
                roadNet.addLink(link);
            }
        }
        return roadNet;
    }

    public static Network run4MetroNet(Network coupledNet) {
        Network metroNet = NetworkUtils.createNetwork();
        for (Link link : coupledNet.getLinks().values()) {
            if (link.getAllowedModes().contains("pt") && !link.getAllowedModes().contains("car")) {
//                bus link 的 mode 是 "bus" (Nanjing).
                metroNet.addNode(link.getFromNode());
                metroNet.addNode(link.getToNode());
                metroNet.addLink(link);
            }
        }
        return metroNet;
    }
}
