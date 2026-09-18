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
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * @author Chunhong Li
 * @Description: run the simulation.
 */
public class RunMatsimBaseline {
//    private static final String configFile = "C:\\Users\\LQP\\IdeaProjects\\adaptiveCapacityOfUrbanTransportNetwork\\simulation\\scenarios\\nanjingBaseline\\config.xml";
//    private static final String networkFile = "C:\\Users\\LQP\\IdeaProjects\\adaptiveCapacityOfUrbanTransportNetwork\\simulation\\scenarios\\nanjingBaseline\\network.xml";
//    private static final String plansFile = "C:\\Users\\LQP\\IdeaProjects\\adaptiveCapacityOfUrbanTransportNetwork\\simulation\\scenarios\\nanjingBaseline\\demand.xml";
//    private static final String scheduleFile = "C:\\Users\\LQP\\IdeaProjects\\adaptiveCapacityOfUrbanTransportNetwork\\simulation\\scenarios\\nanjingBaseline\\transitSchedule.xml";
//    private static final String vehiclesFile = "C:\\Users\\LQP\\IdeaProjects\\adaptiveCapacityOfUrbanTransportNetwork\\simulation\\scenarios\\nanjingBaseline\\transitVehicle.xml";
//    private static final String outputFile = "C:\\Users\\LQP\\IdeaProjects\\adaptiveCapacityOfUrbanTransportNetwork\\simulation\\scenarios\\nanjingBaseline\\scenarios/nanjingBaselineOutput";

    private static final String configFile = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\config.xml";
    private static final String networkFile = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\network_with_transit.xml";
    private static final String plansFile = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\demand.xml";
    private static final String scheduleFile = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\transitSchedule.xml";
    private static final String vehiclesFile = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\transitVehicles.xml";
    private static final String outputFile = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaselineOutput";
    public static void main(String[] args) {
        Config config = ConfigUtils.loadConfig(configFile);

        long seed = 179;
        config.global().setRandomSeed(seed);

        config.network().setInputFile(networkFile);
        config.plans().setInputFile(plansFile);
        config.transit().setTransitScheduleFile(scheduleFile);
        config.transit().setUseTransit(true);
        config.transit().setVehiclesFile(vehiclesFile);
        config.controller().setOutputDirectory(outputFile);
        config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controller().setLastIteration(50);
        config.controller().setWriteEventsInterval(50);

        config.changeMode().setModes(new String[] {"car","pt"});

        config.scoring().setPerforming_utils_hr(75);    // Unit time value per capita in Nanjing
        config.scoring().setMarginalUtilityOfMoney(1);   // The marginal utility of money. Positive.
        config.scoring().setUtilityOfLineSwitch(-2);
        config.scoring().setEarlyDeparture_utils_hr(0);
        config.scoring().setLateArrival_utils_hr(0);
        ScoringConfigGroup.ModeParams ptParams = new ScoringConfigGroup.ModeParams("pt");
        ptParams.setConstant(-2.5);   // "[utils] mode-specific constant. Normally per trip, but that is probably buggy for multi-leg trips."
        ptParams.setMarginalUtilityOfDistance(0);   // [unit / m]  the marginal utility of distance.
        ptParams.setMarginalUtilityOfTraveling(0);   // [unit / hr] the direct marginal utility of time spent travelling by mode.
        ptParams.setMonetaryDistanceRate(0);  // "[money / m] conversion of distance into money. Normally negative."
        ptParams.setDailyUtilityConstant(0);  // [unit / day]
        ptParams.setDailyMonetaryConstant(0);   // [money / day]
        ptParams.setMarginalUtilityOfTraveling(-6);  // 降低pt时间惩罚（原-15过大），使pt和car效用更均衡
        ScoringConfigGroup.ModeParams carParmas = new ScoringConfigGroup.ModeParams("car");

        carParmas.setConstant(-10);  // 降低car模式固定惩罚（原-80过度惩罚car，导致模式分担率失真）

        carParmas.setMarginalUtilityOfDistance(0);
        carParmas.setMarginalUtilityOfTraveling(0);
        carParmas.setMonetaryDistanceRate(-0.00056);
        carParmas.setDailyMonetaryConstant(0);
        carParmas.setDailyUtilityConstant(0);

        config.scoring().addParameterSet(ptParams);
        config.scoring().addParameterSet(carParmas);

        // 重新设置步行速度为 6 km/h（1.6667 m/s），直线距离修正系数为 1
        RoutingConfigGroup.TeleportedModeParams walkParams = (RoutingConfigGroup.TeleportedModeParams)
                config.routing().getModeRoutingParams().get("walk");
        walkParams.setTeleportedModeSpeed(1.6666666666666667);  // 6 km/h = 1.6667 m/s
        walkParams.setBeelineDistanceFactor(1.3);  // 步行绕行系数（原1.0导致pt步行距离被低估）

        config.counts().setCountsScaleFactor(100);
        // 设置网络容量系数为0.3，以适配抽样Agent产生的真实路况
        config.qsim().setFlowCapFactor(0.3);
        config.qsim().setStorageCapFactor(1); 

        // === 策略权重覆盖（策略D：提高SubtourModeChoice权重促进模式切换）===
        // 遍历已有策略，修改权重
        for (ReplanningConfigGroup.StrategySettings ss : config.replanning().getStrategySettings()) {
            switch (ss.getStrategyName()) {
                case "SelectExpBeta":
                    ss.setWeight(0.5);   // 0.7→0.5
                    break;
                case "SubtourModeChoice":
                    ss.setWeight(0.3);   // 0.1→0.3 提高模式切换权重
                    break;
                case "ReRoute":
                    ss.setWeight(0.1);   // 保持不变
                    break;
                case "TimeAllocationMutator_ReRoute":
                    ss.setWeight(0.1);   // 保持不变
                    break;
            }
        }
        config.replanning().setMaxAgentPlanMemorySize(5);

        config.global().setNumberOfThreads(12);  // innovative strategies. using the number of available cores.
        config.qsim().setNumberOfThreads(8);  // parallel qsim.
        config.eventsManager().setNumberOfThreads(6);  // event handling.

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Controler controler = new Controler(scenario);

        // possibly modify controler here
//        controler.addControlerListener(new MyControlerListener());
//        controler.addOverridingModule(new OTFVisLiveModule());

        controler.run();
    }

}
