package org.matsim.network;

/* 2021.3.13, Chunhong Li
 * 将Osm文件转为network.xml，同时转换坐标
 * */

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.NetworkSimplifier;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.io.OsmNetworkReader;

import java.io.UncheckedIOException;

/*
* "Osm2Network" is aimed to getting a network from OSM with coordination transformation.
* But the modes of the output network's links are only cars.
*
* */

public class Osm2NetworkUtil {

    public static void main(String[] args) {

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.loadScenario(config);
        CoordinateTransformation coordinateTransformation = new WGS2Mercator();
        Network network = scenario.getNetwork();
        OsmNetworkReader osmNetworkReader = new OsmNetworkReader(network, coordinateTransformation);

        String dir = "D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市路网\\";
        String inputFile = "郑州市路网.osm";
        String outputFile = "networkZhengV1.xml";

        try {
            osmNetworkReader.parse(dir + inputFile);    // 导入网络

//          The network can be simplified for improving the simulation performance.
            new NetworkSimplifier().run(network);

//          Ensures that each link in the network can be reached by any other link. So are nodes.
            new NetworkCleaner().run(network);

            new NetworkWriter(network).write(dir + outputFile);  // 导出网络
            System.out.println("执行完毕");
            System.out.println("Please find network file in " + dir + outputFile);

        } catch (UncheckedIOException e) {
            e.toString();
        }

    }
}
