package org.matsim.analysis.schedule;

import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author: Chunhong li
 * @date: 2023年02月16日 9:49
 * @Description: general codes for count the residual transitStops and transitLines in flooding scenarios.
 */
public class ScheduleDamageAnalysis4OpenScenario {
    public static void main(String[] args) throws IOException {
//        Todo: output csv file path
//        for hamburg.
//        String writeFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\schedule\\schedule analysis\\transit system damages for hamburg.csv";
//        for nanjing.
//        String writeFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【分析】影响评估\\洪水场景\\【分析】运营破坏相关分析\\transit system damages for nanjing_the same format as hamburg.csv";
//        for LA.
        String writeFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\schedule analysis\\transit system damages for my2 50th LA.csv";

//        count transitRoute num and transitStops num for baseline scenario.
        SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
//        Todo: for hamburg
//        String baselineSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\schedule\\xml\\hamburg-v3.0-transitSchedule.xml";
//        for nanjing
//        String baselineSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\public transit schedule XML\\transitSchedule.xml";
//        for Los Angeles
        String baselineSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-los-angeles-v1.0-transitSchedule_2019-12-18.xml.gz";
        System.out.println("...........Baseline scenario..................");
        HashMap<String, ArrayList<Integer>> baseTransportMode2RouteAndStopNum = simpleAnalyzer.countRouteAndStop(ScheduleTools.readTransitSchedule(baselineSchedulePath));

//        create a bufferedWriter and write the first line.
        BufferedWriter writer = new BufferedWriter(new FileWriter(writeFilePath));
        writer.write("threshold(m),");  // col name
        HashSet<String> otherColNames = new HashSet<>();
        for (String transportMode : baseTransportMode2RouteAndStopNum.keySet()) {
            writer.write(transportMode + "_RouteNum" + ",");   // col name
            writer.write(transportMode + "_StopNum" + ",");   // col name
            otherColNames.add(transportMode);
        }
        writer.newLine();   //  change the new line.

//        count transitRoute num and transitStops for flooding scenarios.
        double threshold = 0.0;
        while (threshold <= 15) {
            writer.write(threshold + ",");
//            Todo: Schedules for flooded hamburg
//            String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\schedule\\xml\\hamburg-transitSchedule-threshold" + threshold + "m.xml.gz";
//            Schedules for flooded nanjing
//            String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after floods damages\\xml\\residual transit schedule.rp100.thereshold" + threshold + "m.xml";
//            Schedules for flooded los angeles
            String schedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-50th-los-angeles-transitSchedule-threshold" + threshold + "m.xml.gz";
            TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule(schedulePath);
            HashMap<String, ArrayList<Integer>> transportMode2RouteAndStopNum = simpleAnalyzer.countRouteAndStop(transitSchedule);
            for (String transportMode : otherColNames) {
                // extract the route and stop num. Note maybe there is no route or stop for a specific transportMode.
                ArrayList<Integer> defaultRouteAndStopNum = new ArrayList<>();
                defaultRouteAndStopNum.add(0);
                defaultRouteAndStopNum.add(0);
                ArrayList<Integer> routeAndStopNum = transportMode2RouteAndStopNum.getOrDefault(transportMode, defaultRouteAndStopNum);
                writer.write(routeAndStopNum.get(0) + ",");  // route num
                writer.write(routeAndStopNum.get(1) + ",");  // stop num
            }
            writer.newLine();
            threshold += 1;
        }

        writer.flush();
        writer.close();
    }
}
