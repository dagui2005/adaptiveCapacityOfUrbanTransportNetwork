package org.matsim.pt;

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
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/* It is a copy of official example and just for examination.
 public transit , especially for bus.
 It can create transitSchedule, transitVehicle and ptNetwork files*/


public class CreatePtManuallyTest {

//    input files
//    private static final Path transitSchedule = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【软件】MatSim\\MATSim官方2017\\matsim-master\\examples\\scenarios (2017)\\pt-tutorial - CreatePtManuallyTest\\Input\\transitScheduleNull.xml");
//    The pt nodes in network file are useful, so you can change it to station csv.
    private static final Path networkPath  = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【软件】MatSim\\MATSim官方2017\\matsim-master\\examples\\scenarios (2017)\\pt-tutorial - CreatePtManuallyTest\\Input\\multimodalnetwork.xml");

//    output files
    private static final Path outputTransitSchedule = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【软件】MatSim\\MATSim官方2017\\matsim-master\\examples\\scenarios (2017)\\pt-tutorial - CreatePtManuallyTest\\Output\\transitSchedule.xml");
    private static final Path outputTransitVehicle = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【软件】MatSim\\MATSim官方2017\\matsim-master\\examples\\scenarios (2017)\\pt-tutorial - CreatePtManuallyTest\\Output\\transitVehicles.xml");
    private static final Path outputTransitNetwork = Paths.get("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【软件】MatSim\\MATSim官方2017\\matsim-master\\examples\\scenarios (2017)\\pt-tutorial - CreatePtManuallyTest\\Output\\transitNetwork.xml");

    public static void main(String[] args){
        new CreatePtManuallyTest().create();

    }

    private void create(){
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);

//        1. read in existing files.
//        new TransitScheduleReader(scenario).readFile(transitSchedule.toString());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkPath.toString());

//      2. create transit vehicles types.
        VehicleType subwayType = scenario.getTransitVehicles().getFactory().createVehicleType(Id.create("subway",VehicleType.class));
        subwayType.setLength(140);   // m as unit.
        subwayType.setPcuEquivalents(10);   // 1 by default. 2021.5.6, chunhong Li, subway is not affected, because it don't move in the same network with car or bus.
        subwayType.setWidth(3);
        subwayType.setNetworkMode("train");
        scenario.getTransitVehicles().addVehicleType(subwayType);   // Note: "getTransitVehicles" rather than "TransitVehicles"
//        2021.5.6, chunhong Li, other attributes are also needed.

        VehicleType busType = scenario.getTransitVehicles().getFactory().createVehicleType(Id.create("bus",VehicleType.class));
        busType.setLength(12);   // m as unit.
        busType.setPcuEquivalents(2);   // 1 by default. Bus is set as 2.
        busType.setWidth(2.55);
        busType.setNetworkMode("bus");
        scenario.getTransitVehicles().addVehicleType(busType);
//        2021.5.6, chunhong Li, other attributes are also needed.   These can be set as a method.

//      3. create some vehicles.
        Vehicle bus1 = scenario.getTransitVehicles().getFactory().createVehicle(Id.createVehicleId("bus1"),busType);
        scenario.getTransitVehicles().addVehicle(bus1);
        Vehicle bus2 = scenario.getTransitVehicles().getFactory().createVehicle(Id.createVehicleId("bus2"),busType);
        scenario.getTransitVehicles().addVehicle(bus2);

//      4. get the existing nodes we want to connect.
        Node node1 = scenario.getNetwork().getNodes().get(Id.createNodeId("1"));
        Node node2 = scenario.getNetwork().getNodes().get(Id.createNodeId("2"));
        Node node3 = scenario.getNetwork().getNodes().get(Id.createNodeId("3"));

//        5. connect nodes with links.
        Link link1 = createLink("link1",node1,node2,scenario.getNetwork().getFactory());
        Link link2 = createLink("link2",node2,node3,scenario.getNetwork().getFactory());
        scenario.getNetwork().addLink(link1);
        scenario.getNetwork().addLink(link2);

//      6. create a stops and add them to the scenario.
        TransitStopFacility stop1 = scenario.getTransitSchedule().getFactory().createTransitStopFacility(Id.create("stop1",TransitStopFacility.class),node1.getCoord(),true);
        stop1.setLinkId(link1.getId());
        stop1.setName("stop1");
        scenario.getTransitSchedule().addStopFacility(stop1);
        TransitStopFacility stop2 = scenario.getTransitSchedule().getFactory().createTransitStopFacility(Id.create("stop2",TransitStopFacility.class),node2.getCoord(),true);
        stop2.setLinkId(link2.getId());
        stop2.setName("stop2");
        scenario.getTransitSchedule().addStopFacility(stop2);
        TransitStopFacility stop3 = scenario.getTransitSchedule().getFactory().createTransitStopFacility(Id.create("stop3",TransitStopFacility.class),node3.getCoord(),true);
        stop3.setLinkId(link2.getId());
        stop3.setName("stop3");
        scenario.getTransitSchedule().addStopFacility(stop3);

//        7. create a line.
        TransitLine line1Test = scenario.getTransitSchedule().getFactory().createTransitLine(Id.create("line1Test",TransitLine.class));
        line1Test.setName("line1Test");

//        8. create transit route stops.
        List<TransitRouteStop> transitRouteStops = Arrays.asList(
                scenario.getTransitSchedule().getFactory().createTransitRouteStop(stop1,0,0),
                scenario.getTransitSchedule().getFactory().createTransitRouteStop(stop2,1*3600,1*3600+1*60),
                scenario.getTransitSchedule().getFactory().createTransitRouteStop(stop3,2*3600,2*3600)
        );



//        9. create a route.
        TransitRoute route11Test = scenario.getTransitSchedule().getFactory().createTransitRoute(
                Id.create("route11Test",TransitRoute.class),
                RouteUtils.createNetworkRoute(Arrays.asList(link1.getId(),link2.getId()),scenario.getNetwork()),  // links
                transitRouteStops,    // stops
                TransportMode.pt
        );

//        10. create two departures for the transit route.
        Departure departure1 = scenario.getTransitSchedule().getFactory().createDeparture(Id.create("departure1",Departure.class),0);
        departure1.setVehicleId(bus1.getId());
        Departure departure2 = scenario.getTransitSchedule().getFactory().createDeparture(Id.create("departure2",Departure.class),1800);
        departure2.setVehicleId(bus2.getId());

//        11. add the elements to the scenario.
        route11Test.addDeparture(departure1);
        route11Test.addDeparture(departure2);
        line1Test.addRoute(route11Test);
        scenario.getTransitSchedule().addTransitLine(line1Test);

//      12. write out the files.
        new NetworkWriter(scenario.getNetwork()).write(outputTransitNetwork.toString());
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(outputTransitVehicle.toString());
        new TransitScheduleWriterV2(scenario.getTransitSchedule()).write(outputTransitSchedule.toString());

    }

    private Link createLink(String id, Node from, Node to, NetworkFactory factory){
        Link link = factory.createLink(Id.createLinkId(id),from,to);
        link.setAllowedModes(new HashSet<>(Collections.singletonList(TransportMode.pt)));
        link.setCapacity(600);
        link.setLength(NetworkUtils.getEuclideanDistance(link.getFromNode().getCoord(),link.getToNode().getCoord()));
        link.setFreespeed(16.67);  // 16.67 m/s = 60 km/h
        return link;
    }

}
