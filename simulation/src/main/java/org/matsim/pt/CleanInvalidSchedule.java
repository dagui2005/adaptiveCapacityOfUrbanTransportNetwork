package org.matsim.pt;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.run.CheckMappedSchedulePlausibility;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Chunhong li
 * @date: 2022年04月07日 9:59
 * @Description: 本程序用于清理无效的公共交通线路和站点。若公交站点或线路经过的 links 不存在于 network.xml 文件中，则视为无效。本程序也用于耦合网络结构破坏后的运营失效。
 */
public class CleanInvalidSchedule {
    //    INPUTS folder: MATSim network file after structural damages.
    private static final String networkFolder = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\";
    //    INPUTS: MATSim transit schedule file before damages.
    private static final String oldSchedulePath = "src/main/resources/transitSchedule.xml";

    //    crs
    private static final String crs = "EPSG:32650";

    //   outputs Folder: MATSim transit schedule after operational damages.
    private static final String newScheduleFolder = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after floods damages\\xml\\";

    public static void clean(double thereshold) {
        String networkPath = networkFolder + "residual coupled network.rp100.thereshold" + thereshold + "m.s.xml";
        String newSchedulePath = newScheduleFolder + "residual transit schedule.rp100.thereshold" + thereshold + "m.xml";

        /* 1. import data */
//        import MATSim network file after structural damages.
        Scenario scenario = ScenarioUtils.loadScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkPath);
        Network networkAfterDamage = scenario.getNetwork();
//        import MATSim transit schedule file before damages.
        TransitSchedule scheduleBeforeDamage = ScheduleTools.readTransitSchedule(oldSchedulePath);

        /* 2. statistics. transit stops and lines which needed to be deleted */
//        Transit stops to be removed.   这部分其实移除的是直接运营失效的站点
        List<TransitStopFacility> transitStopsRemoved = new ArrayList<>();
//        Transit lines to be removed.   这部分是包括直接运营失效和间接运营失效后的线路，由它计算的失效站点数量是直接和间接失效后的值。
        List<TransitLine> transitLinesRemoved = new ArrayList<>();
        List<TransitRoute> transitRoutesRemoved = new ArrayList<>();

        for (TransitStopFacility transitStop : scheduleBeforeDamage.getFacilities().values()
        ) {
            if (transitStop.getLinkId().toString().split("_")[0].equals("pt")) {
                // 如果该 Link 是 artificial link，我们视为永远不会被淹没，跳过
                continue;
            }
            if (!networkAfterDamage.getLinks().containsKey(transitStop.getLinkId())) {
                transitStopsRemoved.add(transitStop);   // 20220410，经检验，道路网络破坏前后的 linkId 是没有发生变化的，移除公共交通的无效站点 这一部分应当是没有问题的。
            }
        }

//        20200410, 下面是之前的算法，遍历所有公共交通线路经过的路段，看看还存不存在，计算被淹没的公共交通线路
        for (TransitLine transitLine : scheduleBeforeDamage.getTransitLines().values()
        ) {
            label:
            // 循环标签，默认情况下 break 只能跳出当前循环，添加标签后，可跳出指定循环。
            for (TransitRoute transitRoute : transitLine.getRoutes().values()
            ) {
                for (Id<Link> linkId : transitRoute.getRoute().getLinkIds()
                ) {
                    if (linkId.toString().split("_")[0].equals("pt")) {
                        // 如果该 Link 是 artificial link，我们视为永远不会被淹没，跳过
                        continue;
                    }
                    if (!networkAfterDamage.getLinks().containsKey(linkId)) {
//                        这条线路中只要有一个路段被破坏，这条线路就被破坏掉了，即停止检测这条线路的其它路段，跳出两层的循环，循环下一个线路
                        transitLinesRemoved.add(transitLine);
                        break label;
                        // 20220410，经检验，移除公共交通线路的原理和程序应该是没问题的，但是问题在于输入的道路网络就是有问题的，输入的网络不含有任何 artificial 路段，这导致大量bus线路没有匹配的路段，被删掉，因此造成了 bus 线网下降的巨快。
                        // 再往前推，这应当是 MATSim Network 转为 shp 的问题，没有把 artificial 路段转过去。
                    }
                }
            }
        }


////        20220410 更改无效线路的计算方法，判断线路对应的站点是否存在，如果存在就认为有效，如果不存在就认为失效
//        for (TransitLine transitLine : scheduleBeforeDamage.getTransitLines().values()
//        ) {
//            label:
//            // 循环标签，默认情况下 break 只能跳出当前循环，添加标签后，可跳出指定循环。
//            for (TransitRoute transitRoute : transitLine.getRoutes().values()
//            ) {
//                for (TransitRouteStop transitRouteStop : transitRoute.getStops()
//                ) {
//                    if (transitStopsRemoved.contains(transitRouteStop)) {
////                        这条线路中只要有一个站点不存在了，这条线路就被破坏掉了，即停止检测这条线路的其它路段，跳出两层的循环，循环下一个线路
//                        transitLinesRemoved.add(transitLine);
//                        break label;
//                        // 20220410，经检验，移除公共交通线路的原理和程序应该是没问题的，但是问题在于输入的道路网络就是有问题的，输入的网络不含有任何 artificial 路段，这导致大量bus线路没有匹配的路段，被删掉，因此造成了 bus 线网下降的巨快。
//                        // 再往前推，这应当是 MATSim Network 转为 shp 的问题，没有把 artificial 路段转过去。
//                    }
//                }
//            }
//        }

        /* 3. delete invalid transit stops and lines. */
        for (TransitStopFacility transitStopFacility : transitStopsRemoved
        ) {
            scheduleBeforeDamage.removeStopFacility(transitStopFacility);
        }
        for (TransitLine transitLine : transitLinesRemoved
        ) {
            scheduleBeforeDamage.removeTransitLine(transitLine);
        }

        /* 3+ delete again. 20220410 我发现总有一些公交线路经过的公交站点并不存在，因此再删除一次 */
        List<TransitLine> transitLinesRemoved2 = new ArrayList<>();
        for (TransitLine transitLine : scheduleBeforeDamage.getTransitLines().values()
        ) {
            label2:
            for (TransitRoute transitRoute : transitLine.getRoutes().values()
            ) {
                for (TransitRouteStop transitRouteStop : transitRoute.getStops()
                ) {
                    if (!scheduleBeforeDamage.getFacilities().containsKey(transitRouteStop.getStopFacility().getId())) {
//                        只要该线路经过的任何一个站点不存在，即删除该线路
                        transitLinesRemoved2.add(transitLine);
                        break label2;
                    }
                }
            }
        }
        for (TransitLine transitLine : transitLinesRemoved2
        ) {
            scheduleBeforeDamage.removeTransitLine(transitLine);
        }

//        这时候使用 "ScheduleCleaner" 删除无效站点，无效换乘信息
//        ScheduleCleaner.removeNotUsedMinimalTransferTimes(scheduleBeforeDamage);  // 20220417 仍然会有换成信息是无效的，因此我直接将所有的换乘信息删除，程序借鉴了本行代码对应的源代码
        MinimalTransferTimes transferTimes = scheduleBeforeDamage.getMinimalTransferTimes();
        MinimalTransferTimes.MinimalTransferTimesIterator iterator = transferTimes.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            transferTimes.remove(iterator.getFromStopId(), iterator.getToStopId());
        }

        /* 4. delete invalid transit vehicles. 20220410, 这里我没有删除无效车辆，因为觉得删不删除对运行没有太大影响 */
//        ScheduleCleaner.cleanVehicles();

        /* 5. write */
        new NetworkWriter(networkAfterDamage).writeFileV2(networkPath);
        new TransitScheduleWriter(scheduleBeforeDamage).writeFileV2(newSchedulePath);

        /* 6. validate the schedule and network */
//        这里需要在控制台搜索 invalid，看看是不是有无效 schedule
        String outputFolder = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性\\【数据】线网数据\\after floods damages\\XML\\validation";
        CheckMappedSchedulePlausibility.main(new String[]{newSchedulePath, networkPath, crs, outputFolder});
//            matsim validate
//            TransitScheduleValidator.main(new String[]{newSchedulePath, networkPath});
    }

    public static void main(String[] args) {
        double thereshold = 15 ;
        while (thereshold <= 15) {
            clean(thereshold);
            thereshold += 0.5;
        }

    }


}
