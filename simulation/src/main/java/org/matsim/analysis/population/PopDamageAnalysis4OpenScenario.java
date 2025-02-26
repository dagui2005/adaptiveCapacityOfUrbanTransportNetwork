package org.matsim.analysis.population;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author: Chunhong li
 * @date: 2023年02月10日 21:0200
 * @Description: 统计开源场景的失效组成。
 * 基准场景，OD inundation 后，path interruption 后，以及最终的 population 见20230223笔记*
 */
public class PopDamageAnalysis4OpenScenario {
    public static void main(String[] args) throws IOException {
//        for Hamburg, German.
        File popCountFile = new File("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\populationAnalysis\\tripCountForEachMode.csv");
//        for nanjing, China. Todo
//        File popCountFile = new File("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【分析】影响评估\\洪水场景\\【分析】功能破坏相关分析\\需求破坏_预处理\\populationAnalysis the same format as hamburg\\popTripCount.txt");
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(popCountFile));
//        baseline scenario.
        Population basePop = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\xml\\my_hamburg_25pct_plans.xml.gz");
        SimpleAnalyzer popAnalyzer = new SimpleAnalyzer();
        HashSet<String> tripModes = popAnalyzer.countTripModes(basePop);
        HashMap<String, Integer> tripModes2Num = popAnalyzer.countTripNumSpecificModes(basePop, tripModes);
//        Write the column names according the results of baseline.
        bufferedWriter.write("Threshold,");
        for (String tripMode : tripModes) {
            bufferedWriter.write(tripMode + "TripNumAfterOD" + ",");
        }
        for (String tripMode : tripModes) {
            bufferedWriter.write(tripMode + "TripNumAfterPath" + ",");
        }
        bufferedWriter.newLine();

//        Flooding scenarios.
        double threshold = 0.0;
        while (threshold <= 8) {
            bufferedWriter.write(threshold + ",");
            // After OD inundation.
            Population popFlood = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\xml\\hamburg-25pct-plans-threshold" + threshold + "-direct.xml.gz");
            HashMap<String, Integer> tripModes2NumFlood = popAnalyzer.countTripNumSpecificModes(popFlood, tripModes);
            for (String tripMode : tripModes) {
                bufferedWriter.write(tripModes2NumFlood.get(tripMode) + ",");
            }

            // After path interruption.
            popFlood = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\xml\\hamburg-25pct-plans-threshold" + threshold + "-indirect.xml.gz");
            tripModes2NumFlood = popAnalyzer.countTripNumSpecificModes(popFlood, tripModes);
            for (String tripMode : tripModes) {
                bufferedWriter.write(tripModes2NumFlood.get(tripMode) + ",");
            }

            bufferedWriter.newLine();
            threshold += 1;
        }
        System.out.println("Baseline trip num for each trip mode: " + tripModes2Num);
        bufferedWriter.flush();
        bufferedWriter.close();
    }
}
