package org.matsim.pt;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.vehicles.*;

import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * "CreateTransitSchedule" is aimed to creating a full transit schedule and a transit vehicle based on a csv containing line information.
 * This class is not perfect because its schedule is virtual data and applied to one line.
 *  2021.4.8, Chunhong Li, Zhengzhou 1th line.
 * */
public class CreateTransitUtil {
    //  paths of original schedule and vehicle files containing some titles only.
    private static final Path transitSchedulePath = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁时刻表\\transitschedule.xml");
    private static final Path transitVehiclePath = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁时刻表\\transitVehicles.xml");

    //  output schedule and vehicle files paths.
    private static final Path outputTransitSchedulePath = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁时刻表\\outputTransitSchedule.xml");
    private static final Path outputTransitVehiclePath = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁时刻表\\outputTransitVehicle");

    public static void main(String args[]) {
        new CreateTransitUtil().create();
    }

    public void create() {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);

        Vehicles vehicles = scenario.getTransitVehicles();  //  " scenario.getTransitVehicles() " returns "vehicles" other than "transitVehicles". Maybe "transitVehicles" is not developed well.
        TransitSchedule transitSchedule = scenario.getTransitSchedule();

//        read in existing files.
        new TransitScheduleReader(scenario).readFile(transitSchedulePath.toString());
        new MatsimVehicleReader(scenario.getVehicles()).readFile(transitVehiclePath.toString());

//        create some transit vehicle types.
        VehicleType subwayType = vehicles.getFactory().createVehicleType(Id.create("subway", VehicleType.class));
        subwayType.setLength(400);
//        note: maybe the method "vehicles.getFactory().createVehicleCapacity()" has been deprecated.
//        And "subwayType.setCapacity()" is also invalid.
        subwayType.setPcuEquivalents(0);   // ??? 2021.4.12,
        scenario.getTransitVehicles().addVehicleType(subwayType);

//        create some transit vehicles.
        int vehiclesNum = 100;
        int k = 1;
        while(k<=vehiclesNum) {
            Vehicle vehicle = vehicles.getFactory().createVehicle(Id.createVehicleId("train"+k), subwayType);
            scenario.getTransitVehicles().addVehicle(vehicle);
            k++;
        }

//      write out to the files.
        new MatsimVehicleWriter(scenario.getVehicles()).writeFile(outputTransitVehiclePath.toString());

//        ??? 2021.4.12, The output "vehicle" file is set by default values rather than the values I set.


//        System.out.println("breakPoint1");
        int p = 0;
        for (Id<VehicleType> vehicleTypeId : vehicles.getVehicleTypes().keySet()) {
//            System.out.println("breakPoint" + p);
            System.out.println(vehicleTypeId);
            System.out.println(vehicles.getVehicleTypes().get(vehicleTypeId));
            k++;

        }
//        VehicleType trainType = vehicles.


////        read the csv data.
//        try {
//            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁"),"utf-8");
//            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
//
//
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//
//        }
    }
}
