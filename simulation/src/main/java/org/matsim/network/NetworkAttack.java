package org.matsim.network;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2022年05月24日 20:56
 * @Description: Class 1. randomAttack: attack the links of network randomly.
 * Class 2. deleteArtificialLinks: delete Artificial links and return those.
 * NOTE 1: Artificial links should be removed before attacks if the imported network is bus mapped network. After attack and network cleaner, artificial links should be added and network cleaner again.
 * NOTE 2: Separate road network and metro network before network cleaner if the imported networks are multiplex networks.
 * NOTE 3: The core problem is the network connections for NetworkCleaner. Multiplex networks and mapped networks need cautions.
 * While MATSim requires that network in one mode is connected other than the whole networks, NetworkCleaner requires the connectivity of whole networks.
 * https://github.com/matsim-org/matsim-code-examples/issues/219
 * showing the network to check the connectivity for each mode using via.
 * NOTE 4: This code use network cleaning function. This requires that a metro station can't be divided by two or more because of transfer lines. So I use python do network attack.
 */
public class NetworkAttack {

    public static void main(String[] args) {
        Network multiplexNets = NetworkUtils.readNetwork("src/main/resources/network.xml");
//        1. separate the road network and the metro network from the multiplex networks
        Network roadNet = SeparateRoadAndSubway.run4RoadNet(multiplexNets);
        Network metroNet = SeparateRoadAndSubway.run4MetroNet(multiplexNets);

////        NOTE: The following codes are random damages considering multiplex networks as a super network.
//        int prop = 1;
//        long seed = 1;
//        while (prop < 74) {
////            2. delete artificial links from the road network.
//            List<Link> artificialLinks = DeleteArtificialLinks.run(roadNet);
////            3. random attacks for the unmapped road network and NetworkCleaner.
//            var netAttack = new NetworkAttack();
//            netAttack.randomAttack(roadNet, (double) prop / (double) 100, seed);   //     除数和被除数都要加　double，可以得到精确解
//            new NetworkCleaner().run(roadNet);
////            4. add artificial links to the road network and NetworkCleaner.
//            AddArtificialLinks.run(roadNet, artificialLinks);
//            new NetworkCleaner().run(roadNet);
////            5. random attacks for the metro network and NetworkCleaner.
//            netAttack.randomAttack(metroNet, (double) prop / (double) 100, seed);  // 除数和被除数都要加　double，可以得到精确解
//            new NetworkCleaner().run(metroNet);
////            6. add the metro network to the road network.
//            if (metroNet.getLinks().size() == 0) {
//                continue;
//            }
//            for (Link metroLink : metroNet.getLinks().values()) {
//                roadNet.addNode(metroLink.getFromNode());
//                roadNet.addNode(metroLink.getToNode());
//                roadNet.addLink(metroLink);
//            }
////            NOTE: the present roadNet is the actually multiplex networks.
//            NetworkUtils.writeNetwork(roadNet, "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\After random damages\\XML.s="+ seed + "\\networkAfterRandomAttack." + prop + ".prop.xml");
//
//            prop += 1;
//        }

//      NOTE: The following codes are random damages considering multiplex networks as separate networks. That is to say, road network and metro network have different disruption proportion.
//        double road_prop = 0.0;   // random disruption proportion for road network:
//        double metro_prop = 0.0;  // random disruption proportion for metro network:
//        double threshold = 15.0;    // 该破坏比例对应的洪水场景的淹没阈值
//        long seed = 10;
////      2. delete artificial links from the road network.
//        List<Link> artificialLinks = DeleteArtificialLinks.run(roadNet);
////      3. random attacks for the unmapped road network and NetworkCleaner.
//        var netAttack = new NetworkAttack();
//        netAttack.randomAttack(roadNet, road_prop, seed);
////        new NetworkCleaner().run(roadNet);
////      4. add artificial links to the road network and NetworkCleaner.
//        AddArtificialLinks.run(roadNet, artificialLinks);
////        Note, don't use network cleaner !!! Because some artificial links are not strongly connected.
////      5. random attacks for the metro network and NetworkCleaner.
//        netAttack.randomAttack(metroNet, metro_prop, seed);
////      6. add the metro network to the road network.
//        if (metroNet.getLinks().size() != 0) {
//            for (Link metroLink : metroNet.getLinks().values()) {
//                roadNet.addNode(metroLink.getFromNode());
//                roadNet.addNode(metroLink.getToNode());
//                roadNet.addLink(metroLink);
//            }
//        }
////      NOTE: the present roadNet is the actually multiplex networks.
//        NetworkUtils.writeNetwork(roadNet, "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\随机破坏\\network\\residual network.random.threshold" + threshold + "m.seed" + seed + ".xml");
    }

    /* attack the links of network randomly in transit links.
     * p: link disruption probability */
    public void randomAttack(Network network, Double p, long seed) {
        Random r = new Random(seed);
//        links to be removed.
        Set<Link> linksAttacked = new HashSet<>();
//        collect links to be removed.
        for (Link link : network.getLinks().values()) {
            if (r.nextDouble() < p) {
                linksAttacked.add(link);
            }
        }

//        delete links
        for (Link link : linksAttacked) {
            network.removeLink(link.getId());
        }

//        delete nodes and links not belonging to the giant component.
        new NetworkCleaner().run(network);
    }
}
