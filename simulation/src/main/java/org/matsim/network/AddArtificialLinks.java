package org.matsim.network;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

/**
 * @author: Chunhong li
 * @date: 2022年04月10日 16:44
 * @Description: 将一个 MATSim network 的部分路段添加到 另一个 matsim network 文件中。
 * 由 MATSim network.xml 转出的 shapefile 文件不包含 artificial Links，这会导致很多 bus 线路没办法匹配，在处理运营失效时会造成误删，因此本程序添加 artificial links to network.xml，
 */
public class AddArtificialLinks {
    private static final String network0Path = "src/main/resources/nanjing/network.xml";
//    TODO: change the random seed for random damages.
    private static final int mySeed = 9;

    public static void run(double threshold) {
//        runoff ==  0, the original MATSim network containing artificial links.
        Network network0 = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getNetwork();
        new MatsimNetworkReader(network0).readFile(network0Path);
//        runoff > 0, the MATSim networks containing no artificial links.
        Network network = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getNetwork();
//        TODO: Change the file name
//        for flood damages
//        String inputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m.xml";
//        String outputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m.s.xml";
//        for flood damages  (distinct threshold for different modes)
        String inputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m (distinct threshold).xml";
        String outputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m (distinct threshold).s.xml";
//        for random damages
//        String inputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After random damages\\xml\\" + "residual coupled network.randomDamages.thereshold" + threshold +"m.seed" + mySeed +".xml";
//        String outputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After random damages\\xml\\" + "residual coupled network.randomDamages.thereshold" + threshold +"m.seed" + mySeed +".s.xml";

        new MatsimNetworkReader(network).readFile(inputNetPath);
        for (Link link : network0.getLinks().values()
        ) {
            if (link.getAllowedModes().contains("artificial")) {
                if (!network.getNodes().containsKey(link.getFromNode().getId())) {
                    network.addNode(link.getFromNode());
                }
                if (!network.getNodes().containsKey(link.getToNode().getId())) {
                    network.addNode(link.getToNode());
                }
                network.addLink(link);
            }
        }

//        write output MATSim network.
        new NetworkWriter(network).writeFileV2(outputNetPath);
    }

    /* add artificial links to the network */
    public static void run(Network network, List<Link> artificialLinks){
        for (Link artificialLink : artificialLinks) {
            network.addNode(artificialLink.getFromNode());
            network.addNode(artificialLink.getToNode());
            network.addLink(artificialLink);
        }
    }

    public static void main(String[] args) {
//        double[] threshold_list = new double[]{3.0};
        double[] threshold_list = new double[]{3.0, 4.0, 5.0};
        for (double threshold : threshold_list) {
            run(threshold);
        }
    }
}
