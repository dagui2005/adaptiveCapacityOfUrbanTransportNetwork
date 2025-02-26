package org.matsim.pt;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.network.WGS2Mercator;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/* It is a improvement of official example.
 Input: pt info and network.
 Output: transitSchedule, transitVehicle (and ptNetwork files)*/


public class CreatePtManually {

    //    input files
//    The pt nodes in network file are useful, so you can change it to station csv.
    private static final Path networkPath  = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市路网\\NetworkZhengMultiModal.xml");
    private static final Path ptLinePath  = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁\\1号线.csv");

    //    output files
    private static final Path outputTransitSchedule = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁时刻表\\transitSchedule.xml");
    private static final Path outputTransitVehicle = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁时刻表\\transitVehicles.xml");
//    private static final Path outputTransitNetwork = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【软件】MatSim\\MATSim官方2017\\matsim-master\\examples\\scenarios (2017)\\pt-tutorial - CreatePtManuallyTest\\Output\\transitNetwork.xml");

    public static void main(String[] args){
        new CreatePtManually().create();


//        Config config = ConfigUtils.createConfig();
//        Scenario scenario = ScenarioUtils.createScenario(config);
//        List<TransitStopFacility> stops = new CreatePtManually().createTSFList(2,scenario);
//        for(TransitStopFacility stop: stops){
//            System.out.println(stop.getIsBlockingLane());
//            System.out.println(stop.getId());
//        }

    }

    private void create(){
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkPath.toString());

//        1. create an arrayList of TransitStopFacility.
        List<TransitStopFacility> stations = null;  // variables in "try-catch" are local variables. But the modifications made by "try-catch" can be saved.
        int stationNum = 0;
        try{
            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(ptLinePath.toString()), "utf-8");
            BufferedReader reader = new BufferedReader(inputStreamReader);
            stationNum = (int) reader.lines().count() - 1;
            System.out.println("the count of the station of the line is " + stationNum);
            stations = new CreatePtManually().createTSFList(stationNum,scenario);

            //            close the stream.
            reader.close();
            inputStreamReader.close();
        }catch (Exception e){
            e.printStackTrace();
        }

//        2. read in station info in a line from a csv file.
        try {
            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(ptLinePath.toString()), "utf-8");
            BufferedReader reader = new BufferedReader(inputStreamReader);
            System.out.println("The first line of the csv file is as follows:");
            System.out.println(reader.readLine());  //  the first line of csv file.

            WGS2Mercator wgs2Mercator = new WGS2Mercator();  // coordination transformation
            String line;
            int k = 0;

            //            read lines one by one.
            while ((line = reader.readLine()) !=null){
                String item[] = line.split(",");
                //                name
                String name = item[1];
                //                longitude
                String longitude = item[2];
                //                latitude
                String latitude = item[3];
                //                coordination transformation
                Coord coord = wgs2Mercator.transform(new Coord(Double.parseDouble(longitude),Double.parseDouble(latitude)));

//                3. set stations the true name, coordination and linkId.
                stations.get(k).setName(name);   // 2021.5.7, Chunhong Li, 中文输出乱码
                stations.get(k).setCoord(coord);
                if((k+1)==stationNum){  // the last station
                    stations.get(k).setLinkId(Id.createLinkId("S1-"+String.valueOf(k)+"-1-"+"S1-"+String.valueOf(k+1)+"-1"));
                }else{
                    stations.get(k).setLinkId(Id.createLinkId("S1-"+String.valueOf(k+1)+"-1-"+"S1-"+String.valueOf(k+2)+"-1"));
                }
                scenario.getTransitSchedule().addStopFacility(stations.get(k));
                k++;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

//        7. create a line.
        TransitLine line1Test = scenario.getTransitSchedule().getFactory().createTransitLine(Id.create("line1Test",TransitLine.class));
        line1Test.setName("地铁1号线");

//        8. create transit route stops.
        List<TransitRouteStop> transitRouteStops = createTRSList(stations,scenario);

//        9. create a route.
        TransitRoute routeA2BTest = scenario.getTransitSchedule().getFactory().createTransitRoute(
                Id.create("routeA2BTest",TransitRoute.class),
                RouteUtils.createNetworkRoute(new CreatePtManually().createLinksList(stationNum),scenario.getNetwork()),  // links
                transitRouteStops,    // stops
                TransportMode.train
        );

//      10. create transit vehicles types.
        VehicleType subwayType = scenario.getTransitVehicles().getFactory().createVehicleType(Id.create("subway",VehicleType.class));
        subwayType.setLength(140);   // m as unit.
        subwayType.setPcuEquivalents(10);   // 1 by default. 2021.5.6, chunhong Li, subway is not affected, because it don't move in the same network with car or bus.
        subwayType.setWidth(3);
        subwayType.setNetworkMode("train");    //  2021.4.7, Chunhong Li, 似乎没有办法更改车辆容量，因此选择手动更改。
        scenario.getTransitVehicles().addVehicleType(subwayType);   // Note: "getTransitVehicles" rather than "TransitVehicles"
//        2021.5.6, chunhong Li, other attributes are also needed.
        VehicleType busType = scenario.getTransitVehicles().getFactory().createVehicleType(Id.create("bus",VehicleType.class));
        busType.setLength(12);   // m as unit.
        busType.setPcuEquivalents(2);   // 1 by default. Bus is set as 2.
        busType.setWidth(2.55);
        busType.setNetworkMode("bus");
        scenario.getTransitVehicles().addVehicleType(busType);
//        2021.5.6, chunhong Li, other attributes are also needed.   These can be set as a method.

//      11. create some vehicles.
        Vehicle subway1 = scenario.getTransitVehicles().getFactory().createVehicle(Id.createVehicleId("subway1"),subwayType);
        scenario.getTransitVehicles().addVehicle(subway1);
        Vehicle subway2 = scenario.getTransitVehicles().getFactory().createVehicle(Id.createVehicleId("subway2"),subwayType);
        scenario.getTransitVehicles().addVehicle(subway2);

//        12. create two departures for the transit route.
        Departure departure1 = scenario.getTransitSchedule().getFactory().createDeparture(Id.create("departure1",Departure.class),0);
        departure1.setVehicleId(subway1.getId());
        Departure departure2 = scenario.getTransitSchedule().getFactory().createDeparture(Id.create("departure2",Departure.class),1800);
        departure2.setVehicleId(subway2.getId());

//        13. add the elements to the scenario.
        routeA2BTest.addDeparture(departure1);
        routeA2BTest.addDeparture(departure2);
        line1Test.addRoute(routeA2BTest);
        scenario.getTransitSchedule().addTransitLine(line1Test);

        new TransitScheduleWriterV2(scenario.getTransitSchedule()).write(outputTransitSchedule.toString());
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(outputTransitVehicle.toString());

    }

//      Sub 1 : create links.
    private Link createLink(String id, Node from, Node to, NetworkFactory factory){
        Link link = factory.createLink(Id.createLinkId(id),from,to);
        link.setAllowedModes(new HashSet<>(Collections.singletonList(TransportMode.pt)));
        link.setCapacity(600);
        link.setLength(NetworkUtils.getEuclideanDistance(link.getFromNode().getCoord(),link.getToNode().getCoord()));
        link.setFreespeed(16.67);  // 16.67 m/s = 60 km/h
        return link;
    }

//      Sub 2: create transitStopFacility list.   ---->  transitSchedule --- transitStops  --- stopFacility
    private List<TransitStopFacility> createTSFList(int num, Scenario scenario){
        List<TransitStopFacility> TSFList = new ArrayList<TransitStopFacility>();
        int k = 1;
        Coord coord = new Coord(0,0);
        while (k<= num){
            TransitStopFacility station = scenario.getTransitSchedule().getFactory().createTransitStopFacility(Id.create("station"+k,TransitStopFacility.class),coord, true);
            TSFList.add(station);   // if "TSFList" is set "null" originally, there will be "nullPointerException".
            k++;
        }
        return TSFList;
    }

//      Sub 3: create transitRouteStop list.   ---->   transitSchedule --- transitLine ---  transitRoute  --- routeProfile
    private List<TransitRouteStop> createTRSList(List<TransitStopFacility> transitStopFacilities,Scenario scenario){
        List<TransitRouteStop> transitRouteStops = new ArrayList<TransitRouteStop>();
        int k = 1;
        int arriveTime = 0;
        int departureTime = 0;
        for(TransitStopFacility transitStopFacility: transitStopFacilities){
            transitRouteStops.add(scenario.getTransitSchedule().getFactory().createTransitRouteStop(transitStopFacility,arriveTime,departureTime));
            arriveTime = departureTime + 5*60;   // travel time between two stations: 20min
            departureTime = arriveTime + 1*60;    // dwell time: 2min.
            k++;
        }
        return transitRouteStops;
    }

//      Sub 4: create links list which the bus or subway serves sequentially in a transitRoute.
//      ---->   transitSchedule --- transitLine ---  transitRoute  ---  route
//      2021.5.17, Chunhong Li, 此处可以改进：本程序添加生成轨道网络的代码，并对此方法进行完善
    private List<Id<Link>> createLinksList(int stationNum){
        List<Id<Link>> linksList = new ArrayList<Id<Link>>();
        int k = 1;
        while(k<=stationNum){
            linksList.add(Id.createLinkId("S" + "1" + "-" + String.valueOf(k) + "-" + "1" + "-" + "S" + "1" + "-" + String.valueOf(k+1) + "-" + "1"));
            k++;
        }
        return linksList;
    }


}
