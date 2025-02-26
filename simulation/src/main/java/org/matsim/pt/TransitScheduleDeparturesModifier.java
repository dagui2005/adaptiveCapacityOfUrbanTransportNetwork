/* *********************************************************************** *
 * project: org.matsim.*
 * RunEmissionToolOffline.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.pt;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.qsim.pt.TransitVehicle;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Spring '21.1102
 * @Description: 本程序用于调整公共交通时刻表，侧重于修正发车间隔和频率。
 * 输入 schedule.xml 文件；输出 schedule.xml 和 vehicle.xml 文件。
 * 注意：schedule.xml 文件的发车间隔与频率不涉及地铁站点是否虚拟重构部分，直接修改即可。
 */
public class TransitScheduleDeparturesModifier {

    //	输入 config.xml 文件路径。其实等价于确定了 schedule.xml, transitVehicle.xml 文件路径。
    private static String configPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】交通仿真\\发车间隔修正_1102\\config.xml";

    //	输出的 schedule.xml 文件路径
    private static String modifiedSchedulePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】交通仿真\\发车间隔修正_1102\\modifiedTransitSchedule.xml";

    //	输出的 vehicle.xml 文件路径
    private static String modifiedVehiclePath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络鲁棒性\\【数据】交通仿真\\发车间隔修正_1102\\transitVehicle.xml";

    public static void main(String[] args) {
        Config config = ConfigUtils.loadConfig(configPath);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        TransitScheduleFactory transitScheduleFactory = scenario.getTransitSchedule().getFactory();
        VehiclesFactory vehiclesFactory = scenario.getTransitVehicles().getFactory();

//        bus, train 两种车辆类型
        List<VehicleType> vehicleTypes = new ArrayList<> ();
        vehicleTypes.addAll(scenario.getTransitVehicles().getVehicleTypes().values());  // collection 转 list 的经典操作
        VehicleType busType = vehicleTypes.get(0);
        VehicleType trainType = vehicleTypes.get(1);


//        删除所有车辆
        for (Vehicle vehicle:scenario.getTransitVehicles().getVehicles().values()){
            scenario.getTransitVehicles().removeVehicle(vehicle.getId());
        }

//		遍历所有 transitRoute，依据发车频率创建每一条线路的发车记录，并匹配对应的车辆
//      工作日：bus(5:40-22:20)高峰8min,平峰15min；
//             subway（6:00-22:00）：高峰5min,平峰10min
        for (TransitLine transitLine : scenario.getTransitSchedule().getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
//                删除所有旧的 departure. !!!谨记，不可在循环中删除()语句中的变量要素，也可以用 iterator
                int p = 0,ps=transitRoute.getDepartures().size();
                List<Departure> departures = new ArrayList<> ();
                departures.addAll(transitRoute.getDepartures().values());while(p<ps){
                    transitRoute.removeDeparture(departures.get(p));
                    p++;
                }

                Departure departure = null;
                if (transitRoute.getTransportMode() == "bus") {
                    int k = 1;   // 记录第几个 departure
                    for(double departureTime=5*3600+40*60;departureTime<7*3600;departureTime+=15*60){
//                        bus 平峰时段 5:40-7:00,发车间隔15min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        bus 车辆及车辆编号
                        Id<Vehicle> busId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle bus = vehiclesFactory.createVehicle(busId,busType);
//                        公交车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(bus);
//                        发车补充：行车计划
                        departure.setVehicleId(busId);
                        transitRoute.addDeparture(departure);
                        k++;

                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<9*3600;departureTime+=8*60){
//                        bus 高峰时段  7:00-9:00, 8min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        bus 车辆及车辆编号
                        Id<Vehicle> busId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle bus = vehiclesFactory.createVehicle(busId,busType);
//                        公交车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(bus);
//                        发车补充：行车计划
                        departure.setVehicleId(busId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<17*3600;departureTime+=15*60){
//                        bus 平峰时段  9:00-17:00, 15min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        bus 车辆及车辆编号
                        Id<Vehicle> busId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle bus = vehiclesFactory.createVehicle(busId,busType);
//                        公交车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(bus);
//                        发车补充：行车计划
                        departure.setVehicleId(busId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<19*3600;departureTime+=8*60){
//                        bus 平峰时段  17:00-19:00, 8min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        bus 车辆及车辆编号
                        Id<Vehicle> busId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle bus = vehiclesFactory.createVehicle(busId,busType);
//                        公交车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(bus);
//                        发车补充：行车计划
                        departure.setVehicleId(busId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<22*3600+20;departureTime+=15*60){
//                        bus 平峰时段  19:00-22:20, 15min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        bus 车辆及车辆编号
                        Id<Vehicle> busId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle bus = vehiclesFactory.createVehicle(busId,busType);
//                        公交车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(bus);
//                        发车补充：行车计划
                        departure.setVehicleId(busId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    System.out.printf("...bus route %s,描述 %s...\n",transitRoute.getId(),transitLine.getName());
                    System.out.printf("...一天的发车次数%d...\n",k);
                    System.out.println(" ");

                } else if (transitRoute.getTransportMode() == "train") {
                    int k = 1;   // 记录第几个 departure
                    for(double departureTime=6*3600;departureTime<7*3600;departureTime+=10*60){
//                        subway 平峰时段 6:00-7:00,发车间隔10min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        subway 车辆及车辆编号
                        Id<Vehicle> trainId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle train = vehiclesFactory.createVehicle(trainId,trainType);
//                        地铁车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(train);
//                        发车补充：行车计划
                        departure.setVehicleId(trainId);
                        transitRoute.addDeparture(departure);
                        k++;

                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<9*3600;departureTime+=5*60){
//                        subway 高峰时段 7:00-9:00,发车间隔5min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        subway 车辆及车辆编号
                        Id<Vehicle> trainId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle train = vehiclesFactory.createVehicle(trainId,trainType);
//                        地铁车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(train);
//                        发车补充：行车计划
                        departure.setVehicleId(trainId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<17*3600;departureTime+=10*60){
//                        subway 平峰时段 9:00-17:00,发车间隔10min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        subway 车辆及车辆编号
                        Id<Vehicle> trainId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle train = vehiclesFactory.createVehicle(trainId,trainType);
//                        地铁车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(train);
//                        发车补充：行车计划
                        departure.setVehicleId(trainId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<19*3600;departureTime+=5*60){
//                        subway 高峰时段 17:00-19:00,发车间隔5min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        subway 车辆及车辆编号
                        Id<Vehicle> trainId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle train = vehiclesFactory.createVehicle(trainId,trainType);
//                        地铁车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(train);
//                        发车补充：行车计划
                        departure.setVehicleId(trainId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    for(double departureTime = departure.getDepartureTime();departureTime<22*3600;departureTime+=10*60){
//                        subway 高峰时段 19:00-22:00,发车间隔10min
//                        发车
                        departure = transitScheduleFactory.createDeparture(Id.create(String.valueOf(k),Departure.class),departureTime);
//                        subway 车辆及车辆编号
                        Id<Vehicle> trainId = Id.createVehicleId(transitRoute.getId()+"_"+k);
                        Vehicle train = vehiclesFactory.createVehicle(trainId,trainType);
//                        地铁车辆完善：添加车辆
                        scenario.getTransitVehicles().addVehicle(train);
//                        发车补充：行车计划
                        departure.setVehicleId(trainId);
                        transitRoute.addDeparture(departure);
                        k++;
                    }
                    System.out.printf("...subway route %s,描述 %s...\n",transitRoute.getId(),transitRoute.getDescription());
                    System.out.printf("...一天的发车次数%d...\n",k);
                    System.out.println(" ");
                } else {
                    System.out.println("...这是一条不能识别的记录...");
                    System.out.println(transitRoute.getTransportMode());
                }
            }
        }

//      写入文件
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(modifiedVehiclePath);
        new TransitScheduleWriterV2(scenario.getTransitSchedule()).write(modifiedSchedulePath);
    }

//    从上一个公交车辆的Id得到下一个公交车辆的Id。2021.1103 暂不需要该程序
    public static Id<Vehicle> getNextVehicle(Id<Vehicle> currentVehicleId){
        String[] vehicleId = currentVehicleId.toString().split("_");
        String routeId = vehicleId[0];  // route 编号
        int currentVehicleNo = Integer.parseInt(vehicleId[1]);  // 车辆序号，非车辆Id。前者从1,2,3,……,前者是后者的一部分

        if(currentVehicleNo==12){
//            假设一条公交 route 单程需要2h，公交发车频率最低为15min，即理论上，最多需要8辆公交车，这里设最大车辆数为12。
            currentVehicleNo = 0;
        }
        currentVehicleNo++;

        Id<Vehicle> nextVehicleId = Id.createVehicleId(routeId+"_"+currentVehicleId);

        return nextVehicleId;
    }

//

}