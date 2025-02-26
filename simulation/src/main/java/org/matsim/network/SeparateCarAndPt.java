package org.matsim.network;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;

/**
 * @author: Chunhong li
 * @date: 2022年05月05日 15:05
 * @Description: 将耦合的car-pt 网络解耦为只有 car 的网络，即把 modes 不包括 car 的都删除，包括 car 的 modes 剔除无关 modes
 */
public class SeparateCarAndPt {
    public static Network run(Network coupledNet) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Network carNet = scenario.getNetwork();

        for (Link link : coupledNet.getLinks().values()
        ) {
            if (link.getAllowedModes().contains("car")) {
                carNet.addNode(link.getFromNode());
                carNet.addNode(link.getToNode());
                link.setAllowedModes(CollectionUtils.stringToSet("car"));
                carNet.addLink(link);
            }
        }

        return carNet;
    }

    public static void main(String[] args) {
//        input network path
//        String coupledNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\coupled Network\\xml\\";      // 无洪水的耦合网络文件路径
        String coupledNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\";      // 有洪水的耦合网络文件路径
//        output network path
//        String carNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\单模式-无洪水\\network\\";      // 无洪水的小汽车网络文件路径
        String carNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\单模式-有洪水\\network\\";         // 有洪水的小汽车网络文件路径

        double threshold = 0.0;
        while (threshold <= 12) {
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new MatsimNetworkReader(scenario.getNetwork()).readFile(coupledNetPath + "residual coupled network.rp100.thereshold" + threshold + "m.s.xml");
            Network carNet = run(scenario.getNetwork());
            new NetworkWriter(carNet).writeFileV2(carNetPath + "residual.car network.threshold" + threshold + "m.xml");
            threshold += 0.5;
        }
    }
}
