package org.matsim.population;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author: Chunhong li
 * @date: 2022年04月15日 16:59
 * @Description: 删除指定的需求。
 * 1. “deletePerson” “savePerson”   本程序目前可用于删除含有特定网格 Id 的需求，或者保存含有特定网格 Id 的需求，这个删除的是 agent.
 * 2. “deleteModes”  删除指定的需求，添加“删除指定模式的需求”，删除 Plan.
 * 3. “deleteUnselectedPlans”   删除未被选中的 plan，这个删除的是 plan. 一个 agent 可能有很多个 plans, 但其中有且只有一个 plan 是 selected。
 * 4. “deleteAgentsNoPlan”    agent 删除： 没有一个 selected plan 的 agent 的删除
 * 5. “minedPopulation”  精簡 popluation，只保留 agent 的属性，活动的位置，类型和结束时间（最后一个活动没有结束时间），trip中主要 leg 的模式
 * 6. "deleteAgentsNoActivity"  删除没有活动的 agent。一个 agent 至少要有一个 plan,一个plan 至少要有一个 activity.
 * *
 */
public class CleanPopulationUtils {

    public static Population deletePerson(Population pop, List<String> invalidGridIds) {
        /* 根据无效的网格 Id，清理 population*/
        List<Id<Person>> invalidPersonIds = new ArrayList<>();

        if (invalidGridIds == null) {
            return pop;
        }

        for (Person person : pop.getPersons().values()
        ) {
            String[] gridIds = person.getId().toString().split("X");
            if (invalidGridIds.contains(gridIds[0]) || invalidGridIds.contains(gridIds[1])) {
                invalidPersonIds.add(person.getId());
            }
        }

        for (Id<Person> personId : invalidPersonIds
        ) {
            pop.removePerson(personId);
        }

        return pop;
    }

    public static Population savePerson(Population pop, List<String> validGridIds) {
        /* 根据有效的网格 Id，清理 population*/
        List<Id<Person>> invalidPersonIds = new ArrayList<>();

        if (validGridIds == null) {
            System.out.println("............ validGridIds is null ..............");
            for (Person person : pop.getPersons().values()
            ) {
                invalidPersonIds.add(person.getId());
            }
        }

        for (Person person : pop.getPersons().values()
        ) {
            String[] gridIds = person.getId().toString().split("X");
            if (validGridIds.contains(gridIds[0]) && validGridIds.contains(gridIds[1])) {
                continue;
            } else {
                invalidPersonIds.add(person.getId());
            }
        }

        for (Id<Person> personId : invalidPersonIds
        ) {
            pop.removePerson(personId);
        }
        return pop;
    }

    public static Population deleteModes(Population pop, String validMode) {
        for (Person person : pop.getPersons().values()
        ) {
            Plan selectedPlan = person.getSelectedPlan();

//            count all legs of which modes == "invalidMode" for the selected plan.
            List<Leg> invalidLegs = new ArrayList<>();
//            if there are invalid legs in the selected plan.
            boolean hasInvalidLeg = false;
            for (PlanElement planElement : selectedPlan.getPlanElements()) {
                if (planElement instanceof Leg) {   // 用instanceof运算符可以用来判断某对象对应的类是否实现了指定接口.
                    if (!((Leg) planElement).getMode().equals(validMode)) {
                        invalidLegs.add((Leg) planElement);
                        hasInvalidLeg = true;
                    }

                }
            }

            if (hasInvalidLeg) {
                person.removePlan(selectedPlan);
            }

        }

        return pop;
    }

    public static Population deleteUnselectedPlans(Population pop) {
        for (Person person : pop.getPersons().values()
        ) {
//            count all unselected plans for each (the) person.
            List<Plan> unselectedPlans = new ArrayList<>();
            for (Plan plan : person.getPlans()) {
                if (!person.getSelectedPlan().equals(plan)) {
                    unselectedPlans.add(plan);
                }
            }
//            remove unselected plans for each (the) person.
            if (unselectedPlans != null) {
                for (Plan plan : unselectedPlans) {
                    person.removePlan(plan);
                }
            }
        }
        return pop;
    }

    //    delete agents without plans.
    public static Population deleteAgentsNoPlan(Population pop) {
//        count all persons without plans.
        List<Person> invalidPersons = new ArrayList<>();
        for (Person person : pop.getPersons().values()
        ) {
            if (person.getPlans().size() == 0) {
                invalidPersons.add(person);
            }
        }

//       remove unselected plans for each (the) person.
        for (Person person : invalidPersons
        ) {
            pop.removePerson(person.getId());
        }

        return pop;
    }

    //  delete agents without Activity
    public static void deleteAgentNoActivity(Population pop) {
//        count the agents
        List<Person> invalidPersons = new ArrayList<>();
        for (Person person : pop.getPersons().values()) {
            Plan selectedPlan = person.getSelectedPlan();
            if (TripStructureUtils.getTrips(selectedPlan).size() == 0) {
                invalidPersons.add(person);
            }
        }

//        remove these invalid persons
        for (Person invalidPerson : invalidPersons) {
            pop.removePerson(invalidPerson.getId());
        }
    }

    /*
    Extract a minimum MATSim plan file.
    only save all useful information, such as activity location, legs modes, departure time.
    * */
    public static Population minedPopulation(Population pop) {
//        new population
        Population minedPop = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        PopulationFactory populationFactory = minedPop.getFactory();

        for (Person person : pop.getPersons().values()) {
//            new person and plan
            Person minedPerson = populationFactory.createPerson(person.getId());
            Plan minedPlan = populationFactory.createPlan();

//            add attributes to agents.
            boolean isPerson = true;   // assuming the agent is a person in default.
            if (!person.getAttributes().isEmpty()) {
                for (String attrName : person.getAttributes().getAsMap().keySet()) {
                    minedPerson.getAttributes().putAttribute(attrName, person.getAttributes().getAttribute(attrName));
                }

//                if (person.getAttributes().getAsMap().containsKey("subpopulation")) {
//                    if (person.getAttributes().getAsMap().get("subpopulation") != "person") {
//                        isPerson = false;     // Todo: hamburg scenario has "person" and "commercial"; los angele scenario has "person" and "freight".
//                    }
//                }
            }


//            NOTE: we should use trip not leg!
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());

            if (trips.size() == 0) {
//                if the selected plan has only one activity and has no trip, we don't mine it.
                continue;
            }

            int k = 0; // count the trip num.
            for (TripStructureUtils.Trip trip : trips) {
                // original activity.
                Activity originActivity = trip.getOriginActivity();
                Activity destinationActivity = trip.getDestinationActivity();
                ListIterator<Leg> legListIterator = trip.getLegsOnly().listIterator();

                // add necessary information for origin activity.
                Activity minedOriginActivity = populationFactory.createActivityFromCoord(originActivity.getType(), originActivity.getCoord());

                // note: 20230311 I find there is still an error that "the endTime of originActivity is negative (no defined)". Because it may have a max duration.
                if (originActivity.getEndTime().isDefined()) {
                    minedOriginActivity.setEndTime(originActivity.getEndTime().seconds());
                } else if (originActivity.getMaximumDuration().isDefined()) {
                    minedOriginActivity.setMaximumDuration(originActivity.getMaximumDuration().seconds());
                } else {
                    System.out.println("Error: Agent " + person.getId() + " has an (not last) activity without end time or max duration!!");
                    System.exit(0);
                }

                minedPlan.addActivity(minedOriginActivity);

                // add necessary information for main leg.
                // 20230309 new method to identify the mainMode of the trip (Routing mode of the first leg)
                String mainMode = TripStructureUtils.identifyMainMode(trip.getTripElements());
                minedPlan.addLeg(populationFactory.createLeg(mainMode));
                // 20230309 new method to identify the mainMode of the trip.
//                while (legListIterator.hasNext()) {
//                    String thisLegMode = legListIterator.next().getMode();
//                    if ((!thisLegMode.equals("walk") && trip.getLegsOnly().size() > 1)) {
//                        minedPlan.addLeg(populationFactory.createLeg(thisLegMode));  // NOTE: MATSim can't export the main mode in this trip. So we select the first mode but walk mode as the main mode of this trip.
//                        break;
//                    }
//                    if (trip.getLegsOnly().size() == 1){
//                        minedPlan.addLeg(populationFactory.createLeg(thisLegMode));  // NOTE: However, if this trip has only one leg, we have to select the only leg mode as the main mode. If the trip has multiplex legs, it will have other leg mode other than walk.
//                    }
//                }

                // add necessary information for the last destination activity.
                if (k == trips.size() - 1) {
                    // NOTE: this destination in this trip is the origin of the next trip. But if this is the last trip, we must add the destination activity.
                    Activity minedDestinationActivity = populationFactory.createActivityFromCoord(destinationActivity.getType(), destinationActivity.getCoord());
//                    minedDestinationActivity.setEndTime(destinationActivity.getEndTime().seconds()); // Be careful about the fact that the last activity has no end time (negative infinity)!!
                    minedPlan.addActivity(minedDestinationActivity);
                }
                k += 1;
            }

//            add plan to person, add person to population
            minedPerson.addPlan(minedPlan);
            minedPop.addPerson(minedPerson);
        }
        return minedPop;
    }

    //    delete agents with invalid ids.
    public static void main(String[] args) {
//        double thereshold = 0.0;
        double[] thresholds = new double[]{3.0};
        for (double threshold : thresholds) {
            String preprocessFolder = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【分析】影响评估\\洪水场景\\【分析】功能破坏相关分析\\需求破坏_预处理\\";
//            TODO: change input file
//            for bi-mode
//            String inPopulationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\双模式-无洪水\\trips\\road.population.xml";
//            String outPopulationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\双模式-有洪水\\trips\\";
//            for sensitivity analysis  (population fraction)
//            String inPopulationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\sensitivity analysis\\demand.noex20.0.27.50pct.xml.gz";
//            String outPopulationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\sensitivity analysis\\trips.T"+ threshold + ".50pct.xml.gz";
//            for sensitivity analysis  (beta in travel distance function of Marta's work)
            String inPopulationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\sensitivity analysis\\demand.noex20.0.27.100pct.beta1.9.xml.gz";
            String outPopulationPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\sensitivity analysis\\trips.T"+ threshold + ".beta1.9.xml.gz";

            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new PopulationReader(scenario).readFile(inPopulationPath);
            int tripsNum = (scenario.getPopulation().getPersons().size() * 2);
            try {
//            1. 直接淹没的网格
                BufferedReader reader1 = new BufferedReader(new FileReader(preprocessFolder + "失效网格.淹没.rp100y.thereshold" + threshold + ".csv"));  //换成你的文件名
                reader1.readLine();  //第一行信息，为标题信息，不用，如果需要，注释掉
                String line = null;
                List<String> DirectFailureGrids = new ArrayList<>();
                while ((line = reader1.readLine()) != null) {
                    DirectFailureGrids.add(line.split(",")[1]);  // 失效网格 Id，第一个元素是 pandas 生成的无效列
                }
                Population populationAfterDirect = deletePerson(scenario.getPopulation(), DirectFailureGrids);
                int tripsNumAfterDirect = (populationAfterDirect.getPersons().size() * 2);

//            2. 间接不能通行的 网格对
                BufferedReader reader2 = new BufferedReader(new FileReader(preprocessFolder + "失效网格对.通行.rp100y.thereshold" + threshold + ".csv"));  //换成你的文件名
                reader2.readLine();  //第一行信息，为标题信息，不用，如果需要，注释掉
                String line2 = null;
                Population populationAfterIndirect = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation();
                while ((line2 = reader2.readLine()) != null) {
                    // 每一行代表一个 subgraph
                    List<String> subgraphNodes = new ArrayList<>();
                    for (int i = 0; i < line2.split(",").length; i++) {
                        if (i != 0) { // 第一列元素为空元素: dataframe 生成 csv 的 index
                            subgraphNodes.add(line2.split(",")[i].split("'")[1]);   // 网格 Id. 20220416发现 validGridIds 中每个元素都形如 "'111'"，我们把内部的''去掉。 '' 表示 char，“” 表示 String，前者是基本数据类型，== equals相同，后者非基本数据类型，equals可以，==会检查指向的内存地址是否相同. https://blog.csdn.net/jsh306/article/details/81703934
                        }
                    }
                    Population populationSub = savePerson(populationAfterDirect, subgraphNodes);   // 取子图内部的所有 trips
                    // 将该 subgraph 的所有 person 添加到 populationAfterIndirect
                    for (Person person : populationSub.getPersons().values()) {
                        populationAfterIndirect.addPerson(person);
                    }
                }
                int tripNumAfterIndirect = (populationAfterIndirect.getPersons().size() * 2);

                new PopulationWriter(populationAfterIndirect).write(outPopulationPath);
                System.out.println("Threshold:" + threshold);
                System.out.println("初始 trips num: " + tripsNum + " 直接失效后: " + tripsNumAfterDirect + " 间接失效后: " + tripNumAfterIndirect);
            } catch (IOException fileNotFoundException) {
                fileNotFoundException.printStackTrace();
            }
        }

    }

//    // 20230217 Extract a minimal plan file from the output population in Hamburg scenario.
//    public static void main(String[] args) {
//        URL populationPath = CleanPopulationUtils.class.getResource("/hamburg/hamburg-v3.0-25pct-base.output_plans.xml.gz");
//        String outPopulationPath = "my_hamburg_25pct_plans.xml.gz";
//        Population population = PopulationUtils.readPopulation(String.valueOf(populationPath));
//        System.out.println("Agents num of baseline scenario: " + population.getPersons().size());
//        Population miniPopulation = CleanPopulationUtils.minedPopulation(population);
//        CleanPopulationUtils.deleteAgentNoActivity(miniPopulation);  // delete agents without any activity.
//        System.out.println("Agents num of baseline scenario after cleaning agents without activity: " + population.getPersons().size());
//        new PopulationWriter(miniPopulation).write(outPopulationPath);
//    }

    // 20230304 Extract a minimal plan file from the output population in Los Angeles scenario.
//    public static void main(String[] args) {
//////        TODO: input population file. Jar 包地址
////        URL populationPath = CleanPopulationUtils.class.getResource("/los angeles/los-angeles-v1.0-population-0.1pct_2019-12-09.xml.gz");
//////        TODO: input population file. cloud 绝对路径 适合输入的 population 文件超级大，不适合打入 Jar 包的情形。
//        String populationPath = "/data/home/u21125761/jobs/LABaseline10pct_202303151108.03/output/outputLABaseline10pct/output_plans.xml.gz";
////        TODO: output population file. cloud 相对路径
//        String outPopulationPath = "/data/home/u21125761/jobs/my_baseline_LA_10pct_output_plans_min.xml.gz";
//        Population population = PopulationUtils.readPopulation(String.valueOf(populationPath));
//        SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
//        System.out.println("Original population: ");
//        simpleAnalyzer.count(population);
//
//        Population miniPopulation = CleanPopulationUtils.minedPopulation(population);
//        CleanPopulationUtils.deleteAgentNoActivity(miniPopulation);  // delete agents without any activity.
//
//        System.out.println("After mining, population: ");
//        simpleAnalyzer.count(miniPopulation);
//
//        new PopulationWriter(miniPopulation).write(outPopulationPath);
//    }
//}

//    public static void main(String[] args) {
//        String inputPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\hamburg\\simulation\\baseline\\input\\hamburg-v3.0-1pct-base.plans.xml.gz";
//        String outputPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\hamburg\\simulation\\baseline\\input\\hamburg-v3.0-1pct-base.plans.mini.xml.gz";
//
//        Population population = PopulationUtils.readPopulation(inputPopPath);
//
//        Population populationMined = CleanPopulationUtils.minedPopulation(population);
//        CleanPopulationUtils.deleteAgentNoActivity(populationMined);
//        PopulationUtils.writePopulation(populationMined, outputPopPath);
//    }
}
