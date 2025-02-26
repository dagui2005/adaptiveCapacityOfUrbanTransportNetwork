package org.matsim.analysis.population;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

import java.net.URL;

/**
 * @author: Chunhong li
 * @date: 2022年12月12日 11:53
 * @Description: 以基准场景随机提取的 persons 为依据，从不同的洪水场景的结果提取出特定的 person 的 trip。
 */
public class ExtractPopulationTest {
    public static void main(String[] args) {
        double threshold = 0.0;
//        normal path
//        String popFloodsPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\有洪水\\threshold" + threshold + "m\\output_plans.xml";
//        String popBaselinePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【分析】影响评估\\洪水场景\\【分析】个体角度分析\\抽样样本\\Sampled_plans_baseline.xml";
//        String sampledPopFloodsPath = "D:\\【学术】\\【h = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\有洪水\\threshold" + threshold + "m\\output_plans.xml";
//        path for super computing
        URL popFloodsPath = ExtractPopulationTest.class.getResource("/nanjing/outputPlans/output_plans_threshold" + threshold + "m.xml.gz");
        URL popBaselinePath = ExtractPopulationTest.class.getResource("/nanjing/outputPlans/Sampled_plans_baseline.xml");
        String sampledPopFloodsPath = "Sampled_plans_threshold" + threshold + "m.xml";
        Population sampledPopBaseline = PopulationUtils.readPopulation(String.valueOf(popBaselinePath));
        Population popFloods = PopulationUtils.readPopulation(String.valueOf(popFloodsPath));
        Population sampledPopFloods = run(sampledPopBaseline, popFloods);
        PopulationUtils.writePopulation(sampledPopFloods, sampledPopFloodsPath);

    }

    public static Population run(Population sampledPopBaseline, Population popFloods) {
        Config config = ConfigUtils.createConfig();
        Population sampledPopFloods = PopulationUtils.createPopulation(config);
        for (Person person : popFloods.getPersons().values()) {
            if (sampledPopBaseline.getPersons().containsValue(person)) {
                sampledPopFloods.addPerson(person);
            }
        }
        return sampledPopFloods;
    }
}
