package org.matsim.analysis.population;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.router.TripStructureUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2022年12月11日 15:06
 * @Description:
 */
public class DeleteWrongPlan {
    public static void main(String[] args) {
//        string extraction.
        String optionalTime = "OptionalTime[18900.0]";
        System.out.print(optionalTime.substring(13, optionalTime.length() - 1));  // 前包后不包
    }

    public Population run(Population population) {
        Set<Person> removedPerson = new HashSet<>();
        for (Person person : population.getPersons().values()) {
            boolean isWrong = false;
            Plan selectedPlan = person.getSelectedPlan();
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(selectedPlan)) {
                int duration = 0;
                for (Leg leg : trip.getLegsOnly()) {
                    String optionalLegTime = leg.getTravelTime().toString();
                    double legTime = Double.parseDouble(optionalLegTime.substring(13, optionalLegTime.length() - 1));
                    duration += legTime;

                    if (trip.getLegsOnly().size() == 1 && leg.getMode().equals("walk")) {
                        // 2. trip has only a walk leg.
                        isWrong = true;
                    }

                }
                if (duration >= 300 * 60) {
                    //  1. duration of duration time exceeds 300min.
                    isWrong = true;
                }
                if (isWrong){
                    removedPerson.add(person);
                }
            }
        }
        for (Person person : removedPerson) {
            population.removePerson(person.getId());
        }
        return population;
    }
}
