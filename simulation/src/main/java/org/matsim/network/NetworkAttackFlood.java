package org.matsim.network;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author: Chunhong li
 * @date: 2023年02月02日 10:48
 * @Description: Identify the network damages induced by floods. The flood map is saved as the tif format.
 */
public class NetworkAttackFlood {
    public static void main(String[] args) {
//        Todo: make sure the correct flood map. the global flood map
        String floodMapPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】洪水数据\\Joint research centre data\\floodMapGL_rp100y\\floodMapGL_rp100y.tif";
//        flood map for Los Angeles. Data comes from the paper named as "Large and inequitable flood risks in Los Angeles, California". Work document 20230228
//        String floodMapPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】洪水数据\\los angeles flood data\\flood data\\my los angeles flood map\\tif\\flood map 50th.tif";

//        Todo: change the input network. hamburg input network
//        String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-v3.0-network-with-pt.xml.gz";
//        nanjing input network
        String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\coupled network.xml";
//        Los Angeles input network
//        String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-los-angeles-v1.0-network_2019-12-10.xml.gz";

//        Nanjing scenario is employed to test the effectiveness of this code. Remember change the coordination transformation.
//        String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\无洪水\\output01_demand.noex20.0.27.100pct\\output_network.xml.gz";
//        String outputNetworkPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\【分析】结构破坏相关分析\\XML\\nanjing-test-direct failure 2.xml";
//        String outputNetworkPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\【分析】结构破坏相关分析\\XML\\nanjing-test-indirect failure 2.xml";

//        1. read the flood map and network.
        Dataset dataset = readTiff(floodMapPath);

//        double threshold = 0.001;
//        while (threshold < 0.01)
        double[] thresholds = new double[]{5.2, 5.4, 5.6, 5.8};
        for (double threshold : thresholds) {
            // NOTE: network will be changed after each iteration! Remember import the network again!!
            Network network = NetworkUtils.readNetwork(networkPath);
            System.out.println("The number of nodes: " + network.getNodes().size());
            System.out.println("The number of links: " + network.getLinks().size());

            // Todo: output for hamburg scenarios.
//            String outputNetworkPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-direct failure-threshold" + threshold + "m.xml.gz";
//            String outputNetworkPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-indirect failure-threshold" + threshold + "m.xml.gz";

            // output for nanjing scenarios
            String outputNetworkPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-network-direct failure-threshold" + threshold + "m.xml.gz";
            String outputNetworkPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-network-indirect failure-threshold" + threshold + "m.xml.gz";
            // Todo for Nanjing. If the input network is the coupled network (2,905kb), we can export the residual file (after indirect failures) to the xml20230221 folder (file after indirect failure for plot) and xml folder (Watch out: This file must be added the artificial links).
            String outputNetworkPath3 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m.xml";

            // output for Los Angeles scenarios
//            String outputNetworkPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-direct failure-threshold" + threshold + "m.xml.gz";
//            String outputNetworkPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-indirect failure-threshold" + threshold + "m.xml.gz";

//        2. read the water depth for each node! 20220202. This is a little different from the Nanjing scenario.
            boolean isFlooded;
            ArrayList<Id<Node>> floodedNodeIds = new ArrayList<>();
            ArrayList<Id<Link>> floodedLinkIds = new ArrayList<>();
            for (Node node : network.getNodes().values()) {
                isFlooded = false;
                // TODO: Different scenario has different projection! So you should change the coordinateTransformation.
                // For hamburg.
//                CoordinateTransformation epsg25832To4326 = TransformationFactory.getCoordinateTransformation("epsg:25832", TransformationFactory.WGS84);
//                Coord coordWGS84 = epsg25832To4326.transform(node.getCoord());
                // For nanjing.
            CoordinateTransformation epsg32650To4326 = TransformationFactory.getCoordinateTransformation("epsg:32650", TransformationFactory.WGS84);
            Coord coordWGS84 = epsg32650To4326.transform(node.getCoord());
//            System.out.println(coordWGS84);
//            System.out.println(getPixelValue(coordWGS84, dataset));
                // For Los Angeles.
//                CoordinateTransformation epsg3310ToWGS84 = TransformationFactory.getCoordinateTransformation("epsg:3310", TransformationFactory.WGS84);
//                Coord coordWGS84 = epsg3310ToWGS84.transform(node.getCoord());
                if (getPixelValue(coordWGS84, dataset) > threshold) {
                    floodedNodeIds.add(node.getId());
                    for (Link link : node.getInLinks().values()) {
                        floodedLinkIds.add(link.getId());
                    }
                    for (Link link : node.getOutLinks().values()) {
                        floodedLinkIds.add(link.getId());
                    }
                }
            }

//        3. remove flooded nodes and links.
            for (Id<Node> nodeId : floodedNodeIds) {
                network.removeNode(nodeId);
            }
            for (Id<Link> linkId : floodedLinkIds) {
                network.removeLink(linkId);
            }
            new NetworkWriter(network).writeFileV2(outputNetworkPath1);
            System.out.println("The number of nodes after direct failures: " + network.getNodes().size());
            System.out.println("The number of links after direct failures: " + network.getLinks().size());

//        4. clean the unconnected nodes for road network and pt network separately.
            Network roadNetwork = SeparateRoadAndSubway.run4RoadNet(network);  // allowed modes don't contain "pt". .// NOTE: This is effective for hamburg scenario and nanjing scenario.
            Network ptNetwork = SeparateRoadAndSubway.run4MetroNet(network); // allowed modes contain "pt" and not "car".
            NetworkCleaner networkCleaner = new NetworkCleaner();
            networkCleaner.run(roadNetwork);
//            networkCleaner.run(ptNetwork);   // Todo: We don't clean the pt network for open-source scenarios because the pt network in baseline scenarios is not connected. Maybe we take the pt lines failure as the indirect failures.
            // add the metro network to the road network.
            if (ptNetwork.getLinks().size() != 0) {
                for (Link metroLink : ptNetwork.getLinks().values()) {
                    roadNetwork.addNode(metroLink.getFromNode());
                    roadNetwork.addNode(metroLink.getToNode());
                    roadNetwork.addLink(metroLink);
                }
            }

            new NetworkWriter(roadNetwork).writeFileV2(outputNetworkPath2);
            // if outputNetworkPath3 exists.
            try {
                new NetworkWriter(roadNetwork).writeFileV2(outputNetworkPath3);
            } catch (Exception e){
                System.out.println(e);
            }

            System.out.println("The number of nodes after indirect failures: " + roadNetwork.getNodes().size());
            System.out.println("The number of links after indirect failures: " + roadNetwork.getLinks().size());

            threshold += 0.001;
        }
        // 释放资源
        dataset.delete();
    }

    /* Function: read the tiff. */
    public static Dataset readTiff(String tifPath) {
        //注册
        gdal.AllRegister();
        //打开文件获取数据集
        Dataset dataset = gdal.Open(tifPath,
                gdalconstConstants.GA_ReadOnly);
        if (dataset == null) {
            System.out.println("打开" + tifPath + "失败" + gdal.GetLastErrorMsg());
            System.exit(1);
        }
        //获取驱动
        Driver driver = dataset.GetDriver();
        //获取驱动信息
        System.out.println("driver long name: " + driver.getLongName());
        int colCount = dataset.getRasterXSize();   // 栅格矩阵的列数
        int rowCount = dataset.getRasterYSize();   // 栅格矩阵的行数
        int bandCount = dataset.getRasterCount();    // 栅格矩阵的波段数
        System.out.println("Col count = " + colCount + ". Row count = " + rowCount + ". RasterCount: " + bandCount);
        // 仿射矩阵，左上角像素的大地坐标和像素分辨率。
        // 共有六个参数，分表代表左上角x坐标；东西方向上图像的分辨率；如果北边朝上，地图的旋转角度，0表示图像的行与x轴平行；左上角y坐标；
        // 如果北边朝上，地图的旋转角度，0表示图像的列与y轴平行；南北方向上地图的分辨率。
        double[] gt = new double[6];
        dataset.GetGeoTransform(gt);
        System.out.println("仿射变换参数" + Arrays.toString(gt));

        return dataset;
    }

    /* Function: get the value from its coordination */
    public static Double getPixelValue(Coord coord, Dataset dataset) {
        // 仿射矩阵，左上角像素的大地坐标和像素分辨率。
        double[] gt = new double[6];
        dataset.GetGeoTransform(gt);
        // 经纬度转换为栅格像素坐标
        int xOff = (int) ((coord.getX() - gt[0]) / gt[1]);  // x pixel  点对应的栅格与左上角的栅格在x轴上相距的栅格数（因为左上角的栅格是(0,0)，所以也是栅格序号） = 东西方向（经度方向/X轴方向）的距离 / 该方向的图像分辨率
        int yOff = (int) ((coord.getY() - gt[3]) / gt[5]); // y pixel  点对应的栅格与左上角的栅格在y轴上相距的栅格数（因为左上角的栅格是(0,0)，所以也是栅格序号） = 南北方向（纬度方向/y轴方向）的距离 / 该方向的图像分辨率
        Band band = dataset.GetRasterBand(0 + 1);  // # 读取一个波段，其参数为波段的索引号，波段索引号从1开始(汪老师和欧洲的洪水数据图形都只有一个波段)
        double[] values = new double[1];
        band.ReadRaster(xOff, yOff, 1, 1, values); // Todo

        return values[0];
    }
}
