package org.matsim.network;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * TransitBuilder
 *
 * 功能：
 * 1. 读取 network_car.xml
 * 2. 读取公交与地铁 shapefile
 * 3. 建立公交线路与 network 的 link 序列映射，如缺失则补充 link
 * 4. 添加地铁线路 link 与 node
 * 5. 生成 transitSchedule.xml
 * 6. 生成 transitVehicles.xml
 */
public class TransitBuilder {

    // === 文件路径 ===
    private static final String INPUT_NETWORK = "D:/Luan/2025-09/MATSim/guangzhoubaseline/network_car.xml";
    private static final String BUSSTOP_SHP = "D:/shp/guangzhou_busstop.shp";
    private static final String BUSLINE_SHP = "D:/shp/guangzhou_busline.shp";
    private static final String BUSSTOP_MERGED_SHP = "D:/shp/guangzhou_busstop_merged.shp";
    private static final String METRO_LINES_SHP = "D:/shp/lines.shp";
    private static final String METRO_STATIONS_SHP = "D:/shp/station.shp";

    private static final String OUTPUT_NETWORK = "D:/MATSim/out/network_with_transit.xml";
    private static final String OUTPUT_SCHEDULE = "D:/MATSim/out/transitSchedule.xml";
    private static final String OUTPUT_VEHICLES = "D:/MATSim/out/transitVehicles.xml";

    // === 自增 ID 生成器 ===
    private static long artificialLinkId = 1;
    private static long artificialNodeId = 1;
    private static long ptLinkId = 1;
    private static long ptNodeId = 1;

    public static void main(String[] args) throws Exception {
        // Step1: 载入 road network
        // Config config = ConfigUtils.createConfig();
        // Scenario scenario = ScenarioUtils.createScenario(config);
        // new MatsimNetworkReader(scenario.getNetwork()).readFile(INPUT_NETWORK);
        // Network network = scenario.getNetwork();
        var result = NetworkLoader.loadNetwork(INPUT_NETWORK);
        var network = result.network;
        System.out.println("✅ Road network loaded: " + network.getLinks().size() + " links");

        // === 2. 处理公交线路，匹配到 network link ===

//        SimpleFeatureSource busStops = loadShp(BUSSTOP_SHP);
//        SimpleFeatureSource busLines = loadShp(BUSLINE_SHP);
//        SimpleFeatureSource busStopsMerged = loadShp(BUSSTOP_MERGED_SHP);
        SimpleFeatureSource metroLines = loadShp(METRO_LINES_SHP);
        SimpleFeatureSource metroStations = loadShp(METRO_STATIONS_SHP);
        // Step2: 集成公交
        BusNetworkIntegrator busIntegrator = new BusNetworkIntegrator(network, 5.0, 50.0);
        busIntegrator.integrateBusLines(BUSSTOP_SHP, BUSLINE_SHP);

        Map<String, List<Id<Link>>> linePaths = busIntegrator.getLineLinkPaths();
        Map<String, Id<Link>> stopToLink = busIntegrator.getStopToLinkMapping();

        // === 3. 处理地铁线路 ===
        // TODO: 遍历 metroLines 按 FlD_road 分组，拼接为 link 序列
        // TODO: 生成双向 link，属性设定
        // TODO: metroStations 坐标查找/生成 node

        // === 4. 生成 transitSchedule.xml ===
        TransitScheduleFactory tsFactory = new TransitScheduleFactoryImpl();
        TransitSchedule schedule = tsFactory.createTransitSchedule();

        // TODO: 添加 transitStops
        // TODO: 添加 minimalTransferTimes
        // TODO: 添加 transitLines (bus + metro)，含 transitRoutes, routeProfile, route, departures

        new TransitScheduleWriter(schedule).writeFile(OUTPUT_SCHEDULE);
        System.out.println("✅ TransitSchedule written: " + OUTPUT_SCHEDULE);

        // === 6. 生成 transitVehicles.xml ===
        Vehicles vehicles = VehicleUtils.createVehiclesContainer();
        VehicleType busType = createBusVehicleType(vehicles);
        VehicleType metroType = createMetroVehicleType(vehicles);

        // TODO: 遍历所有 departure，生成 vehicle 实例
        // Vehicle v = vehiclesFactory.createVehicle(Id.createVehicleId("bus001"), busType);
        // vehicles.addVehicle(v);

        new VehicleWriterV1(vehicles).writeFile(OUTPUT_VEHICLES);
        System.out.println("✅ TransitVehicles written: " + OUTPUT_VEHICLES);

        // === 7. 写出新的 network ===
        new NetworkWriter(network).write(OUTPUT_NETWORK);
        System.out.println("✅ Extended Network written: " + OUTPUT_NETWORK);
    }

    // 加载 shapefile
    private static SimpleFeatureSource loadShp(String path) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("url", new File(path).toURI().toURL());
        DataStore ds = DataStoreFinder.getDataStore(map);
        return ds.getFeatureSource(ds.getTypeNames()[0]);
    }

    // 创建 bus 车辆类型
    private static VehicleType createBusVehicleType(Vehicles vehicles) {
        VehicleType busType = vehicles.getFactory().createVehicleType(Id.create("bus", VehicleType.class));
        VehicleCapacity cap = busType.getCapacity(); // 获取已有 capacity 对象
        cap.setSeats(27);
        cap.setStandingRoom(80);
        busType.setLength(10.0);
        busType.setWidth(3.0);
        vehicles.addVehicleType(busType);
        return busType;
    }

    // 创建 metro 车辆类型
    private static VehicleType createMetroVehicleType(Vehicles vehicles) {
        VehicleType metroType = vehicles.getFactory().createVehicleType(Id.create("metro", VehicleType.class));
        VehicleCapacity cap = metroType.getCapacity(); // 获取已有 capacity 对象
        cap.setSeats(800);
        cap.setStandingRoom(1000);
        metroType.setLength(200.0);
        metroType.setWidth(3.0);
        vehicles.addVehicleType(metroType);
        return metroType;
    }




}
