package org.matsim.analysis.schedule;

import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author: Chunhong li
 * @date: 2023年02月15日 16:18
 * @Description:
 */
public class SimpleAnalyzer {
    public HashMap<String, ArrayList<Integer>> countRouteAndStop(TransitSchedule transitSchedule) {
        System.out.println("the number of transit stops: " + transitSchedule.getFacilities().size());

        // Key: transportModes. Value: the number of corresponding transit routes and stops.
        HashMap<String, ArrayList<Integer>> transportMode2TransitRouteAndStopNum = new HashMap<>();
        int transitLineNum = transitSchedule.getTransitLines().size();
        int transitRouteNum = 0;
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
            transitRouteNum += transitLine.getRoutes().size();
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                String thisTransportMode = transitRoute.getTransportMode();
                int newRouteNum;
                int newStopNum;
                ArrayList<Integer> values = new ArrayList<>();
                if (transportMode2TransitRouteAndStopNum.containsKey(thisTransportMode)) {
                    newRouteNum = transportMode2TransitRouteAndStopNum.get(thisTransportMode).get(0) + 1;
                    newStopNum = transportMode2TransitRouteAndStopNum.get(thisTransportMode).get(1) + transitRoute.getStops().size();
                } else {
                    newRouteNum = 1;
                    newStopNum = transitRoute.getStops().size();
                }
                values.add(newRouteNum);
                values.add(newStopNum);
                transportMode2TransitRouteAndStopNum.put(thisTransportMode, values);
            }
        }

        System.out.println("The number of transit Line: " + transitLineNum);
        System.out.println("The number of transit Route: " + transitRouteNum);
        System.out.println("TransportMode to its transitRoute and transitStops num" + transportMode2TransitRouteAndStopNum);

        return transportMode2TransitRouteAndStopNum;
    }

    public static void main(String[] args) {
        TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-los-angeles-v1.0-transitSchedule_2019-12-18.xml.gz");
        SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
        simpleAnalyzer.countRouteAndStop(transitSchedule);
    }
}
