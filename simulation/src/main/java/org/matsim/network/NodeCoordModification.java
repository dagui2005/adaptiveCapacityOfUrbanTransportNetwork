package org.matsim.network;

/*
* @author: Chunhong Li'21.1110
* @Description:
* 1. network.xml 文件中的坐标系统 from Web Mercator to UTM zone 50 N. 对应的 EPSG from 3857 to 32650.
* 2. transitSchedule.xml 文件中的坐标系统 from Web Mercator to UTM zone 50 N. 对应的 EPSG from 3857 to 32650.
*
*
* */

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV1;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class NodeCoordModification {

//    UTM 以WGS84坐标系为基准的投影坐标系，单位 m，存在适用范围
    public static String UTM50NAsEPSG = "EPSG:32650";
//  Web Mercator 投影坐标系，以 WGS84 坐标系为基准，单位 m，除极高纬度地区外，其余地区均适用
    public static String WebMercator = "EPSG:3857";

//    读取 network.xml 文件路径
    private static String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】交通仿真\\坐标系修正_发车间隔修正_1110\\coupled_network.xml";
//    写入 network.xml 文件路径
    private static String modifiedNetworkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】交通仿真\\坐标系修正_发车间隔修正_1110\\coupled_network_modification.xml";

//    读取 schedule.xml 文件路径
    private static String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】交通仿真\\坐标系修正_发车间隔修正_路段修正_费用函数修正_1115\\modifiedTransitSchedule.xml";

//    写入 schedule.xml 文件路径
    private static String modifiedSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】交通仿真\\坐标系修正_发车间隔修正_路段修正_费用函数修正_1115\\modifiedTransitSchedule2.xml";

    public static void main(String[] args){
//        坐标系 from Web Mercator to UTM Zone 50 N.
        CoordinateTransformation webMercator2Utm50N = TransformationFactory.getCoordinateTransformation(WebMercator,UTM50NAsEPSG);

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.loadScenario(config);

//        1. 网络文件的坐标已经修改，此处不再处理
//        Network network = scenario.getNetwork();

//        new MatsimNetworkReader(network).readFile(networkPath);
//        for(Node node:network.getNodes().values()){
//            Coord coordWebMercator = node.getCoord();
//            Coord coordUtm50N = webMercator2Utm50N.transform(coordWebMercator);
//            node.setCoord(coordUtm50N);
//        }
//
//        new NetworkWriter(network).write(modifiedNetworkPath);

//        2. schedule文件的坐标
        new TransitScheduleReader(scenario).readFile(schedulePath);
        TransitSchedule transitSchedule = scenario.getTransitSchedule();
        for(TransitStopFacility transitStopFacility:transitSchedule.getFacilities().values()){
            Coord coordWebMercator = transitStopFacility.getCoord();
            Coord coordUtm50N = webMercator2Utm50N.transform(coordWebMercator);
            transitStopFacility.setCoord(coordUtm50N);
        }

        new TransitScheduleWriterV1(transitSchedule).write(modifiedSchedulePath);
    }
}
