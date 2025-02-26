package org.matsim.analysis.network;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author: Chunhong li
 * @date: 2023年02月04日 15:45
 * @Description: 1. 统计初始场景的各个模式的网络的路段数，洪水场景下直接失效后各个模式的网络路段数，洪水场景下间接失效后的各个模式的路段数
 * 2. 道路网络的间接失效是最大连通子图处理，公交网络（Space L 建模）的间接失效是晕应处理。
 * 3. 结果写出到 excel. 20220204. 应该输出到 txt 文件的！方便又好用
 */
public class FloodDamageAnalysis4Nanjing {
    public static void main(String[] args) {
//        1. create the output file path.
        String writeFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【分析】影响评估\\洪水场景\\【分析】结构破坏相关分析\\linkAnalysis20230221\\network damage for nanjing.csv";
//        A. input coupled network for road link count.
        Network baselineCoupledNet = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\coupled network.xml");
        Set<String> carMode = new HashSet<>(Arrays.asList("car"));
        Set<String> carBusMode = new HashSet<>(Arrays.asList("car", "bus"));

//        B. input (Space L) pt network for pt link count.
        Network baselinePtNet = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjingBasePtNetwork.xml");
        SimpleAnalyzer networkAnalyzer = new SimpleAnalyzer();
        Set<String> ptMode = new HashSet<>(Arrays.asList("pt"));
//        2. create a bufferWriter.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(writeFilePath));
            int baseRoadLinkNum = networkAnalyzer.linkCount4SpecificModes(baselineCoupledNet, carMode) + networkAnalyzer.linkCount4SpecificModes(baselineCoupledNet, carBusMode);
            int basePtLinkNum = networkAnalyzer.linkCount4SpecificModes(baselinePtNet, ptMode);

//            2.1 the first line of csv.
            writer.write("threshold (m),");  // col name
            var otherColName = new ArrayList<Set<String>>();
            writer.write("road link after direct" + ",");   // col name
            writer.write("pt link after direct" + ",");   // col name
            writer.write("road link after indirect" + ",");   // col name
            writer.write("pt link after indirect" + ",");   // col name
            writer.newLine();   //  change the new line.

//            2.2 the following line. for flooding scenario.
            double threshold = 0.0;
            while (threshold <= 15) {
                writer.write(threshold + ",");
                Network floodedRoadDirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-network-direct failure-threshold" + threshold + "m.xml.gz");
                Network floodedRoadIndirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-network-indirect failure-threshold" + threshold + "m.xml.gz");
                Network floodedPtDirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-pt network-direct failure-threshold" + threshold + "m.xml.gz");
                Network floodedPtIndirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjing-pt network-operational failure-threshold" + threshold + "m.xml.gz");
                int roadLinkNumAfterDirect = networkAnalyzer.linkCount4SpecificModes(floodedRoadDirect, carMode) + networkAnalyzer.linkCount4SpecificModes(floodedRoadDirect, carBusMode);
                int roadLinkNumAfterIndirect = networkAnalyzer.linkCount4SpecificModes(floodedRoadIndirect, carMode) + networkAnalyzer.linkCount4SpecificModes(floodedRoadIndirect, carBusMode);
                int ptLinkNumAfterDirect = networkAnalyzer.linkCount4SpecificModes(floodedPtDirect, ptMode);
                int ptLinkNumAfterIndirect = networkAnalyzer.linkCount4SpecificModes(floodedPtIndirect, ptMode);
                writer.write(roadLinkNumAfterDirect + ",");
                writer.write(ptLinkNumAfterDirect + ",");
                writer.write(roadLinkNumAfterIndirect + ",");
                writer.write(ptLinkNumAfterIndirect + ",");
                writer.newLine();
                threshold += 1;
            }

//            3. use the refresh to make the results to the csv.
            writer.flush();

//            4. close.
            writer.close();

//            5. for baseline scenario.
            System.out.println("Baseline road link count: " + baseRoadLinkNum);
            System.out.println("Baseline pt link count: " + basePtLinkNum);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}