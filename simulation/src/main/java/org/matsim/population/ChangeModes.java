package org.matsim.population;

import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;

/**
 * @author: Chunhong li
 * @date: 2022年05月05日 16:05
 * @Description: 把所有的出行方式全部改成 car
 */
public class ChangeModes {
    public static void run(Population pop, String newMode) {
        for (Person person : pop.getPersons().values()
        ) {
            for (Plan plan : person.getPlans()
            ) {
                for (PlanElement planElement : plan.getPlanElements()) {
                    if (planElement instanceof Leg) {   // 用instanceof运算符可以用来判断某对象对应的类是否实现了指定接口.
//                        更改 leg 的 mode 为 car
                        ((Leg) planElement).setMode(newMode);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        Population population = PopulationUtils.readPopulation("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】需求数据\\demand.noex20.0.27.100pct.xml");
        run(population, "car");
        PopulationUtils.writePopulation(population, "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】需求数据\\demand.noex20.0.27.100pct.all.is.car.xml");
    }
}
