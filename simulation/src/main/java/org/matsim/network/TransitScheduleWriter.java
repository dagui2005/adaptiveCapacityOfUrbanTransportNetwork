package org.matsim.network;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.util.*;

/**
 * TransitScheduleWriter
 *
 * 满足原始五点需求的版本：
 * 1. 记录 EPSG 坐标系
 * 2. 从 shapefile 自动生成公交、地铁站点
 * 3. 自动生成 minimalTransferTimes
 * 4. 从 shapefile 生成公交与地铁线路（含时刻表）
 * 5. 输出 transitSchedule.xml
 */
public class TransitScheduleWriter {

    private final Scenario scenario;
    private final TransitScheduleFactory factory;
    private final TransitSchedule schedule;
    private final Network network;

    private String coordinateSystem = "EPSG:4490"; // 默认 CRS
    private final Map<String, TransitStopFacility> stopMap = new HashMap<>();

    public TransitScheduleWriter(String networkFile) {
        Config config = ConfigUtils.createConfig();
        config.global().setCoordinateSystem(coordinateSystem);
        this.scenario = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(this.scenario.getNetwork()).readFile(networkFile);
        this.network = this.scenario.getNetwork();
        this.schedule = scenario.getTransitSchedule();
        this.factory = this.schedule.getFactory();
    }

    /** 设置 CRS 坐标系 **/
    public void setCRS(String epsg) {
        this.coordinateSystem = epsg;
        this.scenario.getConfig().global().setCoordinateSystem(epsg);
    }

    /** 从 shapefile 读取公交和地铁站点 **/
    public void loadStopsFromShp(String busStopShp, String metroStationShp) throws Exception {
        System.out.println("🔹 Loading bus stops from: " + busStopShp);
        readStops(busStopShp, "bus");
        System.out.println("🔹 Loading metro stations from: " + metroStationShp);
        readStops(metroStationShp, "metro");
        System.out.println("✅ Stops loaded: " + stopMap.size());
    }

    private void readStops(String shpPath, String mode) throws Exception {
        File file = new File(shpPath);
        Map<String, Object> params = new HashMap<>();
        params.put("url", file.toURI().toURL());
        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) {
            throw new RuntimeException("无法读取Shapefile: " + shpPath);
        }

        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureCollection features = dataStore.getFeatureSource(typeName).getFeatures();
        SimpleFeatureIterator it = features.features();

        while (it.hasNext()) {
            SimpleFeature f = it.next();
            String id = safeString(f.getAttribute("id"));
            String name = safeString(f.getAttribute("station"));
            if (name.isEmpty()) name = mode.equals("metro") ? "PT_STATION" : "PT_STOP";

            Object xObj = f.getAttribute("lng");
            Object yObj = f.getAttribute("lat");
            if (xObj == null || yObj == null) continue;

            double x = Double.parseDouble(xObj.toString());
            double y = Double.parseDouble(yObj.toString());
            String linkIdStr = safeString(f.getAttribute("link_id"));

            Coord coord = new Coord(x, y);
            Id<Link> linkId = Id.createLinkId(linkIdStr);
            Id<TransitStopFacility> stopId = Id.create(id + "_" + linkIdStr, TransitStopFacility.class);

            if (!stopMap.containsKey(stopId.toString())) {
                TransitStopFacility stop = factory.createTransitStopFacility(stopId, coord, false);
                stop.setLinkId(linkId);
                stop.setName(name);
                schedule.addStopFacility(stop);
                stopMap.put(stopId.toString(), stop);
            }
        }
        it.close();
        dataStore.dispose();
    }

    /** 自动生成 minimalTransferTimes **/
    public void autoAddTransferTimes(String busStopShp) throws Exception {
        System.out.println("🔹 Generating transfer times...");
        Map<String, List<TransitStopFacility>> groupedById = new HashMap<>();

        // 按 busstop shp 中的 id 分组
        File file = new File(busStopShp);
        Map<String, Object> params = new HashMap<>();
        params.put("url", file.toURI().toURL());
        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) return;

        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureCollection features = dataStore.getFeatureSource(typeName).getFeatures();
        SimpleFeatureIterator it = features.features();
        while (it.hasNext()) {
            SimpleFeature f = it.next();
            String id = safeString(f.getAttribute("id"));
            String linkIdStr = safeString(f.getAttribute("link_id"));
            TransitStopFacility stop = stopMap.get(id + "_" + linkIdStr);
            if (stop != null) {
                groupedById.computeIfAbsent(id, k -> new ArrayList<>()).add(stop);
            }
        }
        it.close();
        dataStore.dispose();

        // 生成 transferTime = 0
        MinimalTransferTimes transferTimes = schedule.getMinimalTransferTimes();
        for (List<TransitStopFacility> group : groupedById.values()) {
            for (TransitStopFacility s1 : group) {
                for (TransitStopFacility s2 : group) {
                    if (!s1.equals(s2)) {
                        transferTimes.set(s1.getId(), s2.getId(), 0.0);
                    }
                }
            }
        }
        System.out.println("✅ Transfer times added: " + groupedById.size());
    }

    /** 从 busline.shp 创建公交线路 **/
    public void loadBusLinesFromShp(String busLineShp, Map<String, List<Id<Link>>> linkPaths) throws Exception {
        System.out.println("🔹 Loading bus lines from: " + busLineShp);
        File file = new File(busLineShp);
        Map<String, Object> params = new HashMap<>();
        params.put("url", file.toURI().toURL());
        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) return;

        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureCollection features = dataStore.getFeatureSource(typeName).getFeatures();
        SimpleFeatureIterator it = features.features();

        Map<String, List<SimpleFeature>> linesByShortName = new HashMap<>();
        while (it.hasNext()) {
            SimpleFeature f = it.next();
            String shortName = safeString(f.getAttribute("short_name"));
            linesByShortName.computeIfAbsent(shortName, k -> new ArrayList<>()).add(f);
        }
        it.close();
        dataStore.dispose();

        for (String shortName : linesByShortName.keySet()) {
            TransitLine line = factory.createTransitLine(Id.create(shortName, TransitLine.class));

            for (SimpleFeature f : linesByShortName.get(shortName)) {
                String lineName = safeString(f.getAttribute("line_name"));
                List<Id<Link>> routeLinks = linkPaths.get(lineName);
                if (routeLinks == null || routeLinks.isEmpty()) continue;

                // 构造停靠点（路径两端）
                List<TransitStopFacility> stops = new ArrayList<>();
                for (TransitStopFacility stop : schedule.getFacilities().values()) {
                    if (routeLinks.contains(stop.getLinkId())) {
                        stops.add(stop);
                    }
                }
                if (stops.size() < 2) continue;

                TransitRouteStop[] routeStops = new TransitRouteStop[stops.size()];
                double offset = 0;
                for (int i = 0; i < stops.size(); i++) {
                    routeStops[i] = factory.createTransitRouteStop(stops.get(i), offset, offset + 60);
                    if (i < stops.size() - 1) {
                        Link l = network.getLinks().get(routeLinks.get(i));
                        offset += l.getLength() / (12000.0 / 3600.0); // 平均速度12km/h
                    }
                }

                NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(
                        routeLinks.get(0), routeLinks, routeLinks.get(routeLinks.size() - 1));

                TransitRoute tr = factory.createTransitRoute(Id.create(lineName, TransitRoute.class),
                        route, Arrays.asList(routeStops), "bus");

                // 发车时刻逻辑
                int[] times = getBusTimes(lineName);
                int depId = 1;
                for (int t = times[0]; t <= times[1]; t += 15 * 60) {
                    Departure dep = factory.createDeparture(Id.create("dep" + depId, Departure.class), t);
                    dep.setVehicleId(Id.create(lineName + "_" + String.format("%03d", depId), org.matsim.vehicles.Vehicle.class));
                    tr.addDeparture(dep);
                    depId++;
                }

                line.addRoute(tr);
            }
            schedule.addTransitLine(line);
        }
        System.out.println("✅ Bus lines loaded: " + linesByShortName.size());
    }

    private int[] getBusTimes(String lineName) {
        boolean isNight = lineName.startsWith("夜");
        int start = isNight ? 22 * 3600 + 30 * 60 : 6 * 3600 + 30 * 60;
        int end = isNight ? 24 * 3600 + 30 * 60 : 22 * 3600 + 30 * 60;
        return new int[]{start, end};
    }

    /** 从 metro.shp 创建地铁线路 **/
    public void loadMetroLinesFromShp(String metroLineShp, Map<String, List<Id<Link>>> linkPaths) throws Exception {
        System.out.println("🔹 Loading metro lines from: " + metroLineShp);
        File file = new File(metroLineShp);
        Map<String, Object> params = new HashMap<>();
        params.put("url", file.toURI().toURL());
        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) return;

        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureCollection features = dataStore.getFeatureSource(typeName).getFeatures();
        SimpleFeatureIterator it = features.features();

        while (it.hasNext()) {
            SimpleFeature f = it.next();
            String lineName = safeString(f.getAttribute("LINE_NAME"));
            List<Id<Link>> routeLinks = linkPaths.get(lineName);
            if (routeLinks == null || routeLinks.isEmpty()) continue;

            TransitLine line = factory.createTransitLine(Id.create(lineName, TransitLine.class));

            List<TransitStopFacility> stops = new ArrayList<>();
            for (TransitStopFacility stop : schedule.getFacilities().values()) {
                if (routeLinks.contains(stop.getLinkId())) {
                    stops.add(stop);
                }
            }
            if (stops.size() < 2) continue;

            TransitRouteStop[] routeStops = new TransitRouteStop[stops.size()];
            double offset = 0;
            double speed = (lineName.contains("18") || lineName.contains("22")) ? 160000.0 / 3600.0 : 40000.0 / 3600.0;
            for (int i = 0; i < stops.size(); i++) {
                routeStops[i] = factory.createTransitRouteStop(stops.get(i), offset, offset + 40);
                if (i < stops.size() - 1) {
                    Link l = network.getLinks().get(routeLinks.get(i));
                    offset += l.getLength() / speed;
                }
            }

            NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(
                    routeLinks.get(0), routeLinks, routeLinks.get(routeLinks.size() - 1));

            TransitRoute tr = factory.createTransitRoute(Id.create(lineName + "_01", TransitRoute.class),
                    route, Arrays.asList(routeStops), "train");

            int depId = 1;
            for (int t = 6 * 3600 + 30 * 60; t <= 22 * 3600 + 30 * 60; t += 5 * 60) {
                Departure dep = factory.createDeparture(Id.create("dep" + depId, Departure.class), t);
                dep.setVehicleId(Id.create(lineName + "_" + String.format("%03d", depId), org.matsim.vehicles.Vehicle.class));
                tr.addDeparture(dep);
                depId++;
            }

            line.addRoute(tr);
            schedule.addTransitLine(line);
        }
        it.close();
        dataStore.dispose();
        System.out.println("✅ Metro lines loaded.");
    }

    /** 输出 XML **/
    public void writeSchedule(String outFile) {
        new TransitScheduleWriterV2(schedule).write(outFile);
        System.out.println("✅ Transit schedule written to: " + outFile);
    }

    private String safeString(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
