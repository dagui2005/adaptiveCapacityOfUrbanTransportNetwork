package org.matsim.pt;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Chunhong li
 * @date: 2022年04月07日 9:59
 * @Description: 本程序用于清理无效的公共交通线路和站点。若公交站点或线路经过的 links 不存在于 network.xml 文件中，则视为无效。本程序也用于耦合网络结构破坏后的运营失效。
 * 相较于 "CleanInvalidSchedule"，这个类更 general，提供的接口更多。
 * 另外，这个程序的主程序是为了计算随机破坏后的系统结构破坏引起的运营破坏。
 */
public class MyScheduleCleaner {
    /* clean the transit lines and stations of schedule whose links not exit on the network. */
    public static void cleanScheduleWithNetwork(TransitSchedule transitSchedule, Network network) {
        //        Transit lines to be removed.   这部分是包括直接运营失效和间接运营失效后的线路，由它计算的失效站点数量是直接和间接失效后的值。
        List<TransitLine> transitLinesRemoved = new ArrayList<>();

        //        遍历所有公共交通线路经过的路段，看看还存不存在，计算被淹没的公共交通线路
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()
        ) {
            boolean isValid = true;
            // 循环标签，默认情况下 break 只能跳出当前循环，添加标签后，可跳出指定循环。
            for (TransitRoute transitRoute : transitLine.getRoutes().values()
            ) {
//                判断 transitRoute 经过的 links (只有中间 links 的判断)
                for (Id<Link> linkId : transitRoute.getRoute().getLinkIds()
                ) {
//                    NOTE: 20220601 我发现一个巨大的坑：transitRoute.getRoute().getLinkIds() 只能得到中间经过的 links，首站和末站对应的 link 无法返回. 20220602
//                    这导致我们无法判断首末站所在的 links 是否还存在，因此我在后面对站点所在的 link 也进行了判断
                    if (!network.getLinks().containsKey(linkId)) {
//                        这条线路中只要有一个路段被破坏，这条线路就被破坏掉了
                        isValid = false;

                        // 20220410，经检验，移除公共交通线路的原理和程序应该是没问题的，但是问题在于输入的道路网络就是有问题的，输入的网络不含有任何 artificial 路段，这导致大量bus线路没有匹配的路段，被删掉，因此造成了 bus 线网下降的巨快。
                        // 再往前推，这应当是 MATSim Network 转为 shp 的问题，没有把 artificial 路段转过去。
                    }
                }
//                判断 transitRoute 所有 stops 所在的 links
                for (TransitRouteStop stop : transitRoute.getStops()) {
                    var linkId = stop.getStopFacility().getLinkId();
                    if (!network.getLinks().containsKey(linkId)) {
//                        这条线路中只要有一个站点被破坏，这条线路就被破坏掉了
                        isValid = false;
                    }
                }
                if (!isValid) {
                    transitLinesRemoved.add(transitLine);
                }
            }
        }

        //       delete invalid lines.
        for (TransitLine transitLine : transitLinesRemoved
        ) {
            transitSchedule.removeTransitLine(transitLine);
        }

        // remove transit stops not used by the transit lines
        ScheduleCleaner.removeNotUsedStopFacilities(transitSchedule);
    }

    /* clean the transfer information of schedule */
    public static void cleanTransfer(TransitSchedule transitSchedule) {
        //        ScheduleCleaner.removeNotUsedMinimalTransferTimes(scheduleBeforeDamage);  // 20220417 仍然会有换成信息是无效的，因此我直接将所有的换乘信息删除，程序借鉴了本行代码对应的源代码
        MinimalTransferTimes transferTimes = transitSchedule.getMinimalTransferTimes();
        MinimalTransferTimes.MinimalTransferTimesIterator iterator = transferTimes.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            transferTimes.remove(iterator.getFromStopId(), iterator.getToStopId());
        }
    }

    public static void main(String[] args) {
//        int seed = 7;    // 随机种子 --- 网络随机破坏的种子
//        while (seed <= 9) {
//            double threshold = 0.0;   // 随机破坏场景对应的洪水场景的淹没阈值
//            while (threshold <= 15) {
////            NOTE: random damages considering multiplex networks as different networks. That is to say, road network and metro network have different random disruption proportion.
//                Network network = NetworkUtils.readNetwork(networkFolder + "residual coupled network.randomDamages.thereshold" + threshold + "m.seed" + seed + ".s.xml");
//                TransitSchedule transitSchedule = ScheduleTools.readTransitSchedule("src/main/resources/transitSchedule.xml");
//
//                // clean the transit lines and stations of schedule whose links not exit on the network
//                cleanScheduleWithNetwork(transitSchedule, network);
//                // remove transfer information
//                cleanTransfer(transitSchedule);
//
//                ScheduleTools.writeTransitSchedule(transitSchedule, newScheduleFolder + "residual schedule.randomDamages.thereshold" + threshold + "m.seed" + seed + ".xml");
//
//                threshold += 0.5;
//            }
//            seed += 1;
//        }

//        Flood damages for open-source scenarios.
        double[] thresholds = new double[]{3.0, 4.0, 5.0};
        for (double threshold : thresholds) {
//            Todo: change city.
//            for Nanjing.
//            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m.s.xml";
//            Network network = NetworkUtils.readNetwork(networkPath);
//            String oldSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\public transit schedule XML\\transitSchedule.xml";
//            TransitSchedule oldSchedule = ScheduleTools.readTransitSchedule(oldSchedulePath);
//            String newSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after floods damages\\xml\\residual transit schedule.rp100.thereshold" + threshold + "m.xml";

//            for Nanjing.  (distinct threshold for different modes)
            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold" + threshold + "m (distinct threshold).s.xml";
            Network network = NetworkUtils.readNetwork(networkPath);
            String oldSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\public transit schedule XML\\transitSchedule.xml";
            TransitSchedule oldSchedule = ScheduleTools.readTransitSchedule(oldSchedulePath);
            String newSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】线网数据\\after floods damages\\xml\\residual transit schedule.rp100.thereshold" + threshold + "m (distinct threshold).xml";

//            for hamburg.
//            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\network\\xml\\hamburg-network-indirect failure-threshold" + threshold + "m.xml.gz";
//            Network network = NetworkUtils.readNetwork(networkPath);
//            String oldSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\schedule\\xml\\hamburg-v3.0-transitSchedule.xml.gz";
//            TransitSchedule oldSchedule = ScheduleTools.readTransitSchedule(oldSchedulePath);
//            String newSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】德国汉堡\\洪水场景\\schedule\\xml\\hamburg-transitSchedule-threshold" + threshold + "m.xml.gz";
//            for los angeles
//            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-50th-los-angeles-network-indirect failure-threshold" + threshold + "m.xml.gz";
//            Network network = NetworkUtils.readNetwork(networkPath);
//            String oldSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-los-angeles-v1.0-transitSchedule_2019-12-18.xml.gz";
//            TransitSchedule oldSchedule = ScheduleTools.readTransitSchedule(oldSchedulePath);
//            String newSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-50th-los-angeles-transitSchedule-threshold" + threshold + "m.xml.gz";

//            for baseline. Todo: We need only one iteration.
//            String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\network\\xml\\my2-los-angeles-v1.0-network_2019-12-10.xml.gz";
//            Network network = NetworkUtils.readNetwork(networkPath);
//            String oldSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\los-angeles-v1.0-transitSchedule_2019-12-18.xml.gz";
//            TransitSchedule oldSchedule = ScheduleTools.readTransitSchedule(oldSchedulePath);
//            String newSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【验证】美国洛杉矶\\flood scenario\\schedule\\xml\\my2-los-angeles-v1.0-transitSchedule_2019-12-18.xml.gz";

            // clean the transit lines and stations of schedule whose links not exit on the network
            cleanScheduleWithNetwork(oldSchedule, network);
            // remove transfer information
            cleanTransfer(oldSchedule);

            ScheduleTools.writeTransitSchedule(oldSchedule, newSchedulePath);
        }
    }
}
