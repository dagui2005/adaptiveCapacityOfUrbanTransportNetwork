package org.matsim.network;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.gis.matsim2esri.network.Links2ESRIShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2022年03月24日 11:31
 * @Description: 将网络文件由 XML 格式转换为 Shapefile 格式，便于 geopandas 和 osmnx 处理。本程序的主要使用内置类 "Links2ESRIShape"，但导出的 shapefile 没有 modes，因此可以使用 "Network2Shapefiler"。
 * 输出的包括 links 和 Polygons 两种 shapefiles
 */
public class Network2Shapefile {
    //    输入的耦合网络 XML 文件路径
    private static String inputFilePath = "scenarios/nanjingMultiModal_0315/network.xml";
    //    道路网络 XML 文件路径
    private static String roadNetPath = "data/decouplingNetworks/roadNetwork";
    //    轨道网络 XML 文件路径
    private static String raiNetPath = "data/decouplingNetworks/railNetwork";
    //    地面公交网络 XML 文件路径
    private static String busNetPath = "data/decouplingNetworks/busNetwork";
    //    输入的 xml 文件的坐标系统，同时也是输出的 shapefile 文件的坐标系统
    private static String defaultCRS = "EPSG:32650";

    //    输出的耦合网络 shapefile 文件路径 outputAsLines.shp -----> 道路路段用 lines 表示
    private static String outputCouNetsPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\coupled Networks\\Lines\\lines_epsg32650.shp";
    //    输出的耦合网络 shapefile 文件路径 outputAsPolygons.shp  -------> 道路路段用 polygons 表示，路段通过自由流速度，车道数，通行能力推算
    private static String outputCouNetsPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\coupled Networks\\Polygons\\Polygons_epsg32650.shp";

    //    输出的轨道网络 shapefile 文件路径 outputAsLines.shp
    private static String outputRailNetsPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\metro Network\\Lines\\lines_epsg32650.shp";
    //    输出的轨道网络 shapefile 文件路径 outputAsPolygons.shp
    private static String outputRailNetsPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\metro Network\\Polygons\\Polygons_epsg32650.shp";

    //    输出的bus网络 shapefile 文件路径 outputAsLines.shp
    private static String outputBusNetsPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\bus Network\\Lines\\lines_epsg32650.shp";
    //    输出的bus网络 shapefile 文件路径 outputAsPolygons.shp
    private static String outputBusNetsPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\bus Network\\Polygons\\Polygons_epsg32650.shp";

    //    输出的道路网络 shapefile 文件路径 outputAsLines.shp
    private static String outputRoadNetsPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\road Network\\Lines\\lines_epsg32650.shp";
    //    输出的道路网络 shapefile 文件路径 outputAsPolygons.shp
    private static String outputRoadNetsPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】路网数据\\road Network\\Polygons\\Polygons_epsg32650.shp";


    //    解耦双层级多模式交通网络。 coupledNets ---> ArrayList<Network>(railNet, busNet, roadNet)
    public static ArrayList<Network> decoupNet(Network coupledNet) {
//        subNetworks and their scenarios
        Scenario railSce = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario busSce = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario roadSce = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        for (Link link : coupledNet.getLinks().values()) {
            Set<String> allowedModes = link.getAllowedModes();
//            Note: the link belonging to metro can't be the element of bus network.
            if (allowedModes.contains("pt")) {
//                add elements to rail network
                railSce.getNetwork().addNode(link.getFromNode());
                railSce.getNetwork().addNode(link.getToNode());
                railSce.getNetwork().addLink(link);
            } else if (allowedModes.contains("bus")) {
//                add elements to bus network
                busSce.getNetwork().addNode(link.getFromNode());
                busSce.getNetwork().addNode(link.getToNode());
                busSce.getNetwork().addLink(link);
            }
//            Note: But, the link belonging to bus network may be the element of road network.
            if (allowedModes.contains("car")) {
//                add elements to road network
                roadSce.getNetwork().addNode(link.getFromNode());
                roadSce.getNetwork().addNode(link.getToNode());
                roadSce.getNetwork().addLink(link);
            }
        }

        return new ArrayList<>(Arrays.asList(railSce.getNetwork(), busSce.getNetwork(), roadSce.getNetwork()));
    }

    public static void main(String[] args) {
//        first step: decoupling the multilayer interconnected networks
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(inputFilePath);
        ArrayList<Network> subNets = decoupNet(scenario.getNetwork());

//        second step: write subnetworks to folder "decouplingNetworks" separately
        new NetworkWriter(subNets.get(0)).write(raiNetPath);
        new NetworkWriter(subNets.get(1)).write(busNetPath);
        new NetworkWriter(subNets.get(2)).write(roadNetPath);

//        third step: transfer XML files to Shapefile.   coupled networks, rail network, bus network, road network
        Links2ESRIShape.main(new String[]{inputFilePath, outputCouNetsPath1, outputCouNetsPath2, defaultCRS});
        Links2ESRIShape.main(new String[]{raiNetPath, outputRailNetsPath1, outputRailNetsPath2, defaultCRS});
        Links2ESRIShape.main(new String[]{busNetPath, outputBusNetsPath1, outputBusNetsPath2, defaultCRS});
        Links2ESRIShape.main(new String[]{roadNetPath, outputRoadNetsPath1, outputRoadNetsPath2, defaultCRS});
    }

}
