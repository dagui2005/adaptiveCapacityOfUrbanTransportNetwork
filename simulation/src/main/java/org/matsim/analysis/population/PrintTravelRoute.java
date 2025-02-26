package org.matsim.analysis.population;

import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;

import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * @author: Chunhong li
 * @date: 2022年12月01日 15:08
 * @Description: 打印每个人的每次 trip 的所有 legs，以及每个 leg 的所有 route. Car leg 依次经过的 linkId，容易得到；但是 pt leg 只能得到首末 link，不能得到经过的线路，不过线路可以通过首末 link 推算出来。
 * 案例参考 matsim-example-project-14 population-analysis
 */
public class PrintTravelRoute {

    public static void main(String[] args) throws FileNotFoundException {
        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【分析】影响评估\\洪水场景\\【分析】个体角度分析\\抽样样本\\Sampled_plans_threshold0.0m.xml");
        PrintStream ps = new PrintStream("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【分析】影响评估\\洪水场景\\【分析】个体角度分析\\出行轨迹\\the route of trips threshold0.0m.txt");
        System.setOut(ps);
        System.out.println("PersonID; Destination; MainMode; PT interaction duration time; LegMode; LegDepartureTime; LegTravelTime; LegDistance; LegStartLink; LegEndLink; LegRouteType; LegRouteDescription;");

//            1. remove wrong samples.
        DeleteWrongPlan deleteWrongPlan = new DeleteWrongPlan();
        Population populationMined = deleteWrongPlan.run(population);

        for (Person person : populationMined.getPersons().values()) {
            var selectedPlan = person.getSelectedPlan();

//            2. We want the specific path for car trip (also is car leg). We also want the specific public transit lines for bus trip or metro trip (!= pt leg).
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(selectedPlan)) {
                System.out.print(person.getId().toString() + "; ");   // personId
                // Car trip contains origin info, car leg info and destination info.
                // PT trip contains origin info, (walk leg, pt interaction activity, pt leg, ...) and destination info.
                Activity destinationActivity = trip.getDestinationActivity();
                //  consider destination. home or work
                System.out.print(destinationActivity.getType() + "; ");
                // To do: consider pt interaction time, this is waiting time.
                var legs = trip.getLegsOnly();
//                TripStructureUtils.getActivities(trip.getTripElements());   // 2022126 I want get stage activities (pt interaction). But there is something wrong. So I use the following codes.
                if (trip.getTripElements().size() > 1) {
                    System.out.print("pt" + "; ");  // mainMode
                    for (PlanElement tripElement : trip.getTripElements()) {
                        if (tripElement instanceof Activity) {
                            // pt interaction
//                        System.out.println(((Activity) tripElement).getStartTime());
//                        System.out.println(((Activity) tripElement).getEndTime());
                            System.out.print(((Activity) tripElement).getMaximumDuration().toString() + ", ");   // pt interaction duration
                        }
                    }
                    System.out.print("; ");
                } else {
                    System.out.print("car" + "; ");  // mainMode
                    System.out.print("NAN; ");   // pt interaction duration
                }
//                System.out.println(legs);
                for (Leg leg : legs) {
                    // all information about leg
                    System.out.print(leg.getMode() + "; ");
                    System.out.print(leg.getDepartureTime() + "; ");
                    System.out.print(leg.getTravelTime() + "; ");
                    System.out.print(leg.getRoute().getDistance() + "; ");
                    System.out.print(leg.getRoute().getStartLinkId() + "; ");
                    System.out.print(leg.getRoute().getEndLinkId() + "; ");
                    System.out.print(leg.getRoute().getRouteType() + "; ");    // for walk leg: generic; for car leg: links; for pt leg: default_pt
                    System.out.print(leg.getRoute().getRouteDescription() + "; ");  // for walk leg:    ; for car leg: 1112 1213 1323 2324; for pt leg: {"transitRouteId":"3to1","boardingTime":"18:35:00","transitLineId":"Blue Line","accessFacilityId":"2b","egressFacilityId":"1"}
//                    System.out.println(leg.getRoute().getTravelTime());  // the same as "leg.getTravelTime()".
//                    System.out.println(leg.getAttributes());   // for walk leg: { key=routingMode; object=pt }; for car leg: { key=routingMode; object=car }; for pt leg: { key=routingMode; object=pt }.
                }
                System.out.println();
            }
        }
        ps.close();
    }
}
