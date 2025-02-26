package org.matsim.network;

import org.locationtech.jts.geom.Coordinate;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PolylineFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author: Chunhong li
 * @date: 2022年04月6日 11:31
 * @Description: 将网络文件由 XML 格式转换为 Shapefile 格式，便于 geopandas 和 osmnx 处理。本程序的主要通过自己编程 "myLinks2ESRIShape" 实现，参考 matsim-codes-examples。输出的包括 links 和 nodes 两种 shapefiles
 * 包含路段的 modes 信息。
 * 20220410，但是，我发现，这个程序没有把 artificial 路段转过去，考虑到在图像展示的时候也不需要展示 artificial 路段，因此，不转也行，但是当你做运营失效处理的时候（CleanInvalidSchedule.java），就必须考虑添加上 artificial links，否则会造成大量的 bus lines 被误删。
 */
public class Network2Shapefiler {
    //    输入的网络 XML 文件路径
    private String inputNetPath;

    //    输出的网络 shapefile 文件路径， 将生成 nodes 和 links 两份 shapefiles
    private String outputNodeShpPath; // nodes
    private String outputLinkShpPath; // nodes

    //    输入的 xml 文件的坐标系统，同时也是输出的 shapefile 文件的坐标系统
    private String defaultCRS;

    public static void main(String[] args) {
        Network2Shapefiler network2Shapefiler = new Network2Shapefiler();
        // TODO: Nanjing:32650; Hamburg: 25832; Los angeles; 3310
        network2Shapefiler.setDefaultCRS("epsg:32650");
        double[] thresholds = new double[]{5.2, 5.4, 5.6, 5.8};
        for (double threshold : thresholds) {
            //            Todo: flooding scenarios for hamburg scenario
//            network2Shapefiler.setInputNetPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-pt failure-threshold" + threshold + "m.xml.gz");
//            network2Shapefiler.setOutputLinkShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\shp\\link\\hamburg-network-pt failure-threshold" + threshold + "m.shp");
//            network2Shapefiler.setOutputNodeShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\shp\\node\\hamburg-network-pt failure-threshold" + threshold + "m.shp");

//            flooding scenarios f or los angeles scenario.
//            network2Shapefiler.setInputNetPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-indirect failure-threshold" + threshold + "m.xml.gz");
//            network2Shapefiler.setOutputLinkShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\shp\\link\\my2-50th-los angeles-network-indirect failure-threshold" + threshold + "m.shp");
//            network2Shapefiler.setOutputNodeShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\shp\\node\\my2-50th-los angeles-network-indirect failure-threshold" + threshold + "m.shp");

//            flooding scenarios for nanjing scenario
            network2Shapefiler.setInputNetPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-pt network-operational failure-threshold" + threshold + "m.xml.gz");
            network2Shapefiler.setOutputLinkShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\shp20230221\\link\\nanjing-pt network-operational failure-threshold" + threshold + "m.shp");
            network2Shapefiler.setOutputNodeShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\shp20230221\\node\\nanjing-pt network-operational failure-threshold" + threshold + "m.shp");

//            Todo: we need only one iteration for exporting baseline shp.
//            network2Shapefiler.setInputNetPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-los-angeles-v1.0-network_2019-12-10.xml.gz");
//            network2Shapefiler.setOutputLinkShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\shp\\link\\my2-los-angeles-v1.0-network.shp");
//            network2Shapefiler.setOutputNodeShpPath("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\shp\\node\\my2-los-angeles-v1.0-network.shp");

            network2Shapefiler.myLinks2ESRIShape();
        }
    }

    public String getDefaultCRS() {
        return defaultCRS;
    }

    public void setDefaultCRS(String defaultCRS) {
        this.defaultCRS = defaultCRS;
    }

    public String getInputNetPath() {
        return inputNetPath;
    }

    public void setInputNetPath(String inputNetPath) {
        this.inputNetPath = inputNetPath;
    }

    public String getOutputNodeShpPath() {
        return outputNodeShpPath;
    }

    public void setOutputNodeShpPath(String outputNodeShpPath) {
        this.outputNodeShpPath = outputNodeShpPath;
    }

    public String getOutputLinkShpPath() {
        return outputLinkShpPath;
    }

    public void setOutputLinkShpPath(String outputLinkShpPath) {
        this.outputLinkShpPath = outputLinkShpPath;
    }

    public void myLinks2ESRIShape() {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        new MatsimNetworkReader(network).readFile(inputNetPath);

        CoordinateReferenceSystem crs = MGC.getCRS(defaultCRS);    // EPSG Code

        Collection<SimpleFeature> features = new ArrayList<>();
        PolylineFeatureFactory linkFactory = new PolylineFeatureFactory.Builder().
                setCrs(crs).
                setName("link").
                addAttribute("ID", String.class).
                addAttribute("fromID", String.class).
                addAttribute("toID", String.class).
                addAttribute("length", Double.class).
                addAttribute("type", String.class).
                addAttribute("capacity", Double.class).
                addAttribute("freespeed", Double.class).
                addAttribute("modes", String.class).
                addAttribute("lanes", Double.class).
                create();

        for (Link link : network.getLinks().values()) {
            Coordinate fromNodeCoordinate = new Coordinate(link.getFromNode().getCoord().getX(), link.getFromNode().getCoord().getY());
            Coordinate toNodeCoordinate = new Coordinate(link.getToNode().getCoord().getX(), link.getToNode().getCoord().getY());
            Coordinate linkCoordinate = new Coordinate(link.getCoord().getX(), link.getCoord().getY());
            SimpleFeature ft = linkFactory.createPolyline(new Coordinate[]{fromNodeCoordinate, linkCoordinate, toNodeCoordinate},
                    new Object[]{link.getId().toString(), link.getFromNode().getId().toString(), link.getToNode().getId().toString(), link.getLength(), NetworkUtils.getType(link), link.getCapacity(), link.getFreespeed(), link.getAllowedModes(), link.getNumberOfLanes()}, null);
            features.add(ft);
        }
        ShapeFileWriter.writeGeometries(features, outputLinkShpPath);

//        TODO: I don't need plot the node currently.
//        features = new ArrayList<>();
//        PointFeatureFactory nodeFactory = new PointFeatureFactory.Builder().
//                setCrs(crs).
//                setName("nodes").
//                addAttribute("ID", String.class).
//                create();

//        for (Node node : network.getNodes().values()) {
//            SimpleFeature ft = nodeFactory.createPoint(node.getCoord(), new Object[]{node.getId().toString()}, null);
//            features.add(ft);
//        }
//        ShapeFileWriter.writeGeometries(features, outputNodeShpPath);
    }


}
