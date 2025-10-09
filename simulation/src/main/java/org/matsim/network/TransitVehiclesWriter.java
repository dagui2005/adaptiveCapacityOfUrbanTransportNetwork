package org.matsim.network;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;

import java.io.File;
import java.util.Locale;

/**
 * TransitVehiclesWriter — 增强版接口示例
 *
 * 新增方法：
 *   public void writeVehiclesFromSchedule(String scheduleFile, String outFile)
 *
 * 功能：
 *   - 自动读取 transitSchedule.xml；
 *   - 判断线路类型（bus / metro）；
 *   - 为每个 Departure 自动创建对应的 Vehicle；
 *   - Vehicle ID 规则： lineId_routeId_depTime；
 *   - 输出标准 MATSim transitVehicles.xml。
 */
public class TransitVehiclesWriter {

    private final org.matsim.vehicles.Vehicles vehicles;
    private final org.matsim.vehicles.VehicleType busType;
    private final org.matsim.vehicles.VehicleType metroType;

    public TransitVehiclesWriter() {
        this.vehicles = org.matsim.vehicles.VehicleUtils.createVehiclesContainer();
        org.matsim.vehicles.VehiclesFactory vf = vehicles.getFactory();

        // ===== Bus Type =====
        busType = vf.createVehicleType(Id.create("bus", org.matsim.vehicles.VehicleType.class));
        busType.getCapacity().setSeats(27);
        busType.getCapacity().setStandingRoom(80);
        busType.setLength(10.0);
        busType.setWidth(3.0);
        busType.setNetworkMode("car");
        busType.setFlowEfficiencyFactor(1.0);
        busType.setPcuEquivalents(1.0);
        busType.getAttributes().putAttribute("accessTimeInSecondsPerPerson", 0.5);
        busType.getAttributes().putAttribute("doorOperationMode",
                org.matsim.vehicles.VehicleType.DoorOperationMode.serial.toString());
        busType.getAttributes().putAttribute("egressTimeInSecondsPerPerson", 0.5);
        busType.getAttributes().putAttribute("costInformation", "");
        vehicles.addVehicleType(busType);

        // ===== Metro Type =====
        metroType = vf.createVehicleType(Id.create("metro", org.matsim.vehicles.VehicleType.class));
        metroType.getCapacity().setSeats(800);
        metroType.getCapacity().setStandingRoom(1000);
        metroType.setLength(200.0);
        metroType.setWidth(3.0);
        metroType.setNetworkMode("car");
        metroType.setFlowEfficiencyFactor(1.0);
        metroType.setPcuEquivalents(1.0);
        metroType.getAttributes().putAttribute("accessTimeInSecondsPerPerson", 0.5);
        metroType.getAttributes().putAttribute("doorOperationMode",
                org.matsim.vehicles.VehicleType.DoorOperationMode.serial.toString());
        metroType.getAttributes().putAttribute("egressTimeInSecondsPerPerson", 0.5);
        metroType.getAttributes().putAttribute("costInformation", "");
        vehicles.addVehicleType(metroType);
    }

    /**
     * 新增接口：从 transitSchedule.xml 自动生成对应的 transitVehicles.xml
     */
    public void writeVehiclesFromSchedule(String scheduleFile, String outFile) {
        try {
            System.out.println("TransitVehiclesWriter -> Reading schedule: " + scheduleFile);

            // 1) 读取 schedule
            Config config = ConfigUtils.createConfig();
            var scenario = ScenarioUtils.createScenario(config);
            TransitScheduleReader reader = new TransitScheduleReader(scenario);
            reader.readFile(scheduleFile);
            TransitSchedule schedule = scenario.getTransitSchedule();

            int count = 0;
            for (TransitLine line : schedule.getTransitLines().values()) {
                boolean isBus = isBusLine(line.getId().toString());
                for (TransitRoute route : line.getRoutes().values()) {
                    for (Departure dep : route.getDepartures().values()) {
                        // 生成车辆 ID：line_route_depTime（保留整数秒）
                        String vehIdStr = String.format(Locale.US, "%s_%s_%d",
                                line.getId(), route.getId(), (int) dep.getDepartureTime());
                        if (isBus) addBus(vehIdStr);
                        else addMetro(vehIdStr);
                        count++;
                    }
                }
            }

            // 2) 写出 vehicles 文件
            new MatsimVehicleWriter(vehicles).writeFile(new File(outFile).getAbsolutePath());
            System.out.printf("TransitVehiclesWriter -> Wrote %d vehicles to: %s%n", count, outFile);

        } catch (Exception e) {
            throw new RuntimeException("Error writing vehicles from schedule: " + scheduleFile, e);
        }
    }

    // --- 旧方法保留，便于手动添加 ---
    public void addBus(String vehId) {
        org.matsim.vehicles.Vehicle v = vehicles.getFactory()
                .createVehicle(Id.create(vehId, Vehicle.class), busType);
        vehicles.addVehicle(v);
    }

    public void addMetro(String vehId) {
        org.matsim.vehicles.Vehicle v = vehicles.getFactory()
                .createVehicle(Id.create(vehId, Vehicle.class), metroType);
        vehicles.addVehicle(v);
    }

    public void write(String outFile) {
        new MatsimVehicleWriter(vehicles).writeFile(new File(outFile).getAbsolutePath());
        System.out.println("Transit vehicles file written to: " + outFile);
    }

    // --- 类型识别逻辑，可扩展 ---
    private boolean isBusLine(String lineId) {
        String s = lineId.toLowerCase();
        return s.contains("bus") || s.contains("b") || s.contains("公交");
    }
}
