package org.matsim.analysis.network;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2023年02月04日 15:45
 * @Description: 1. 统计初始场景的各个模式的网络的路段数，洪水场景下直接失效后各个模式的网络路段数，洪水场景下间接失效后的各个模式的路段数
 * 2. 道路网络的间接失效是最大连通子图处理，公交网络（Space L 建模）的间接失效是晕应处理。
 */
public class NetworkDamageAnalysis4OpenScenario {
    public static void main(String[] args) {
//        1. Todo: change the writeFilePath for different scenarios. create the output file path.
//        for hamburg.
//        String writeFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\network analysis\\network damage for hamburg added floods experiments.csv";
//        for LA
        String writeFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\network analysis\\network damage for my2 50th los angeles 0.01-0.19m.csv";
//        A. Todo: input network for different scenarios.
//        for hamburg
//        Network baselineNetwork = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-v3.0-network-with-pt.xml.gz");
//        for LA
        Network baselineNetwork = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-los-angeles-v1.0-network_2019-12-10.xml.gz");

        SimpleAnalyzer networkAnalyzer = new SimpleAnalyzer();
//        2. create a bufferWriter.
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(writeFilePath));
            HashMap<Set<String>, Integer> baselineLinkCount = networkAnalyzer.linkCount(baselineNetwork);

//            2.1 the first line of csv.
            writer.write("threshold (m),");  // col name
            var otherColName = new ArrayList<Set<String>>();
            for (Set<String> colName : baselineLinkCount.keySet()) {
                writer.write("\"" + colName.toString() + "\"" + "after direct" + ",");   // col name
                otherColName.add(colName);
            }
            for (Set<String> colName : baselineLinkCount.keySet()) {
                writer.write("\"" + colName.toString() + "\"" + "after indirect" + ",");   // col name
            }
            writer.newLine();   //  change the new line.

//            2.2 the following line. for flooding scenario.
//            Todo: for Hamburg
//            double[] thresholds = new double[]{0.0, 0.3, 0.5, 1.0, 1.2, 1.4, 1.5, 1.6, 1.8, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0, 10.5, 11.0, 11.5, 12.0, 12.5, 13.0, 13.5, 14.0, 14.5, 15.0};
////            Todo: for LA
            double threshold = 0.01;
            while (threshold < 0.2) {
//            double[] thresholds = new double[]{0.0, 0.2, 0.3, 0.4, 0.6, 0.8, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 30.0};
//            for (double threshold : thresholds) {
                writer.write(threshold + ",");
//                  Todo: For different scenarios. for hamburg
                Network floodedNetworkDirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-direct failure-threshold" + threshold + "m.xml.gz");
                Network floodedNetworkIndirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-pt failure-threshold" + threshold + "m.xml.gz");
//                for Los Angeles
//                Network floodedNetworkDirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-direct failure-threshold" + threshold + "m.xml.gz");
//                Network floodedNetworkIndirect = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-pt failure-threshold" + threshold + "m.xml");
                var floodedLinkCountDirect = networkAnalyzer.linkCount(floodedNetworkDirect);
                var floodedLinkCountIndirect = networkAnalyzer.linkCount(floodedNetworkIndirect);
                for (Set<String> colName : otherColName) {
                    writer.write(floodedLinkCountDirect.getOrDefault(colName, 0) + ",");  // getOrDefault() 方法获取指定 key 对应对 value，如果找不到 key ，则返回设置的默认值。
                }

                for (Set<String> colName : otherColName) {
                    writer.write(floodedLinkCountIndirect.getOrDefault(colName, 0) + ",");  // getOrDefault() 方法获取指定 key 对应对 value，如果找不到 key ，则返回设置的默认值。
                }
                writer.newLine();

                threshold += 0.01;
            }

//            3. use the refresh to make the results to the csv.
            writer.flush();

//            4. close.
            writer.close();

//            5. for baseline scenario.
            System.out.println("Baseline network link count: " + baselineLinkCount);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
