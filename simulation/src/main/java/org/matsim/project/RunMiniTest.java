package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * Minimal MATSim run used as a HARD GATE before the 2418-line rollout.
 *
 * <p>Question it answers: can a TransitRoute whose link sequence contains straight
 * {@code PROTOV_*} virtual links be driven by MATSim without re-triggering
 * {@code DefaultTurnAcceptanceLogic: Cannot move vehicle}?
 *
 * <p>Everything is passed on the command line so the same class runs both the B3
 * control and the PROTOV test scenario, differing only in the route geometry:
 *
 * <pre>
 * RunMiniTest &lt;configFile&gt; &lt;networkFile&gt; &lt;plansFile|none&gt;
 *             &lt;scheduleFile&gt; &lt;vehiclesFile&gt; &lt;outputDir&gt; &lt;lastIteration&gt;
 * </pre>
 */
public class RunMiniTest {

    public static void main(String[] args) {
        String configFile = args[0];
        String networkFile = args[1];
        String plansFile = args[2];
        String scheduleFile = args[3];
        String vehiclesFile = args[4];
        String outputDir = args[5];
        int lastIter = Integer.parseInt(args[6]);

        Config config = ConfigUtils.loadConfig(configFile);
        config.global().setRandomSeed(179);

        config.network().setInputFile(networkFile);
        config.plans().setInputFile("none".equals(plansFile) ? null : plansFile);
        config.transit().setTransitScheduleFile(scheduleFile);
        config.transit().setUseTransit(true);
        config.transit().setUsingTransitInMobsim(true);
        config.transit().setVehiclesFile(vehiclesFile);

        config.controller().setOutputDirectory(outputDir);
        config.controller().setOverwriteFileSetting(
                OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controller().setLastIteration(lastIter);
        config.controller().setWriteEventsInterval(lastIter);
        config.controller().setWritePlansInterval(0);
        config.controller().setCreateGraphs(false);
        config.controller().setDumpDataAtEnd(false);
        config.controller().setWriteSnapshotsInterval(0);

        config.global().setNumberOfThreads(4);
        config.qsim().setNumberOfThreads(4);
        config.eventsManager().setNumberOfThreads(2);

        System.out.println("=== RunMiniTest ===");
        System.out.println("config     : " + configFile);
        System.out.println("network    : " + networkFile);
        System.out.println("plans      : " + plansFile);
        System.out.println("schedule   : " + scheduleFile);
        System.out.println("vehicles   : " + vehiclesFile);
        System.out.println("output     : " + outputDir);
        System.out.println("lastIter   : " + lastIter);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        System.out.println("loaded persons   = " + scenario.getPopulation().getPersons().size());
        System.out.println("loaded network   = " + scenario.getNetwork().getLinks().size() + " links");
        System.out.println("loaded transitLines = " + scenario.getTransitSchedule().getTransitLines().size());

        Controler controler = new Controler(scenario);
        controler.run();
        System.out.println("=== RunMiniTest DONE ===");
    }
}
