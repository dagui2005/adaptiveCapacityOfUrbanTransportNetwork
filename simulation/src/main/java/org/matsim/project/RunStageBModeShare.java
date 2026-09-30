package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * STAGE B runner -- demand / mode-share calibration.
 *
 * <p><b>Single-variable contract.</b> The scoring / replanning / qsim block in
 * {@link #main} is a VERBATIM copy of the block in {@code RunMatsimBaseline}
 * (the class that produced the OLD Layer-5b baseline in
 * {@code D:\Luan\2025-09\MATSim\guangzhoubaselineOutput}). On top of that
 * verbatim block this runner adds FOUR optional single-variable knobs -- pt.mut
 * (A2), car.mut (A3), pt.constant (A4) and brainExpBeta (A5) -- plus the
 * transit supply, all arriving through the command line:
 *
 * <pre>
 * RunStageBModeShare &lt;configFile&gt; &lt;networkFile&gt; &lt;plansFile|none&gt;
 *                    &lt;scheduleFile&gt; &lt;vehiclesFile&gt; &lt;outputDir&gt; &lt;lastIteration&gt;
 *                    [ptMut] [carMut] [ptConstant] [brainExpBeta]
 * </pre>
 *
 * <p>The four bracketed arguments are the Stage-B calibration knobs (A2..A5).
 * Exactly ONE of them may differ from its A1 baseline in any given arm.
 *
 * <p><b>Deliberate differences from {@code RunMiniTest}</b> (which is a topology
 * gate, not a demand runner):
 * <ul>
 *   <li>threads are NOT pinned to 4 -- the OLD baseline used 12 / 8 / 6;</li>
 *   <li>{@code createGraphs} is left ON, so {@code modestats.csv},
 *       {@code scorestats.csv}, {@code traveldistancestats.csv} and the
 *       per-iteration {@code legHistogram} files are written (that is how the
 *       OLD baseline produced its mode-share trajectory);</li>
 *   <li>{@code writeTripsInterval = 1}, so per-iteration {@code trips.csv.gz}
 *       gives trip duration / distance / mode for every iteration.</li>
 * </ul>
 *
 * <p><b>What this class must never do.</b> It does not touch
 * {@code BusNetworkIntegrator}, {@code TransitBuilder}, {@code RunMatsimBaseline}
 * or {@code RunMiniTest}, and it re-computes no transit path: the network,
 * schedule and vehicles come in as files that were already frozen and audited
 * by {@code out/production2418/} + {@code verify_production.py}.
 */
public class RunStageBModeShare {

    /** Freeze marker: which code produced the frozen scoring block. */
    private static final String SCORING_BLOCK_SOURCE = "RunMatsimBaseline.main (verbatim)";

    /**
     * A1's value of {@code pt.marginalUtilityOfTraveling}.  The optional 8th
     * command-line argument exists so Stage-B calibration (A2) can move this ONE
     * number; omitting it reproduces A1 bit-for-bit.
     */
    private static final double PT_MUT_A1_BASELINE = -6.0;

    /**
     * A1's value of {@code car.marginalUtilityOfTraveling}.  The optional 9th
     * command-line argument exists so Stage-B calibration (A3) can move this ONE
     * number; omitting it reproduces A1 bit-for-bit.
     */
    private static final double CAR_MUT_A1_BASELINE = 0.0;

    /**
     * A1's value of {@code pt.constant}.  The optional 10th command-line argument
     * exists so Stage-B calibration (A4) can move this ONE number; omitting it
     * reproduces A1 bit-for-bit.
     */
    private static final double PT_CONST_A1_BASELINE = -2.5;

    /**
     * A1's value of {@code BrainExpBeta} -- the logit SCALE that turns utility
     * differences into plan-selection probabilities.  It adds no utility of its
     * own; it only decides how strongly an existing utility difference is
     * amplified.  The optional 11th command-line argument exists so Stage-B
     * calibration (A5) can move this ONE number; omitting it reproduces A1
     * bit-for-bit, because {@code stageB_config.xml} already carries 1.0.
     */
    private static final double BRAIN_EXP_BETA_A1_BASELINE = 1.0;

    /**
     * A1's value of {@code subtourModeChoice.probaForRandomSingleTripMode}.
     * The optional 12th command-line argument exists so Stage-B M2 can move this
     * ONE number; omitting it reproduces A1 bit-for-bit, because
     * {@code stageB_config.xml} does not carry the parameter and the MATSim
     * default is 0.0.
     *
     * <p>Source-level fact (MATSim 2026.0-2025w38,
     * {@code ChooseRandomLegModeForSubtour}): the single-trip channel is only
     * constructed when the number of NON-chain-based modes is >= 2.
     * With {@code modes={car,pt}} and {@code chainBasedModes={car}} that count is
     * 1, so this knob is READ but INERT -- and, because the guard short-circuits
     * on {@code changeSingleLegMode != null} before {@code rng.nextDouble()}, it
     * does not even advance the RNG stream.  M2 exists to test exactly that.
     */
    private static final double SINGLE_TRIP_PROBA_A1_BASELINE = 0.0;

    /**
     * M2-2 knob: the weight of the {@code ChangeSingleTripMode} strategy.
     *
     * <p>0.0 means "do NOT register the strategy at all", which reproduces A1
     * bit-for-bit.  Anything > 0 registers the strategy, which is the ONLY
     * mode-choice strategy that acts on SINGLE trips: it needs no closed
     * subtour (TripsToLegs -&gt; ChangeSingleLegMode -&gt; ReRoute) and reads
     * {@code config.changeMode()}, already {car, pt} here.  SubtourModeChoice
     * is left untouched, so this is a clean single-variable mechanism test.
     */
    private static final double CST_WEIGHT_A1_BASELINE = 0.0;

    /**
     * M3 knob: the {@code disableAfterIteration} of the
     * {@code ChangeSingleTripMode} entry.  -1 == the MATSim default == no
     * per-strategy disable, so the strategy then dies at the global
     * innovation cutoff instead (it.46 in a 50-iteration run).
     *
     * <p>Setting 0 makes the strategy act ONLY in the replanning pass of
     * iteration 0 (the pass that builds it.1's plans) and be set to weight
     * 0.0 from iteration 1 on -- verified in StrategyManager: it calls
     * {@code addChangeRequest(maxIter + 1, strategy, subpop, 0.0)}.  With a
     * large weight this is the M3 one-shot candidate-injection protocol:
     * every agent is handed the alternative-mode plan once, and from it.1
     * the remaining weights are numerically identical to A1, so the settled
     * share is decided by the ordinary selectors rather than by a
     * re-normalised strategy budget.
     */
    private static final int CST_DISABLE_AFTER_A1_BASELINE = -1;

    /**
     * DIAGNOSTIC-ONLY knob: {@code controller.writePlansInterval}.
     * 0 == the A1 value, i.e. plans are never dumped.  A value &gt; 0 makes
     * MATSim dump the whole population -- every plan of every person,
     * including the {@code selected} flag -- to
     * {@code ITERS/it.N/N.plans.xml.gz} (plus it.0/it.1, which MATSim always
     * writes through {@code writePlansUntilIteration}).
     *
     * <p>This is the only way to observe the candidate-plan vs chosen-plan
     * joint distribution, i.e. P(PT selected | PT candidate).  It writes
     * files and consumes no random numbers, so it cannot influence the
     * dynamics -- which the smoke gate proves by bit-identity against A1.
     */
    private static final int WRITE_PLANS_INTERVAL_A1_BASELINE = 0;

    public static void main(String[] args) {
        if (args.length < 7) {
            System.err.println("usage: RunStageBModeShare <config> <network> <plans|none> "
                    + "<schedule> <vehicles> <outDir> <lastIteration> "
                    + "[ptMarginalUtilityOfTraveling | default -6.0 = A1 baseline] "
                    + "[carMarginalUtilityOfTraveling | default 0.0 = A1 baseline] "
                    + "[ptConstant | default -2.5 = A1 baseline] "
                    + "[brainExpBeta | default 1.0 = A1 baseline] "
                    + "[singleTripProba | default 0.0 = A1 baseline] "
                    + "[changeSingleTripModeWeight | default 0.0 = A1 baseline] "
                    + "[cstDisableAfterIteration | default -1 = A1 baseline] "
                    + "[writePlansInterval | default 0 = A1 baseline]");
            System.exit(2);
        }
        String configFile   = args[0];
        String networkFile  = args[1];
        String plansFile    = args[2];
        String scheduleFile = args[3];
        String vehiclesFile = args[4];
        String outputFile   = args[5];
        int    lastIter     = Integer.parseInt(args[6]);
        // ---- Stage-B single-variable knob (OPTIONAL) --------------------------
        double ptMutArg     = (args.length >= 8)
                ? Double.parseDouble(args[7]) : PT_MUT_A1_BASELINE;
        double carMutArg    = (args.length >= 9)
                ? Double.parseDouble(args[8]) : CAR_MUT_A1_BASELINE;
        double ptConstArg   = (args.length >= 10)
                ? Double.parseDouble(args[9]) : PT_CONST_A1_BASELINE;
        // ---- A5 single-variable knob (OPTIONAL, 11th arg) --------------------
        double brainExpBetaArg = (args.length >= 11)
                ? Double.parseDouble(args[10]) : BRAIN_EXP_BETA_A1_BASELINE;
        // ---- M2 single-variable knob (OPTIONAL, 12th arg) --------------------
        double singleTripProbaArg = (args.length >= 12)
                ? Double.parseDouble(args[11]) : SINGLE_TRIP_PROBA_A1_BASELINE;
        // ---- M2-2 single-variable knob (OPTIONAL, 13th arg) ------------------
        double cstWeightArg = (args.length >= 13)
                ? Double.parseDouble(args[12]) : CST_WEIGHT_A1_BASELINE;
        // ---- M3 single-variable knob (OPTIONAL, 14th arg) --------------------
        int cstDisableAfterArg = (args.length >= 14)
                ? Integer.parseInt(args[13]) : CST_DISABLE_AFTER_A1_BASELINE;
        // ---- M3 diagnostic output knob (OPTIONAL, 15th arg) ------------------
        int writePlansIntervalArg = (args.length >= 15)
                ? Integer.parseInt(args[14]) : WRITE_PLANS_INTERVAL_A1_BASELINE;

        Config config = ConfigUtils.loadConfig(configFile);

        long seed = 179;
        config.global().setRandomSeed(seed);

        config.network().setInputFile(networkFile);
        config.plans().setInputFile("none".equals(plansFile) ? null : plansFile);
        config.transit().setTransitScheduleFile(scheduleFile);
        config.transit().setUseTransit(true);
        config.transit().setUsingTransitInMobsim(true);
        config.transit().setVehiclesFile(vehiclesFile);
        config.controller().setOutputDirectory(outputFile);
        config.controller().setOverwriteFileSetting(
                OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controller().setLastIteration(lastIter);

        // ---- output policy (OUTPUT ONLY -- cannot influence dynamics) ----------
        // The OLD baseline used writeEventsInterval=50 (events at it.0 / it.50).
        // Stage B keeps that intent -- events only at the last iteration -- but
        // sets it to 0 because trips.csv.gz (per iteration) already carries
        // travel_time / travel_distance / mode per trip, which is what the
        // demand analysis needs, and the event stream is ~1.5 GB per dump.
        config.controller().setWriteEventsInterval(0);
        config.controller().setWriteTripsInterval(1);
        // Diagnostic only (writes files; consumes no random numbers).
        // 0 == A1, i.e. no plans dump at all.
        config.controller().setWritePlansInterval(writePlansIntervalArg);
        config.controller().setCreateGraphs(true);
        config.controller().setDumpDataAtEnd(false);
        config.controller().setWriteSnapshotsInterval(0);

        config.changeMode().setModes(new String[]{"car", "pt"});

        // ---- M2 knob (config-only; NO scoring parameter is touched) -----------
        // How often SubtourModeChoice picks a random SINGLE trip instead of a
        // subtour.  Read by ChooseRandomLegModeForSubtour, but the channel is
        // built only if (modes - chainBasedModes).length >= 2; here that is
        // {pt}.length == 1, so the value lands in the config and changes nothing.
        config.subtourModeChoice().setProbaForRandomSingleTripMode(singleTripProbaArg);

        // ---- M2-2 knob: register ChangeSingleTripMode (config-only) -----------
        // The only mode-choice channel that reaches single-trip persons.  It
        // consumes config.changeMode() (already {car,pt} above), NOT
        // subtourModeChoice, so SubtourModeChoice's semantics are unchanged.
        // weight 0.0 == not registered == A1 bit-for-bit.
        if (cstWeightArg > 0.0) {
            ReplanningConfigGroup.StrategySettings cst = new ReplanningConfigGroup.StrategySettings();
            cst.setStrategyName("ChangeSingleTripMode");
            cst.setWeight(cstWeightArg);
            cst.setDisableAfter(cstDisableAfterArg);
            config.replanning().addStrategySettings(cst);
        }

        // ==================== BEGIN verbatim RunMatsimBaseline block ====================
        config.scoring().setPerforming_utils_hr(75);    // Unit time value per capita in Nanjing
        config.scoring().setMarginalUtilityOfMoney(1);   // The marginal utility of money. Positive.
        config.scoring().setUtilityOfLineSwitch(-2);
        config.scoring().setEarlyDeparture_utils_hr(0);
        config.scoring().setLateArrival_utils_hr(0);
        ScoringConfigGroup.ModeParams ptParams = new ScoringConfigGroup.ModeParams("pt");
        ptParams.setConstant(ptConstArg);  // A4 knob (default -2.5 == A1)
        ptParams.setMarginalUtilityOfDistance(0);
        ptParams.setMarginalUtilityOfTraveling(0);
        ptParams.setMonetaryDistanceRate(0);
        ptParams.setDailyUtilityConstant(0);
        ptParams.setDailyMonetaryConstant(0);
        ptParams.setMarginalUtilityOfTraveling(ptMutArg);  // A2 knob (default -6.0 == A1)
        ScoringConfigGroup.ModeParams carParmas = new ScoringConfigGroup.ModeParams("car");
        carParmas.setConstant(-10);
        carParmas.setMarginalUtilityOfDistance(0);
        carParmas.setMarginalUtilityOfTraveling(0);
        carParmas.setMonetaryDistanceRate(-0.00056);
        carParmas.setMarginalUtilityOfTraveling(carMutArg);  // A3 knob (default 0.0 == A1)
        carParmas.setDailyMonetaryConstant(0);
        carParmas.setDailyUtilityConstant(0);
        config.scoring().addParameterSet(ptParams);
        config.scoring().addParameterSet(carParmas);
        // A5 knob (default 1.0 == A1 == what stageB_config.xml already holds).
        // BrainExpBeta rescales EVERY utility difference at once, so it must
        // never be moved in the same arm as pt.mut / car.mut / pt.constant.
        config.scoring().setBrainExpBeta(brainExpBetaArg);

        RoutingConfigGroup.TeleportedModeParams walkParams = (RoutingConfigGroup.TeleportedModeParams)
                config.routing().getModeRoutingParams().get("walk");
        walkParams.setTeleportedModeSpeed(1.6666666666666667);  // 6 km/h
        walkParams.setBeelineDistanceFactor(1.3);

        config.counts().setCountsScaleFactor(100);
        config.qsim().setFlowCapFactor(0.3);
        config.qsim().setStorageCapFactor(1);

        for (ReplanningConfigGroup.StrategySettings ss : config.replanning().getStrategySettings()) {
            switch (ss.getStrategyName()) {
                case "SelectExpBeta":                ss.setWeight(0.5); break;
                case "SubtourModeChoice":            ss.setWeight(0.3); break;
                case "ReRoute":                      ss.setWeight(0.1); break;
                case "TimeAllocationMutator_ReRoute": ss.setWeight(0.1); break;
                default: break;
            }
        }
        config.replanning().setMaxAgentPlanMemorySize(5);

        config.global().setNumberOfThreads(12);
        config.qsim().setNumberOfThreads(8);
        config.eventsManager().setNumberOfThreads(6);
        // ===================== END verbatim RunMatsimBaseline block =====================

        System.out.println("=== RunStageBModeShare ===");
        System.out.println("scoring block source : " + SCORING_BLOCK_SOURCE);
        System.out.println("config     : " + configFile);
        System.out.println("network    : " + networkFile);
        System.out.println("plans      : " + plansFile);
        System.out.println("schedule   : " + scheduleFile);
        System.out.println("vehicles   : " + vehiclesFile);
        System.out.println("output     : " + outputFile);
        System.out.println("lastIter   : " + lastIter);
        System.out.println("seed       : " + seed);
        System.out.println("pt.mut knob: " + ptMutArg
                + "   (A1 baseline " + PT_MUT_A1_BASELINE + ")");
        System.out.println("car.mut knob: " + carMutArg
                + "   (A1 baseline " + CAR_MUT_A1_BASELINE + ")");
        System.out.println("pt.const knob: " + ptConstArg
                + "   (A1 baseline " + PT_CONST_A1_BASELINE + ")");
        System.out.println("brainExpBeta: " + brainExpBetaArg
                + "   (A1 baseline " + BRAIN_EXP_BETA_A1_BASELINE + ")");
        System.out.println("singleTripProba: " + singleTripProbaArg
                + "   (A1 baseline " + SINGLE_TRIP_PROBA_A1_BASELINE + ")");
        System.out.println("smc modes : " + java.util.Arrays.toString(
                config.subtourModeChoice().getModes())
                + "   chainBased : " + java.util.Arrays.toString(
                config.subtourModeChoice().getChainBasedModes())
                + "   => non-chain-based count = " 
                + (config.subtourModeChoice().getModes().length
                   - countShared(config.subtourModeChoice().getModes(),
                               config.subtourModeChoice().getChainBasedModes())));
        System.out.println("cstWeight knob: " + cstWeightArg
                + "   (A1 baseline " + CST_WEIGHT_A1_BASELINE + ")");
        System.out.println("cstDisableAfter: " + cstDisableAfterArg
                + "   (A1 baseline " + CST_DISABLE_AFTER_A1_BASELINE + ")");
        System.out.println("writePlansInterval: " + writePlansIntervalArg
                + "   (A1 baseline " + WRITE_PLANS_INTERVAL_A1_BASELINE + ")");
        System.out.println("changeMode : modes=" + java.util.Arrays.toString(
                config.changeMode().getModes())
                + "   ignoreCarAvailability=" + config.changeMode().getIgnoreCarAvailability());
        System.out.println("strategies : " + strategySummary(config));
        System.out.println("flowCapF   : " + config.qsim().getFlowCapFactor()
                + "   storageCapF : " + config.qsim().getStorageCapFactor());
        System.out.println("pt  params : const=" + config.scoring().getOrCreateModeParams("pt").getConstant()
                + " mut=" + config.scoring().getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()
                + " mdr=" + config.scoring().getOrCreateModeParams("pt").getMonetaryDistanceRate());
        System.out.println("car params : const=" + config.scoring().getOrCreateModeParams("car").getConstant()
                + " mut=" + config.scoring().getOrCreateModeParams("car").getMarginalUtilityOfTraveling()
                + " mdr=" + config.scoring().getOrCreateModeParams("car").getMonetaryDistanceRate());

        Scenario scenario = ScenarioUtils.loadScenario(config);

        int nPersons = scenario.getPopulation().getPersons().size();
        int nLinks   = scenario.getNetwork().getLinks().size();
        int nLines   = scenario.getTransitSchedule().getTransitLines().size();
        System.out.println("loaded persons      = " + nPersons);
        System.out.println("loaded network      = " + nLinks + " links");
        System.out.println("loaded transitLines = " + nLines);

        if (nPersons == 0 && !"none".equals(plansFile)) {
            System.err.println("FATAL: plansFile was given but 0 persons loaded -> aborting");
            System.exit(3);
        }

        Controler controler = new Controler(scenario);

        // CAUTION (measured): OutputDirectoryHierarchy applies
        // `deleteDirectoryIfExists` at the START of run(), so anything written into
        // the output directory BEFORE run() is wiped.  The first version of this
        // audit fingerprint did exactly that and vanished.  Write it AFTER the run.
        controler.run();

        writeAuditFingerprint(config, args, outputFile);
        System.out.println("=== RunStageBModeShare DONE ===");
    }

    /**
     * The weight carried by the {@code ChangeSingleTripMode} strategy in the
     * EFFECTIVE replanning list, or 0.0 when that strategy was not registered.
     * Read back from the config so run_args.txt records what the run really
     * used, not what argv asked for.
     */
    private static double cstWeightOf(Config config) {
        for (ReplanningConfigGroup.StrategySettings ss : config.replanning().getStrategySettings()) {
            if ("ChangeSingleTripMode".equals(ss.getStrategyName())) {
                return ss.getWeight();
            }
        }
        return 0.0;
    }

    /**
     * The {@code disableAfterIteration} carried by the
     * {@code ChangeSingleTripMode} entry in the EFFECTIVE replanning list,
     * or -1 when that strategy was not registered.
     */
    private static int cstDisableAfterOf(Config config) {
        for (ReplanningConfigGroup.StrategySettings ss : config.replanning().getStrategySettings()) {
            if ("ChangeSingleTripMode".equals(ss.getStrategyName())) {
                return ss.getDisableAfter();
            }
        }
        return -1;
    }

    /** "name=weight, name=weight, ..." over the effective replanning strategy list. */
    private static String strategySummary(Config config) {
        StringBuilder sb = new StringBuilder();
        for (ReplanningConfigGroup.StrategySettings ss : config.replanning().getStrategySettings()) {
            if (sb.length() > 0) { sb.append(", "); }
            sb.append(ss.getStrategyName()).append('=').append(ss.getWeight());
        }
        return sb.toString();
    }

    /** How many entries of {@code a} also occur in {@code b} (set semantics). */
    private static int countShared(String[] a, String[] b) {
        int n = 0;
        for (String x : a) {
            for (String y : b) {
                if (x.equals(y)) { n++; break; }
            }
        }
        return n;
    }

    /** Writes the effective config + argv into the (already created) output dir. */
    private static void writeAuditFingerprint(Config config, String[] args, String outputFile) {
        java.io.File dir = new java.io.File(outputFile);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("FATAL: cannot create output dir " + outputFile);
            System.exit(5);
        }
        org.matsim.core.config.ConfigUtils.writeConfig(
                config, outputFile + "/effective_config.xml");
        try (java.io.PrintWriter w = new java.io.PrintWriter(
                new java.io.File(outputFile + "/run_args.txt"), "UTF-8")) {
            w.println("argv   = " + String.join(" ", args));
            w.println("pt.mut = " + config.scoring().getOrCreateModeParams("pt")
                    .getMarginalUtilityOfTraveling());
            w.println("car.mut = " + config.scoring().getOrCreateModeParams("car")
                    .getMarginalUtilityOfTraveling());
            w.println("pt.const = " + config.scoring().getOrCreateModeParams("pt")
                    .getConstant());
            w.println("car.const = " + config.scoring().getOrCreateModeParams("car")
                    .getConstant());
            w.println("brainExpBeta = " + config.scoring().getBrainExpBeta());
            w.println("singleTripProba = "
                    + config.subtourModeChoice().getProbaForRandomSingleTripMode());
            w.println("smc.modes = " + String.join(",",
                    config.subtourModeChoice().getModes()));
            w.println("smc.chainBasedModes = " + String.join(",",
                    config.subtourModeChoice().getChainBasedModes()));
            w.println("cstWeight = " + cstWeightOf(config));
            w.println("cstDisableAfter = " + cstDisableAfterOf(config));
            w.println("writePlansInterval = " + config.controller().getWritePlansInterval());
            w.println("strategies = " + strategySummary(config));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("wrote      : " + outputFile
                + "/effective_config.xml + run_args.txt");
    }
}
