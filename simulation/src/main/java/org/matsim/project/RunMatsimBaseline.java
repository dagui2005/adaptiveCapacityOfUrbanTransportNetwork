/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.project;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * @author Chunhong Li
 * @Description: run the simulation.
 */
public class RunMatsimBaseline {
    private static final String configFile = "scenarios/nanjingBaseline/config.xml";
    private static final String networkFile = "network.xml";
    private static final String plansFile = "demand.xml";
    private static final String scheduleFile = "transitSchedule.xml";
    private static final String vehiclesFile = "transitVehicle.xml";
    private static final String outputFile = "scenarios/nanjingMultiModal/output";

    public static void main(String[] args) {
        Config config = ConfigUtils.loadConfig(configFile);

        long seed = 179;
        config.global().setRandomSeed(seed);

        config.network().setInputFile(networkFile);
        config.plans().setInputFile(plansFile);
        config.transit().setTransitScheduleFile(scheduleFile);
        config.transit().setUseTransit(true);
        config.transit().setVehiclesFile(vehiclesFile);
        config.controler().setOutputDirectory(outputFile);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setLastIteration(50);
        config.controler().setWriteEventsInterval(50);

        config.changeMode().setModes(new String[] {"car","pt"});

        config.planCalcScore().setPerforming_utils_hr(75);    // Unit time value per capita in Nanjing
        config.planCalcScore().setMarginalUtilityOfMoney(1);   // The marginal utility of money. Positive.
        config.planCalcScore().setUtilityOfLineSwitch(-2);
        config.planCalcScore().setEarlyDeparture_utils_hr(0);
        config.planCalcScore().setLateArrival_utils_hr(0);
        PlanCalcScoreConfigGroup.ModeParams ptParams = new PlanCalcScoreConfigGroup.ModeParams("pt");
        ptParams.setConstant(-2.5);   // "[utils] mode-specific constant. Normally per trip, but that is probably buggy for multi-leg trips."
        ptParams.setMarginalUtilityOfDistance(0);   // [unit / m]  the marginal utility of distance.
        ptParams.setMarginalUtilityOfTraveling(0);   // [unit / hr] the direct marginal utility of time spent travelling by mode.
        ptParams.setMonetaryDistanceRate(0);  // "[money / m] conversion of distance into money. Normally negative."
        ptParams.setDailyUtilityConstant(0);  // [unit / day]
        ptParams.setDailyMonetaryConstant(0);   // [money / day]
        ptParams.setMarginalUtilityOfTraveling(-15);
        PlanCalcScoreConfigGroup.ModeParams carParmas = new PlanCalcScoreConfigGroup.ModeParams("car");

        carParmas.setConstant(-80);

        carParmas.setMarginalUtilityOfDistance(0);
        carParmas.setMarginalUtilityOfTraveling(0);
        carParmas.setMonetaryDistanceRate(-0.00056);
        carParmas.setDailyMonetaryConstant(0);
        carParmas.setDailyUtilityConstant(0);

        config.planCalcScore().addParameterSet(ptParams);
        config.planCalcScore().addParameterSet(carParmas);

        config.counts().setCountsScaleFactor(100);
        config.qsim().setFlowCapFactor(1);
        config.qsim().setStorageCapFactor(1);

        config.global().setNumberOfThreads(48);  // innovative strategies. using the number of available cores.
        config.qsim().setNumberOfThreads(47);  // parallel qsim.
        config.parallelEventHandling().setNumberOfThreads(4);  // event handling.

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Controler controler = new Controler(scenario);

        // possibly modify controler here
//        controler.addControlerListener(new MyControlerListener());
//        controler.addOverridingModule(new OTFVisLiveModule());

        controler.run();
    }

}
