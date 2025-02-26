package org.matsim.analysis.event;

import org.matsim.api.core.v01.Id;
import org.matsim.core.events.EventsUtils;
import org.matsim.pt.analysis.TransitRouteAccessEgressAnalysis;
import org.matsim.pt.analysis.VehicleTracker;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: Chunhong li
 * @date: 2022年12月22日 11:49
 * @Description: TransitRouteAccessEgressAnalysis:
 * construct function (method): 输入 transitRoute 和 vehicleTracker （VehicleTracker 追踪系统中所有的 (pt) vehicle 在的 facility ）
 * headings (Map) vehicleID: departure
 * accessCounters (Map): Departure:(Map(facilityID: accessPassengerNum))
 * handleEvent (PersonEntersVehicleEvent): 通过 vehicle 找到其对应的 departure，以及其现在所在 facilityID，在该 departure 及对应的 facilityID 上加一
 */
public class RunTransitRouteAccessEgressAnalysis {
    public static void main(String[] args) throws FileNotFoundException {
        String eventsFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\无洪水\\output01_demand.noex20.0.27.100pct\\output_events.xml.gz";
        String transitSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\无洪水\\output01_demand.noex20.0.27.100pct\\output_transitSchedule.xml.gz";
        String egressFilePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\【分析】节点恢复顺序\\【场景】洪水场景\\【输入】公交线路流量\\egress passengers baseline.txt";

        TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule(transitSchedulePath);
        run(transitSchedule, eventsFilePath, egressFilePath);
    }

    public static void run(TransitSchedule transitSchedule, String eventFilePath, String egressFilePath) throws FileNotFoundException {
//        1.create an eventManager.
        var eventsManager = EventsUtils.createEventsManager();

//        2.create two kind of eventHandlers and add them to the eventManager.
//        2.0 eventHandler: VehicleTracker
        VehicleTracker vehicleTracker = new VehicleTracker();
        eventsManager.addHandler(vehicleTracker);
//        2.1 EventHandler: TransitRouteAccessEgressAnalysis
        // 每次 transitRoute 都对应一个 eventHandler，有多少 transitRoute，对应多少个 eventHandler
        int transitRouteNum = transitSchedule.getTransitLines().values().stream().mapToInt(transitLine -> transitLine.getRoutes().size()).sum();
        System.out.println("The number of transitRoute is " + transitRouteNum);
        Map<TransitRoute, TransitRouteAccessEgressAnalysis> transitRouteAccessEgressAnalysisMap = new HashMap<>();
        Map<Id<TransitRoute>, Map<Departure, Integer>> egressCounters4All = new HashMap<>();  // 最关键的，最后输出全靠它

        int k = 0;
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                TransitRouteAccessEgressAnalysis transitRouteAccessEgressAnalysis = new TransitRouteAccessEgressAnalysis(transitRoute, vehicleTracker);
                transitRouteAccessEgressAnalysisMap.put(transitRoute, transitRouteAccessEgressAnalysis);
                eventsManager.addHandler(transitRouteAccessEgressAnalysisMap.get(transitRoute));
            }
        }

//        3. read the events.
        EventsUtils.readEvents(eventsManager, eventFilePath);

//        4. write it to txt.
        PrintStream ps = new PrintStream(egressFilePath);
        System.setOut(ps);
        System.out.println("transitRouteId; excessNumForEachDeparture");
        for (TransitRoute transitRoute : transitRouteAccessEgressAnalysisMap.keySet()) {
            System.out.print(transitRoute.getId().toString() + "; ");   // transitRoute ID
            TransitRouteAccessEgressAnalysis transitRouteAccessEgressAnalysis = transitRouteAccessEgressAnalysisMap.get(transitRoute);
            for (Departure departure : transitRoute.getDepartures().values()) {
                int excessNum4Departure = 0;  // 该 transitRoute 的 该 departure 的 下车总人数 （所有站点的下车总人数）
                for (Integer excessNum : transitRouteAccessEgressAnalysis.getAccessCounter(departure).values()) {
                    if (excessNum != null){
                        excessNum4Departure += excessNum;
                    }
                }
                System.out.print(excessNum4Departure + "; ");  // the number of excess passengers.
            }
            System.out.print("\n");
        }
        ps.close();
    }
}
