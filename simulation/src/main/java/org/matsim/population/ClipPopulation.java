package org.matsim.population;

import org.matsim.analysis.population.SimpleAnalyzer;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.HashSet;

/**
 * @author: Chunhong li
 * @date: 2023年03月08日 17:26
 * @Description: clip the population based on the rectangular scope or shp file (We will add the function later).
 */
public class ClipPopulation {

    public static void clipPopBasedOnRectScope(Population population, Coord minXY, Coord maxXY) {
        HashSet<Id<Person>> agentIdsBeyondScope = new HashSet<>();
        for (Person person : population.getPersons().values()) {
            Plan selectedPlan = person.getSelectedPlan();
            for (PlanElement planElement : selectedPlan.getPlanElements()) {
                if (planElement instanceof Activity) {
//                    NOTE: if any activity is beyond the scope, we think the agent is beyond the scope.
                    Coord coord = ((Activity) planElement).getCoord();
                    if (coord.getX() < minXY.getX() || coord.getX() > maxXY.getX()) {
                        agentIdsBeyondScope.add(person.getId());
                        continue;
                    }
                    if (coord.getY() < minXY.getY() || coord.getY() > maxXY.getY()) {
                        agentIdsBeyondScope.add(person.getId());
                    }
                }
            }
        }

        for (Id<Person> personId : agentIdsBeyondScope) {
            population.removePerson(personId);
        }
    }

    public static void main(String[] args) {
        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\los-angeles-v1.0-population-25pct_2020-03-07.xml.gz");
        String outputPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\population\\xml\\my-los-angeles-v1.0-population-25pct_2020-03-07.xml.gz";
//        Statistics.
        System.out.println("Original population: ");
        SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
        simpleAnalyzer.count(population);

//        Clip scope. (LA flood map)
        Coord minLonLat = new Coord(-118.69353628, 33.70592779);
        Coord maxLonLat = new Coord(-117.64637248, 34.41048562);
//        TODO: Los Angeles:3310
        CoordinateTransformation wgs84ToEpsg3310 = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "epsg:3310");
        Coord minXY = wgs84ToEpsg3310.transform(minLonLat);
        Coord maxXY = wgs84ToEpsg3310.transform(maxLonLat);
//        Clip the population
        clipPopBasedOnRectScope(population, minXY, maxXY);
        System.out.println("After clipping, population: ");
        simpleAnalyzer.count(population);
//        mine the minimal population file.
        Population miniPopulation = CleanPopulationUtils.minedPopulation(population);
        CleanPopulationUtils.deleteAgentNoActivity(miniPopulation);  // delete agents without any activity.
        System.out.println("After mining, population: ");
        simpleAnalyzer.count(miniPopulation);

        new PopulationWriter(miniPopulation).write(outputPopPath);
    }
}
