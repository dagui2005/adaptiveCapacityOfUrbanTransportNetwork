package org.matsim.population;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

/**
 * @author: Chunhong li
 * @date: 2022年05月26日 11:12
 * @Description: 从 population 中随机抽取 一部分 plans
 */
public class PopulationSampling {
    public static void main(String[] args) {
        String inputPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\demand.noex20.0.27.100pct.xml";
        String outputPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\sensitivity analysis\\demand.noex20.0.27.50pct.xml.gz";
        Population population = PopulationUtils.readPopulation(inputPopPath);
        PopulationUtils.sampleDown(population, 0.50);
        PopulationUtils.writePopulation(population, outputPopPath);
    }
}
