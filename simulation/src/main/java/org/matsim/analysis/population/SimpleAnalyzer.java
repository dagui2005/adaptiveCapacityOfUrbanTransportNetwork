package org.matsim.analysis.population;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;

import java.util.*;

/**
 * @author: Chunhong li
 * @date: 2023年01月15日 20:13
 * @Description:
 */
public class SimpleAnalyzer {
    public static void main(String[] args) {
//        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】公开场景\\【场景】德国汉堡\\input\\baseCase\\hamburg-v3.0-25pct-base.plans.xml.gz");
//        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\simulation\\flood-25pct\\threshold2\\hamburg-v3.0-25pct-base.output_plans.xml.gz");
        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my-50th-LA-10pct-population-threshold0.0-indirect.xml.gz");

        SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
//        print all activities.
//        simpleAnalyzer.activityAnalysis(population);

//        print all subpopulations
//        simpleAnalyzer.subpopulationAnalysis(population);

//        count the agents and trips
        simpleAnalyzer.count(population);

//        print the scope of population.
        // CoordinateTransformation for Hamburg, German
//        CoordinateTransformation EPSG25832ToWGS84 = TransformationFactory.getCoordinateTransformation("epsg:25832", TransformationFactory.WGS84);
//        simpleAnalyzer.printScope(population, EPSG25832ToWGS84);
        // CoordinateTransformation for Nanjing, China
        // ....
        // CoordinateTransformation for Los Angeles, America. Todo: Coord
//        CoordinateTransformation epsg3310ToWGS84 = TransformationFactory.getCoordinateTransformation("epsg:3310", TransformationFactory.WGS84);
//        simpleAnalyzer.printScope(population, epsg3310ToWGS84);
    }

    /* print all unduplicated activities. */
    public static void activityAnalysis(Population population) {
        HashSet<String> activities = new HashSet<String>();
        for (Person person : population.getPersons().values()) {
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
                String destinationActivity = trip.getDestinationActivity().getType();
                String originActivity = trip.getOriginActivity().getType();

                // In the open scenario of hamburg and los-angeles, each activity has multiplex names. For example, educ_primary_1, educ_primary-2, ...
                String[] validName1 = Arrays.copyOfRange(destinationActivity.split("_"), 0, destinationActivity.split("_").length - 1);
                destinationActivity = Arrays.toString(validName1);
                String[] validName2 = Arrays.copyOfRange(originActivity.split("_"), 0, originActivity.split("_").length - 1);
                originActivity = Arrays.toString(validName2);

                activities.add(destinationActivity);
                activities.add(originActivity);
            }
        }

        System.out.println("All unduplicated activities: ");
        System.out.println(activities);
    }

    /* print all subpopulations. */
    public void subpopulationAnalysis(Population population) {
        HashSet<String> subpopulations = new HashSet<String>();
        for (Person person : population.getPersons().values()) {
            // Apart from "subpopulation", Attributes have other attributes, such as age, education ...
            subpopulations.add(person.getAttributes().getAttribute("subpopulation").toString());
        }

        System.out.println("All unduplicated subpopulations:");
        System.out.println(subpopulations);
    }


    /* print the number of agents and trips. */
    public void count(Population population) {
        System.out.println("The number of agents: " + population.getPersons().size());
        int tripNum = 0;
        for (Person person : population.getPersons().values()) {
            var trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            tripNum += trips.size();
        }
        System.out.println("The number of trips: " + tripNum);
    }

    /* print the scope of population area using minx,miny, maxx and maxy. Note: we use WGS84 coordination instead of projection coordination. */
    public void printScope(Population population, CoordinateTransformation coordinateTransformation) {
        // Projection coordination.
        // first person' coordination is assigned to minx, miny, max, may.
        Iterator<? extends Person> iterator = population.getPersons().values().iterator(); // iterator 可以随时使用循环，随时中止循环（可拆分循环）
        Person person1 = iterator.next();
        List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person1.getSelectedPlan());

//        20230302 Bug: the first person has no trip.
        double minx = 0;  // 0 is not the first value. We must assign a value to it.
        double miny = 0;
        double maxx = 0;
        double maxy = 0;
        boolean isFirstValueGiven = false;
        while (!isFirstValueGiven){   // The loop continues if the first value not be given
            if (trips.size() > 0 ){
            minx = trips.get(0).getOriginActivity().getCoord().getX();
            miny = trips.get(0).getOriginActivity().getCoord().getY();
            maxx = trips.get(0).getOriginActivity().getCoord().getX();
            maxy = trips.get(0).getOriginActivity().getCoord().getY();
            isFirstValueGiven = true;}
            else {
                person1 = iterator.next();
                trips = TripStructureUtils.getTrips(person1.getSelectedPlan());
            }
        }

        // other persons;
        while (iterator.hasNext()) {
            trips = TripStructureUtils.getTrips(iterator.next().getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                double thisX = trip.getOriginActivity().getCoord().getX();
                double thisY = trip.getOriginActivity().getCoord().getY();
                if (thisX < minx) {
                    minx = thisX;
                }
                if (thisX > maxx) {
                    maxx = thisX;
                }
                if (thisY < miny) {
                    miny = thisY;
                }
                if (thisY > maxy) {
                    maxy = thisY;
                }
            }
        }
        // WGS84 coordination.
        Coord coord1 = coordinateTransformation.transform(new Coord(minx, miny));  // This coord may be a virtual point, not a real activity location.
        Coord coord2 = coordinateTransformation.transform(new Coord(maxx, maxy));
        System.out.println("The point on the lower left conner (WGS84):" + coord1.getX() + "; " + coord1.getY());
        System.out.println("The point on the upper right conner (WGS84):" + coord2.getX() + "; " + coord2.getY());
    }

    /* Count the number of trips using specific mode. */
    public HashMap<String, Integer> countTripNumSpecificModes(Population population, HashSet<String> tripModes) {
        String thisMainMode = null;
        HashMap<String, Integer> tripModes2Num = new HashMap<String, Integer>();
        for (String tripMode : tripModes) {
            tripModes2Num.put(tripMode, 0);
        }
        for (Person person : population.getPersons().values()) {
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
//                MATSim don't provide the main mode in this trip. We thus take the first leg mode (no walk ) as the main mode.
//                However, if this trip has only one leg, we have to select the only leg mode as the main mode. If the trip has multiplex legs, it will have other leg mode other than walk.
                for (Leg leg : trip.getLegsOnly()) {
                    if ((!leg.getMode().equals("walk")) && (trip.getLegsOnly().size() > 1)) {
                        thisMainMode = leg.getMode();
                        break;
                    }
                    if (trip.getLegsOnly().size() == 1) {
                        thisMainMode = leg.getMode();
                    }
                }
                if (tripModes2Num.containsKey(thisMainMode)) {
                    tripModes2Num.put(thisMainMode, tripModes2Num.get(thisMainMode) + 1);
                }
            }
        }
        return tripModes2Num;
    }

    /* Return all available trip mode */
    public HashSet<String> countTripModes(Population population) {
        String thisMainMode = null;
        HashSet<String> tripModes = new HashSet<>();
        for (Person person : population.getPersons().values()) {
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
//                MATSim don't provide the main mode in this trip. We thus take the first leg mode (no walk ) as the main mode.
//                However, if this trip has only one leg, we have to select the only leg mode as the main mode. If the trip has multiplex legs, it will have other leg mode other than walk.
                for (Leg leg : trip.getLegsOnly()) {
                    if ((!leg.getMode().equals("walk")) && (trip.getLegsOnly().size() > 1)) {
                        thisMainMode = leg.getMode();
                        break;
                    }
                    if (trip.getLegsOnly().size() == 1) {
                        thisMainMode = leg.getMode();
                    }
                }
                tripModes.add(thisMainMode);
            }
        }
        System.out.println("All trip main mode: " + tripModes);
        return tripModes;
    }

}
