package org.matsim.analysis.network;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.network.SeparateCarAndPt;

/**
 * @author: Chunhong li
 * @date: 2023年02月05日 10:32
 * @Description:
 */
public class ConnectivityTest {
    public static void main(String[] args) {
        Network network = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold3.0m (distinct threshold).s.xml");
//        Network roadNetwork = SeparateRoadAndSubway.run4RoadNet(network);  // allowed modes don't contain "pt".
//        Network ptNetwork = SeparateRoadAndSubway.run4MetroNet(network); // allowed modes contain "pt" and not "car".
//        System.out.println("Before network clean, the number of links for road network = " + roadNetwork.getLinks().size());
//        System.out.println("Before network clean, the number of links for pt network = " + ptNetwork.getLinks().size());
//        NetworkCleaner networkCleaner = new NetworkCleaner();
//        networkCleaner.run(roadNetwork);
//        networkCleaner.run(ptNetwork);
//        System.out.println("After network clean, the number of links for road network = " + roadNetwork.getLinks().size());
//        System.out.println("After network clean, the number of links for pt network = " + ptNetwork.getLinks().size());

        Network carNetwork = SeparateCarAndPt.run(network);
        System.out.println("Before network clean, the number of links for car network = " + carNetwork.getLinks().size());
        NetworkCleaner networkCleaner = new NetworkCleaner();
        networkCleaner.run(carNetwork);
        System.out.println("After network clean, the number of links for car network = " + carNetwork.getLinks().size());


    }
}
