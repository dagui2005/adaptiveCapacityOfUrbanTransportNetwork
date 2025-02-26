package org.matsim.population;

import org.gdal.gdal.Dataset;
import org.matsim.analysis.population.SimpleAnalyzer;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.network.NetworkAttackFlood;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Chunhong li
 * @date: 2023年02月09日 17:03
 * @Description: If a place is directly flooded or indirectly failed due to connectivity, it could not be origin or destination anymore. The plan (agent) will be removed.
 * So, we use an activity failed map generated from python codes (tiff file) to identify whether the location is available.
 */
public class PopulationAttackFlood {
    public static void main(String[] args) {
        double[] thresholds = new double[]{0.003, 0.007, 0.01, 0.02, 0.07};
//        double[] thresholds = new double[]{0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0};
        for (double threshold : thresholds) {
//            TODO: Change the input files and output files. for LA
            String activityFailedMapPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\demand failure map\\需求失效分布.rp100.thereshold" + threshold + ".tif";
            String populationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my_baseline_LA_10pct_output_plans_min.xml.gz";
            String outputPopPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my-50th-LA-10pct-population-threshold" + threshold + "-direct.xml.gz";
            String outputPopPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my-50th-LA-10pct-population-threshold" + threshold + "-indirect.xml.gz";
//            TODO: Change the input files and output files. for Hamburg
//            String activityFailedMapPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\demand failure map\\需求失效分布.rp100.thereshold" + threshold + ".tif";
//            String populationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\xml\\my_hamburg_25pct_plans.xml.gz";
//            String outputPopPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\xml\\my_hamburg_25pct_plans_threshold" + threshold + "_direct.xml.gz";
//            String outputPopPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\population\\xml\\my_hamburg_25pct_plans_threshold" + threshold + "_indirect.xml.gz";

//        1. read the activity failure map and population
            Dataset dataset = NetworkAttackFlood.readTiff(activityFailedMapPath);
            Population population = PopulationUtils.readPopulation(populationPath);
            System.out.println("The number of agents: " + population.getPersons().size() + ". (Note: This may be a sample.)");

//        2. read the water depth for each activity and identify if a person is valid! Note: coordination system.
            ArrayList<Id<Person>> directFailedPersons = new ArrayList<>();  // We assume the person including his all trips is failed if one activity location is failed.
            ArrayList<Id<Person>> indirectFailedPersons = new ArrayList<>();
//            TODO: Change the coordinate transformation. for hamburg
//            CoordinateTransformation epsg25832To4326 = TransformationFactory.getCoordinateTransformation("epsg:25832", TransformationFactory.WGS84);
//            TODO: for LA
            CoordinateTransformation epsg3310To4326 = TransformationFactory.getCoordinateTransformation("epsg:3310", TransformationFactory.WGS84);
            for (Person person : population.getPersons().values()) {
                List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
                for (TripStructureUtils.Trip trip : trips) {
//                    each trip only consider his origin.
                    Coord coord = trip.getOriginActivity().getCoord();
                    Coord coordWGS84 = epsg3310To4326.transform(coord);
                    var failureValue = NetworkAttackFlood.getPixelValue(coordWGS84, dataset);
                    if (failureValue == 1) {
                        // directly flooded
                        directFailedPersons.add(person.getId());
                    } else if (failureValue == 2) {
                        // indirectly disconnected
                        indirectFailedPersons.add(person.getId());
                    }
                }
            }
            // 释放资源
            dataset.delete();

//        3. delete direct failed persons.
            for (Id<Person> directFailedPerson : directFailedPersons) {
                population.removePerson(directFailedPerson);
            }
            PopulationUtils.writePopulation(population, outputPopPath1);

//        4. delete indirect failed persons.
            for (Id<Person> indirectFailedPerson : indirectFailedPersons) {
                population.removePerson(indirectFailedPerson);
            }

//        5. original population file has much information about linkID. So we only save the necessary info to a new population file.
            Population miniPopulation = CleanPopulationUtils.minedPopulation(population);
            CleanPopulationUtils.deleteAgentNoActivity(miniPopulation);  // delete agents without any activity.
            PopulationUtils.writePopulation(miniPopulation, outputPopPath2);
        }
    }

//    public static void main(String[] args) {
////        TODO: only for LA, 100-year flooding scenario (Threshold = 0).
//        String floodMapPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】洪水数据\\los angeles flood data\\flood data\\my los angeles flood map\\tif\\flood map 50th.tif";
//        String inPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my_baseline_LA_10pct_output_plans_min.xml.gz";
//        String outPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my-50th-LA-10pct-population-threshold0.0-only consider inundation 20230321.xml.gz";
//        Double threshold = 0.0;
//        CoordinateTransformation epsg3310To4326 = TransformationFactory.getCoordinateTransformation("epsg:3310", TransformationFactory.WGS84);
//
//        removeInundatedAgents(inPopPath, outPopPath, NetworkAttackFlood.readTiff(floodMapPath), threshold, epsg3310To4326);
//
//////        test
////        Dataset dataset = NetworkAttackFlood.readTiff(floodMapPath);
////        Coord coord = new Coord(-118.69353628, 33.70592779);
////        Double value = NetworkAttackFlood.getPixelValue(coord, dataset);
////        System.out.println(value);
//    }

    /* only remove the inundated agents. this function don't consider path interruption. This is a little like NetworkAttackFlood.java */
    public static void removeInundatedAgents(String inPopPath, String outPopPath, Dataset floodData, Double threshold, CoordinateTransformation epsgXToWGS84) {
//        1. flood map is read only once. But population should be read multiple times.
        Population population = PopulationUtils.readPopulation(inPopPath);
        SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
        System.out.println("Baseline scenario: ");
        simpleAnalyzer.count(population);

//        2. read the water depth for each activity and identify if a person is valid! Note: coordination system.
        ArrayList<Id<Person>> directFailedPersons = new ArrayList<>();  // We assume the person including his all trips is failed if one activity location is failed.
        for (Person person : population.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                Coord coord = trip.getOriginActivity().getCoord();
                Coord coordWGS84 = epsgXToWGS84.transform(coord);
                Double waterDepth = NetworkAttackFlood.getPixelValue(coordWGS84, floodData);
                if (waterDepth > threshold) {
                    directFailedPersons.add(person.getId());
                }
            }
        }

//        3. remove the inundated persons.
        for (Id<Person> personId : directFailedPersons) {
            population.removePerson(personId);
        }
        System.out.println("Flooding scenario (threshold = " + threshold + " :");
        simpleAnalyzer.count(population);

//        4. original population file has much information about linkID. So we only save the necessary info to a new population file.
        Population miniPopulation = CleanPopulationUtils.minedPopulation(population);
        CleanPopulationUtils.deleteAgentNoActivity(miniPopulation);  // delete agents without any activity.
        System.out.println("After mining:");
        simpleAnalyzer.count(miniPopulation);
        PopulationUtils.writePopulation(miniPopulation, outPopPath);
    }
}
