package org.matsim.population;

import org.gdal.gdal.Dataset;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
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
 * @Description: If a place is directly flooded or indirectly failed due to connectivity, it could not be origin or destination anymore. Trips after the activities will be removed.
 * So, we use an activity failed map generated from python codes (tiff file) to identify whether the location is available.
 */
public class PopulationAttackFlood2 {
    public static void main(String[] args) {
//        double[] thresholds = new double[]{0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0};
        double[] thresholds = new double[]{0.0};
        for (double threshold : thresholds) {
//            TODO: Change the input files and output files. for LA
            String activityFailedMapPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\demand failure map\\需求失效分布.rp100.thereshold" + threshold + ".tif";
            String populationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my_baseline_LA_10pct_output_plans_min.xml.gz";
            String outputPopPath1 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my-50th-LA-10pct-population-threshold" + threshold + "-direct-20230322.xml.gz";
            String outputPopPath2 = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my-50th-LA-10pct-population-threshold" + threshold + "-indirect-20230322.xml.gz";
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
            PopulationFactory populationFactory = PopulationUtils.getFactory();
            for (Person person : population.getPersons().values()) {
                List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
                int validTripNum = -1; // 记录有几个 有效的 trip，-1 表示全部都有效，0 表示一个都没有效果，1表示有一个 trip 有效 TODO: 20230322 做到此处
                for (TripStructureUtils.Trip trip : trips) {
//                    each trip only consider his origin.
                    Coord coord = trip.getOriginActivity().getCoord();
                    Coord coordWGS84 = epsg3310To4326.transform(coord);
                    var failureValue = NetworkAttackFlood.getPixelValue(coordWGS84, dataset);
                    if (failureValue == 1 || failureValue == 2) {
                        // if the activity location is inundated or indirect failed, we change the plan,

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
}
