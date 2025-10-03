package org.matsim.network;
import org.matsim.api.core.v01.Id; // 修复：添加 Id 类的导入
import org.matsim.vehicles.*;
import org.matsim.vehicles.VehicleType;

import java.io.File;

/**
 * TransitVehiclesWriter
 * 负责生成 transitVehicles.xml
 */
public class TransitVehiclesWriter {

    private final Vehicles vehicles;
    private final VehicleType busType;
    private final VehicleType metroType;

    public TransitVehiclesWriter() {
        this.vehicles = VehicleUtils.createVehiclesContainer();

        VehiclesFactory vf = vehicles.getFactory();

        busType = vf.createVehicleType(Id.create("bus", VehicleType.class));
        busType.getCapacity().setSeats(27);
        busType.getCapacity().setStandingRoom(80);
        busType.setLength(10.0);
        busType.setWidth(3.0);
        vehicles.addVehicleType(busType);

        metroType = vf.createVehicleType(Id.create("metro", VehicleType.class));
        metroType.getCapacity().setSeats(800);
        metroType.getCapacity().setStandingRoom(1000);
        metroType.setLength(200.0);
        metroType.setWidth(3.0);
        vehicles.addVehicleType(metroType);
    }

    public void addBus(String vehId) {
        Vehicle v = vehicles.getFactory().createVehicle(Id.create(vehId, Vehicle.class), busType);
        vehicles.addVehicle(v);
    }

    public void addMetro(String vehId) {
        Vehicle v = vehicles.getFactory().createVehicle(Id.create(vehId, Vehicle.class), metroType);
        vehicles.addVehicle(v);
    }

    public void write(String outFile) {
        new MatsimVehicleWriter(vehicles).writeFile(new File(outFile).getAbsolutePath());
    }
}
