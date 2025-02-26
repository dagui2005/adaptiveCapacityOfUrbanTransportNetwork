package org.matsim.analysis.population;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.population.PopulationUtils;

import java.util.HashSet;

/**
 * @author: Chunhong li
 * @date: 2023年01月29日 21:45
 * @Description: Certain subpopulations are extracted by attribute.
 */
public class ExtractSubpopulation {
    public static void main(String[] args) {
//        For hamburg scenario
//        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】公开场景\\【场景】德国汉堡\\input\\baseCase\\hamburg-v3.0-25pct-base.plans.xml.gz");
//        run(population, "person");   // Agent are "person" or "commercial" in the hamburg scenario.
//        new PopulationWriter(population).write("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】公开场景\\【场景】德国汉堡\\input\\myBaseCase\\hamburg-v3.0-25pct-base.plans.onlyPersons.xml.gz");

//        For Los Angeles scenario
        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】公开场景\\【场景】美国洛杉矶\\baseline\\los-angeles-v1.0-population-0.1pct_2019-12-09.xml.gz");
        run(population, "person");   // Agent are "person" or "freight" in the Los Angeles scenario.
        new PopulationWriter(population).write("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】公开场景\\【场景】美国洛杉矶\\myBaseline\\los-angeles-v1.0-population-0.1pct_2019-12-09.onlyPersons.xml.gz");
    }

    public static void run(Population population, String preferredValue){
        HashSet<Id<Person>> removedPersonIds = new HashSet<>();
        for (Person person : population.getPersons().values()) {
            var tag = person.getAttributes().getAttribute("subpopulation").toString();
            if (!tag.equals(preferredValue)){
                removedPersonIds.add(person.getId());
            }
        }
        for (Id<Person> removedPersonId : removedPersonIds) {
            population.removePerson(removedPersonId);
        }
    }
}
