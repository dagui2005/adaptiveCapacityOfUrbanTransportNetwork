package org.matsim.pt;

import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * @author: Chunhong li
 * @date: 2022年12月07日 20:35
 * @Description: 输入 schedule.xml; 输出 txt。每行代表一条线路，依次是线路经过的站点的信息。
 */
public class Lines2StopFacilities {
    public static void main(String[] args) throws FileNotFoundException {
        TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule("src/main/resources/transitSchedule.xml");
        String outputPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\transitLines2StopFacilities\\transitLines2StopFacilities.txt";
        Lines2StopFacilities ls = new Lines2StopFacilities();
        ls.run(transitSchedule, outputPath);
    }

    public void run(TransitSchedule transitSchedule, String outputPath) throws FileNotFoundException {
        PrintStream ps = new PrintStream(outputPath);
        System.setOut(ps);
        System.out.print("Number; transitLineId; transitRouteId; transportMode; facilityAId, facilityBId, ...; facilityACoordX, facilityBCoordX, ...; facilityACoordY, facilityBCoordY, ...; \n");
        int number = 0;
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                number += 1;
                System.out.print(number + "; ");
                System.out.print(transitLine.getId().toString() + "; ");
                System.out.print(transitRoute.getId().toString() + "; ");
                System.out.print(transitRoute.getTransportMode() + "; ");

                for (TransitRouteStop  transitRouteStop : transitRoute.getStops()) {
                    System.out.print(transitRouteStop.getStopFacility().getId() + ", ");
                }
                System.out.print("; ");
                for (TransitRouteStop  transitRouteStop : transitRoute.getStops()) {
                    System.out.print(transitRouteStop.getStopFacility().getCoord().getX() + ", ");
                }
                System.out.print("; ");
                for (TransitRouteStop  transitRouteStop : transitRoute.getStops()) {
                    System.out.print(transitRouteStop.getStopFacility().getCoord().getY() + ", ");
                }
                System.out.print("; \n");
            }
        }
        ps.close();
    }
}
