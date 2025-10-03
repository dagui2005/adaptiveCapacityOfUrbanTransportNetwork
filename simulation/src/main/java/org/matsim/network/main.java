package org.matsim.network;

import org.matsim.api.core.v01.Coord;
import org.matsim.network.NetworkLoader;
import org.matsim.network.BusNetworkIntegrator;
import org.matsim.network.MetroNetworkIntegrator;
import org.matsim.network.TransitScheduleWriter;
import org.matsim.network.TransitVehiclesWriter;
import org.matsim.api.core.v01.Id;

import java.util.Arrays;

class Main {
    public static void main(String[] args) throws Exception {
        // 输入文件路径（你可以通过 args 或配置文件传入）
        String networkFile = "network_car.xml";
        String busStopsShp = "guangzhou_busstop.shp";
        String busLinesShp = "guangzhou_busline.shp";
        String metroLinesShp = "lines.shp";
        String metroStationsShp = "station.shp";

        // Step1: 载入 road network
        var result = NetworkLoader.loadNetwork(networkFile);
        var network = result.network;

        // Step2: 集成公交
        BusNetworkIntegrator busIntegrator = new BusNetworkIntegrator(network, 5, 50.0);
        busIntegrator.integrateBusLines(busStopsShp, busLinesShp);

        // Step3: 集成地铁
        MetroNetworkIntegrator metroIntegrator = new MetroNetworkIntegrator();
        metroIntegrator.buildNetworkFromShp(metroLinesShp, "network_with_metro.xml");

        // Step4: 生成 schedule
        TransitScheduleWriter tsw = new TransitScheduleWriter("network_with_metro.xml");

// 创建停靠点
        var stop1 = tsw.createStop("S1", "LKBS000000001", new Coord(113.3, 23.1));
        var stop2 = tsw.createStop("S2", "LKBS000000002", new Coord(113.4, 23.2));

// 取 link 对象
        var link1 = tsw.network.getLinks().get(Id.createLinkId("LKBS000000001"));
        var link2 = tsw.network.getLinks().get(Id.createLinkId("LKBS000000002"));

// 添加公交线路：每 900 秒一班，首班车 06:30，末班车 22:30
        tsw.addBusLine(
                "B1",
                Arrays.asList(stop1, stop2),
                Arrays.asList(link1, link2),
                900,                     // headwaySec = 15min
                6 * 3600 + 30 * 60,      // 06:30
                22 * 3600 + 30 * 60      // 22:30
        );

        tsw.writeSchedule("transitSchedule.xml");


        // Step5: 生成车辆文件
        TransitVehiclesWriter tvw = new TransitVehiclesWriter();
        tvw.addBus("B1_veh001");
        tvw.addMetro("M1_veh001");
        tvw.write("transitVehicles.xml");

        System.out.println("All done.");
    }
}
