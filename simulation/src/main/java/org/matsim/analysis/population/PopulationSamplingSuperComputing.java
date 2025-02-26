package org.matsim.analysis.population;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author: Chunhong li
 * @date: 2022年12月01日 15:08
 * @Description: 在超算平台上随机取样 output_plans 并输出
 */
public class PopulationSamplingSuperComputing {

    public static void main(String[] args) throws FileNotFoundException, MalformedURLException {
        URL PlansPath = PopulationSamplingSuperComputing.class.getResource("/nanjing/outputPlans/output_plans.xml.gz");
        Population population = PopulationUtils.readPopulation(String.valueOf(PlansPath));
        double samplePCT = 0.0001;
        // Note: we can't use getResource to find a nonexistent file which we prepare to generate.
        // Note: all things exported by jar is outside the jar and the same folder as the jar.

//        population sampling. This part is done in super-computing platform.
        PopulationUtils.sampleDown(population, samplePCT);   // 该函数具有随机性，且随机种子每次都不同，因此需要把输出的 population 记录下来
        PopulationUtils.writePopulation(population, "Sampled_plans.xml");
    }
}
