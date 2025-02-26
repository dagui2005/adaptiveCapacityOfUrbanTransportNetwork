package org.matsim.analysis.population;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;

import java.net.URL;
import java.util.List;

/**
 * @author: Chunhong li
 * @date: 2023年03月09日 9:29
 * @Description:
 */
public class PrintPlan {
    public static void run(Population population, Id<Person> targetPersonId){
        Person person = population.getPersons().get(targetPersonId);
//        print his plan.
        Plan plan = person.getSelectedPlan();
        List<Activity> activities = TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
        List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(plan);
        System.out.println(activities);
        System.out.println(trips);

//        export to a population.xml
        Config config = ConfigUtils.loadConfig("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】公开场景\\【场景】美国洛杉矶\\baseline\\input\\los-angeles-v1.1-0.1pct.config.xml");
        Population populationTarget = PopulationUtils.createPopulation(config);
        populationTarget.addPerson(person);
        new PopulationWriter(populationTarget).write("C:\\Users\\17120\\Desktop\\" + targetPersonId + ".xml");
    }

    public static void main(String[] args) {
        URL url =  PrintPlan.class.getResource("/los angeles/los-angeles-v1.0-population-0.1pct_2019-12-09.xml.gz");
        Population population = PopulationUtils.readPopulation(String.valueOf(url));
        run(population, Id.createPersonId("1000053"));
    }
}
