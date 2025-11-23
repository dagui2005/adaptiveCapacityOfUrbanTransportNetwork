package org.matsim.network;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.io.NetworkWriter;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final String INPUT_NETWORK = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\network_car.xml";
//    private static final String INPUT_NETWORK = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\network_with_bus.xml";
    private static final String BUSSTOP_SHP = "D:\\Luan\\2025-09\\MATSim\\guangzhou_bus2025\\guangzhou_busstop.shp";
    private static final String BUSLINE_SHP = "D:\\Luan\\2025-09\\MATSim\\guangzhou_bus2025\\guangzhou_busline.shp";
    private static final String METRO_LINES_SHP = "D:\\Luan\\2025-09\\MATSim\\广州地铁shp\\line.shp";
    private static final String METRO_STATIONS_SHP = "D:\\Luan\\2025-09\\MATSim\\广州地铁shp\\station.shp";

    private static final String OUTPUT_NETWORK_BUS = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\network_with_bus.xml";
    private static final String OUTPUT_NETWORK = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\network_with_transit.xml";
    private static final String OUTPUT_SCHEDULE = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\transitSchedule.xml";
    private static final String OUTPUT_VEHICLES = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\transitVehicles.xml";

    // 参数
    private static final int NODE_COORD_DECIMAL = 2;
    private static final double NODE_SNAP_TOLERANCE = 20.0; // m
    private static final double ANGLE_THRESHOLD_DEG = 30.0; // 角度

    public static void main(String[] args) throws Exception {
        System.out.println("=== TransitBuilder start ===");

        // 1) 读取基础 road network
        var loadResult = NetworkLoader.loadNetwork(INPUT_NETWORK);
        Scenario scenario = loadResult.scenario;
        Network network = loadResult.network;
        String inputCRS = loadResult.coordinateReferenceSystem;
        System.out.println("Loaded base network, links=" + network.getLinks().size());

        // 2) Bus 匹配 - 修改构造函数调用，添加 inputCRS 参数
        System.out.println("-> Running BusNetworkIntegrator ...");
        BusNetworkIntegrator busIntegrator = new BusNetworkIntegrator(network, ANGLE_THRESHOLD_DEG, NODE_SNAP_TOLERANCE, inputCRS);
        busIntegrator.integrateBusLines(BUSSTOP_SHP, BUSLINE_SHP, OUTPUT_NETWORK_BUS);
        Map<String, BusNetworkIntegrator.BusLinePathInfo> busLinePathInfos = busIntegrator.getLinePathInfos(); // 获取线路路径信息
        Map<String, Id<Link>> stopToLink = busIntegrator.getStopToLinkMapping();
        System.out.println("Bus integration done. Bus lines matched: " + busLinePathInfos.size());

        // 3) Metro 集成 - 修改构造函数调用，添加 inputCRS 参数
        System.out.println("-> Running MetroNetworkIntegrator ...");
        MetroNetworkIntegrator metroIntegrator = new MetroNetworkIntegrator(NODE_COORD_DECIMAL, inputCRS);
        Map<String, List<Id<Link>>> metroLinePaths =
                metroIntegrator.buildNetworkFromShp(METRO_LINES_SHP, METRO_STATIONS_SHP, network);
        Map<String, Id<Link>> ptToLink = metroIntegrator.getPtToLinkMapping(); // 获取地铁站点映射
        System.out.println("Metro integration done. Metro lines added: " + metroLinePaths.size());

        // 4) 写出扩展后的 network
        new NetworkWriter(network).write(OUTPUT_NETWORK);
        System.out.println("-> Writing extended network to: " + OUTPUT_NETWORK);

        // 5) 生成 transitSchedule.xml
        System.out.println("-> Generating transit schedule ...");
        TransitScheduleWriter scheduleWriter = new TransitScheduleWriter(OUTPUT_NETWORK);
        if (inputCRS != null) scheduleWriter.setCRS(inputCRS);

        // 加载公交与地铁站点
        // 在 TransitBuilder.main 中合并两个映射
        Map<String, Id<Link>> combinedStopToLink = new HashMap<>();
        combinedStopToLink.putAll(stopToLink); // 公交站点映射
        combinedStopToLink.putAll(ptToLink);   // 地铁站点映射

        // 更新调用
        scheduleWriter.loadStopsFromShp(BUSSTOP_SHP, METRO_STATIONS_SHP, combinedStopToLink);
//        scheduleWriter.loadStopsFromShp(BUSSTOP_SHP, METRO_STATIONS_SHP, stopToLink);
//        scheduleWriter.autoAddTransferTimes(BUSSTOP_SHP);

        // 修改为：
        scheduleWriter.autoAddTransferTimes(combinedStopToLink);

        // 加载线路
        scheduleWriter.loadBusLinesFromShp(BUSLINE_SHP, busLinePathInfos);
        scheduleWriter.loadMetroLinesFromShp(METRO_LINES_SHP, metroLinePaths);

        // 写出 schedule
        scheduleWriter.writeSchedule(OUTPUT_SCHEDULE);
        System.out.println("Transit schedule written to: " + OUTPUT_SCHEDULE);

        // 6) 调用 TransitVehiclesWriter 生成 transitVehicles.xml
        System.out.println("-> Generating transit vehicles ...");
        TransitVehiclesWriter vehiclesWriter = new TransitVehiclesWriter();
        vehiclesWriter.writeVehiclesFromSchedule(OUTPUT_SCHEDULE, OUTPUT_VEHICLES);
        System.out.println("Transit vehicles written to: " + OUTPUT_VEHICLES);

        System.out.println("=== TransitBuilder finished ===");
    }
}
