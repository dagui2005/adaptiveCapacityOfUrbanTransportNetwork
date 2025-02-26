package org.matsim.population;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

/**
 * @author: Chunhong li
 * @date: 2022年05月26日 11:12
 * @Description: 从 population 中随机抽取 一部分 plans
 * 20221025 用于从多模式交通系统种抽取一定比例的需求作为单模式（或双模式）交通系统的初始需求（未受到洪水影响）。
 */
public class PopulationSampling {
//    For Nanjing Single mode, Bi model
//    public static void main(String[] args) {
////        double samplingProp = 0.3909;    // car split is 1 - 60.91% = 39.09%. 使用仿真实验得到的方式分担率 (调查结果  https://www.nanjing.gov.cn/njxx/201906/t20190620_1570916.html)
//        double samplingProp = 0.6517;    // car and bus split is 65.17%.   估算得到的结果（详见20221017-问题及 python 8 洪水下的功能韧性的程序）
//
//        Population population = PopulationUtils.readPopulation("src/main/resources/demand.noex20.0.27.100pct.xml");
//        PopulationSampling populationSampling = new PopulationSampling();
////            sampling
//        populationSampling.run(population, samplingProp);
////            change pt to car
//        ChangeModes.run(population, "car");
//        PopulationUtils.writePopulation(population, "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\双模式-无洪水\\trips\\road.population.xml");
//    }

    public static void main(String[] args) {
        String inputPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\demand.noex20.0.27.100pct.xml";
        String outputPopPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】需求数据\\sensitivity analysis\\demand.noex20.0.27.50pct.xml.gz";
        Population population = PopulationUtils.readPopulation(inputPopPath);
        PopulationUtils.sampleDown(population, 0.50);
        PopulationUtils.writePopulation(population, outputPopPath);
    }
}
