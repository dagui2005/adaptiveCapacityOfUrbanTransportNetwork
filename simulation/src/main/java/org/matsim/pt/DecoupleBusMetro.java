package org.matsim.pt;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author: Chunhong li
 * @date: 2022年04月07日 21:54
 * @Description: 输入 bus 和 subway 耦合的 schedule, 输出 bus Schedule 和 subway Schedule. 这里的解耦方式是通过判断线路 id 是否再地铁线路 id 集合中.
 */
public class DecoupleBusMetro {

    public static void main(String[] args) {
        double threshold = 0.0;
        while (threshold <= 0) {
            String coupledSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\public transit schedule XML\\transitSchedule.xml";
//            String coupledSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after floods damages\\xml\\residual transit schedule.rp100.thereshold" + threshold + "m.xml";
            String busSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\双模式-无洪水\\schedule\\bus schedule.xml";

            var coupledSchedule = ScheduleTools.readTransitSchedule(coupledSchedulePath);
            var transitScheduleList = decouple(coupledSchedule);
            ScheduleTools.writeTransitSchedule(transitScheduleList.get(0), busSchedulePath);

            threshold += 0.5;
        }
    }

    public static List<TransitSchedule> decouple(TransitSchedule coupledSchedule) {
        /* Input: coupledSchedule, Outputs: ArrayList<>(Arrays.asList(busSchedule, metroSchedule))*/

        //    地铁线路 ID 集合
        ArrayList<Id<TransitLine>> metroLineIds = new ArrayList<>();
        //    下面是南京市的地铁线路. 一共 10 条.
        metroLineIds.add(Id.create("252014062790001_252014062790002", TransitLine.class));
        metroLineIds.add(Id.create("252014062790101_252014062790102", TransitLine.class));
        metroLineIds.add(Id.create("252014073000001_252014073000002", TransitLine.class));
        metroLineIds.add(Id.create("252015032500001_252015032500002", TransitLine.class));
        metroLineIds.add(Id.create("252017011600001_32012015011300001", TransitLine.class));
        metroLineIds.add(Id.create("252017030200001_252017122800001", TransitLine.class));
        metroLineIds.add(Id.create("252017030700001_252018052800002", TransitLine.class));
        metroLineIds.add(Id.create("252017112900001_32012014122500003", TransitLine.class));
        metroLineIds.add(Id.create("3201023303_3201023304", TransitLine.class));
        metroLineIds.add(Id.create("201033401_201033402", TransitLine.class));

        TransitSchedule busSchedule = ScheduleTools.createSchedule();  // ScheduleTools 非常好用，可以用于创建、合并、输出 Schedule, Vehicle，等，是一个宝箱。
        TransitSchedule metroSchedule = ScheduleTools.createSchedule();

        // 20200110. bus的stop都有“:”，而 metro的stops没有，根据这个将公共交通站点分给bus或metro
        for (TransitStopFacility transitStop : coupledSchedule.getFacilities().values()
        ) {
            if (transitStop.getId().toString().contains(":")) {
                busSchedule.addStopFacility(transitStop);
            } else {
                metroSchedule.addStopFacility(transitStop);
            }
//            busSchedule.addStopFacility(transitStop);
//            metroSchedule.addStopFacility(transitStop);
        }

        for (TransitLine transitLine : coupledSchedule.getTransitLines().values()
        ) {
            if (metroLineIds.contains(transitLine.getId())) {
                metroSchedule.addTransitLine(transitLine);
            } else {
                busSchedule.addTransitLine(transitLine);
            }
        }

        return new ArrayList<>(Arrays.asList(busSchedule, metroSchedule));
    }
}
