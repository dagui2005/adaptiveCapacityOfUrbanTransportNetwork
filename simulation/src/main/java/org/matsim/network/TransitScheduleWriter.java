package org.matsim.network;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.population.routes.NetworkRoute;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * 构建并写出一个带有公交+地铁的 TransitSchedule
 * 功能：
 * 1. 设置 CRS
 * 2. 自动生成停靠点（stops）
 * 3. 添加 transferTimes
 * 4. 生成公交和地铁线路
 * 5. 按照规则生成时刻表
 */
public class TransitScheduleWriter {

    private final Scenario scenario;
    private final TransitScheduleFactory factory;
    private final TransitSchedule schedule;
    final Network network;

    public TransitScheduleWriter(String networkFile) {
        Config config = ConfigUtils.createConfig();
        this.scenario = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(this.scenario.getNetwork()).readFile(networkFile);
        this.network = this.scenario.getNetwork();
        this.schedule = scenario.getTransitSchedule();
        this.factory = this.schedule.getFactory();
    }

    /** 1. 添加停靠点 */
    public TransitStopFacility createStop(String id, String linkId, Coord coord) {
        TransitStopFacility stop = factory.createTransitStopFacility(
                Id.create(id, TransitStopFacility.class), coord, false);
        stop.setLinkId(Id.createLinkId(linkId));
        stop.setName(id);
        schedule.addStopFacility(stop);
        return stop;
    }

    /** 2. 添加换乘时间 */
    public void addTransferTime(TransitStopFacility stop1, TransitStopFacility stop2, int timeSec) {
        // 在MATSim 2026.0版本中，使用MinimalTransferTimes来添加换乘时间
        MinimalTransferTimes transferTimes = schedule.getMinimalTransferTimes();
        transferTimes.set(stop1.getId(), stop2.getId(), timeSec);
    }

    /** 3. 构建公交线路 */
    public void addBusLine(String lineId, List<TransitStopFacility> stops, List<Link> routeLinks, int headwaySec, int startTime, int endTime) {
        TransitLine line = factory.createTransitLine(Id.create(lineId, TransitLine.class));

        // route profile
        TransitRouteStop[] routeStops = new TransitRouteStop[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            routeStops[i] = factory.createTransitRouteStop(stops.get(i), i * 300, i * 300 + 30); // 5 min intervals
        }

        // 创建路由ID列表
        List<Id<Link>> linkIds = new ArrayList<>();
        for (Link link : routeLinks) {
            linkIds.add(link.getId());
        }

        // 使用RouteUtils创建NetworkRoute
        NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(linkIds.get(0), linkIds, linkIds.get(linkIds.size() - 1));

        TransitRoute transitRoute = factory.createTransitRoute(
                Id.create(lineId + "_R", TransitRoute.class),
                route,
                Arrays.asList(routeStops),
                "bus"
        );

        // 4. 时刻表：headway-based
        for (int t = startTime; t < endTime; t += headwaySec) {
            transitRoute.addDeparture(factory.createDeparture(Id.create(lineId + "_dep_" + t, Departure.class), t));
        }

        line.addRoute(transitRoute);
        schedule.addTransitLine(line);
    }

    /** 4. 构建地铁线路 */
    public void addMetroLine(String lineId, List<TransitStopFacility> stops, List<Link> routeLinks, int intervalSec, int startTime, int endTime) {
        TransitLine line = factory.createTransitLine(Id.create(lineId, TransitLine.class));

        TransitRouteStop[] routeStops = new TransitRouteStop[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            routeStops[i] = factory.createTransitRouteStop(stops.get(i), i * 180, i * 180 + 20); // 3 min intervals
        }

        // 创建路由ID列表
        List<Id<Link>> linkIds = new ArrayList<>();
        for (Link link : routeLinks) {
            linkIds.add(link.getId());
        }

        // 使用RouteUtils创建NetworkRoute
        NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(linkIds.get(0), linkIds, linkIds.get(linkIds.size() - 1));

        TransitRoute transitRoute = factory.createTransitRoute(
                Id.create(lineId + "_R", TransitRoute.class),
                route,
                Arrays.asList(routeStops),
                "subway"
        );

        // timetable
        for (int t = startTime; t < endTime; t += intervalSec) {
            transitRoute.addDeparture(factory.createDeparture(Id.create(lineId + "_dep_" + t, Departure.class), t));
        }

        line.addRoute(transitRoute);
        schedule.addTransitLine(line);
    }

    /** 保存到 XML */
    public void writeSchedule(String outFile) {
        new TransitScheduleWriterV2(schedule).write(outFile);
    }

    /** 测试入口 */
    public static void main(String[] args) {
        String networkFile = "output/network.xml";
        String outSchedule = "output/transitSchedule.xml";

        TransitScheduleWriter builder = new TransitScheduleWriter(networkFile);

        // 创建停靠点
        TransitStopFacility stopA = builder.createStop("StopA", "1", new Coord(0, 0));
        TransitStopFacility stopB = builder.createStop("StopB", "2", new Coord(1000, 0));
        TransitStopFacility stopC = builder.createStop("StopC", "3", new Coord(2000, 0));
        TransitStopFacility stopD = builder.createStop("StopD", "10", new Coord(3000, 0));

        // transferTimes
        builder.addTransferTime(stopA, stopC, 120);

        // bus line
        builder.addBusLine("Bus1", Arrays.asList(stopA, stopB, stopC),
                Arrays.asList(builder.network.getLinks().get(Id.createLinkId("1")),
                        builder.network.getLinks().get(Id.createLinkId("2")),
                        builder.network.getLinks().get(Id.createLinkId("3"))),
                600, 6 * 3600, 22 * 3600);

        // metro line
        builder.addMetroLine("Metro1", Arrays.asList(stopC, stopD),
                Arrays.asList(builder.network.getLinks().get(Id.createLinkId("3")),
                        builder.network.getLinks().get(Id.createLinkId("10"))),
                300, 5 * 3600, 23 * 3600);

        builder.writeSchedule(outSchedule);

        System.out.println("Transit schedule written to " + outSchedule);
    }
}