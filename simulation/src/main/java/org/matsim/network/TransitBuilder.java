package org.matsim.network;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;

/**
 * TransitBuilder — 把 network + bus matching + metro integration + schedule + vehicles 全流程自动衔接
 *
 * 调用约定：
 *  - NetworkLoader.loadNetwork(String) 返回 LoadResult (含 scenario, network, CRS)
 *  - BusNetworkIntegrator.integrateBusLines(busStopsShp, busLinesShp)
 *  - MetroNetworkIntegrator.buildNetworkFromShp(linesShp, stationsShp, network)
 *  - TransitScheduleWriter / TransitVehiclesWriter 为你项目中已有的版本
 */
public class TransitBuilder {

    // === 输入/输出路径 ===
    private static final String INPUT_NETWORK = "D:/Luan/2025-09/MATSim/guangzhoubaseline/network_car.xml";
    private static final String BUSSTOP_SHP = "D:/shp/guangzhou_busstop.shp";
    private static final String BUSLINE_SHP = "D:/shp/guangzhou_busline.shp";
    private static final String METRO_LINES_SHP = "D:/shp/lines.shp";
    private static final String METRO_STATIONS_SHP = "D:/shp/station.shp";

    private static final String OUTPUT_NETWORK = "D:/MATSim/out/network_with_transit.xml";
    private static final String OUTPUT_SCHEDULE = "D:/MATSim/out/transitSchedule.xml";
    private static final String OUTPUT_VEHICLES = "D:/MATSim/out/transitVehicles.xml";

    // 参数
    private static final int NODE_COORD_DECIMAL = 5;
    private static final double NODE_SNAP_TOLERANCE = 50.0; // m

    public static void main(String[] args) throws Exception {
        System.out.println("=== TransitBuilder start ===");

        // 1) 读取基础 road network
        var loadResult = NetworkLoader.loadNetwork(INPUT_NETWORK);
        Scenario scenario = loadResult.scenario;
        Network network = loadResult.network;
        String inputCRS = loadResult.coordinateReferenceSystem;
        System.out.println("Loaded base network, links=" + network.getLinks().size());

        // 2) Bus 匹配
        System.out.println("-> Running BusNetworkIntegrator ...");
        BusNetworkIntegrator busIntegrator = new BusNetworkIntegrator(network, NODE_COORD_DECIMAL, NODE_SNAP_TOLERANCE);
        busIntegrator.integrateBusLines(BUSSTOP_SHP, BUSLINE_SHP);
        Map<String, List<Id<Link>>> busLinePaths = busIntegrator.getLineLinkPaths();
        Map<String, Id<Link>> stopToLink = busIntegrator.getStopToLinkMapping();
        System.out.println("Bus integration done. Bus lines matched: " + busLinePaths.size());

        // 3) Metro 集成
        System.out.println("-> Running MetroNetworkIntegrator ...");
        MetroNetworkIntegrator metroIntegrator = new MetroNetworkIntegrator(NODE_COORD_DECIMAL);
        Map<String, List<Id<Link>>> metroLinePaths =
                metroIntegrator.buildNetworkFromShp(METRO_LINES_SHP, METRO_STATIONS_SHP, network);
        System.out.println("Metro integration done. Metro lines added: " + metroLinePaths.size());

        // 4) 写出扩展后的 network
        System.out.println("-> Writing extended network to: " + OUTPUT_NETWORK);
        new NetworkWriter(network).write(OUTPUT_NETWORK);

        // 5) 生成 transitSchedule.xml
        System.out.println("-> Generating transit schedule ...");
        TransitScheduleWriter scheduleWriter = new TransitScheduleWriter(OUTPUT_NETWORK);
        if (inputCRS != null) scheduleWriter.setCRS(inputCRS);

        // 加载公交与地铁站点
        scheduleWriter.loadStopsFromShp(BUSSTOP_SHP, METRO_STATIONS_SHP);
        scheduleWriter.autoAddTransferTimes(BUSSTOP_SHP);

        // 加载线路
        scheduleWriter.loadBusLinesFromShp(BUSLINE_SHP, busLinePaths);
        scheduleWriter.loadMetroLinesFromShp(METRO_LINES_SHP, metroLinePaths);

        // 写出 schedule
        scheduleWriter.writeSchedule(OUTPUT_SCHEDULE);
        System.out.println("Transit schedule written to: " + OUTPUT_SCHEDULE);

        // 6) 调用 TransitVehiclesWriter 生成 transitVehicles.xml
        System.out.println("-> Generating transit vehicles ...");
        TransitVehiclesWriter vehiclesWriter = new TransitVehiclesWriter();
        vehiclesWriter.writeVehiclesFromSchedule(OUTPUT_SCHEDULE, OUTPUT_VEHICLES);
        System.out.println("Transit vehicles written to: " + OUTPUT_VEHICLES);

        // 7) 最终写回 network（已在步骤4写出，可选再次输出）
        new NetworkWriter(network).write(OUTPUT_NETWORK);
        System.out.println("Final network written to: " + OUTPUT_NETWORK);

        System.out.println("=== TransitBuilder finished ===");
    }
}
