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

import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;


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
    private Map<String, Id<Link>> stopToLinkMapping = new HashMap<>();

    private String coordinateSystem = "EPSG:32649"; // 默认 CRS
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
    public void loadStopsFromShp(String busStopShp, String metroStationShp, Map<String, Id<Link>> stopToLinkMapping) throws Exception {
        this.stopToLinkMapping = stopToLinkMapping != null ? stopToLinkMapping : new HashMap<>();
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

        // 获取shapefile的坐标系
        CoordinateReferenceSystem sourceCRS = features.getSchema().getCoordinateReferenceSystem();
        CoordinateReferenceSystem targetCRS = CRS.decode(this.coordinateSystem);

        // 如果shapefile没有坐标系信息，默认为WGS84
        if (sourceCRS == null) {
            sourceCRS = CRS.decode("EPSG:4326"); // WGS84
        }

        // 创建坐标转换器
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);

        SimpleFeatureIterator it = features.features();

        while (it.hasNext()) {
            SimpleFeature f = it.next();
            // 根据模式不同读取不同的属性字段
            String id, name;
            if (mode.equals("metro")) {
                // 地铁站点使用POIID和STATION_NA字段
                id = safeString(f.getAttribute("POIID"));
                name = safeString(f.getAttribute("STATION_NA"));
                if (name.isEmpty()) name = "PT_STATION";
            } else {
                // 公交站点使用id和station字段
                id = safeString(f.getAttribute("id"));
                name = safeString(f.getAttribute("station"));
                if (name.isEmpty()) name = "PT_STOP";
            }
            double x, y;

            // 优先从lng、lat属性获取坐标
            Object xObj = f.getAttribute("lng");
            Object yObj = f.getAttribute("lat");
            if (xObj != null && yObj != null) {
                x = Double.parseDouble(xObj.toString());
                y = Double.parseDouble(yObj.toString());
            } else {
                // 如果lng、lat属性为空，再从几何对象获取坐标
                Object geometryObj = f.getDefaultGeometry();
                if (geometryObj != null && geometryObj instanceof Geometry) {
                    Geometry geometry = (Geometry) geometryObj;
                    Coordinate coord = geometry.getCoordinate();
                    if (coord != null) {
                        x = coord.x;
                        y = coord.y;
                    } else {
                        continue; // 如果几何对象也没有有效坐标，则跳过该要素
                    }
                } else {
                    continue; // 如果既没有lng/lat属性也没有几何对象，则跳过该要素
                }
            }

            // 执行坐标转换
            Coordinate sourceCoord = new Coordinate(x, y);
            Coordinate targetCoord = new Coordinate();
            JTS.transform(sourceCoord, targetCoord, transform);

            // 根据模式不同使用不同的属性字段
            String stopId;
            if (mode.equals("metro")) {
                // 地铁站点使用POIID字段
                stopId = safeString(f.getAttribute("POIID"));
            } else {
                // 公交站点使用id字段
                stopId = safeString(f.getAttribute("id"));
            }
            // 对于地铁站点，需要查找所有匹配的linkId
            List<Id<Link>> linkIds = new ArrayList<>();
            if (mode.equals("metro")) {
                // 查找所有以该POIID开头的映射项
                for (Map.Entry<String, Id<Link>> entry : stopToLinkMapping.entrySet()) {
                    if (entry.getKey().startsWith(stopId + "X")) {
                        linkIds.add(entry.getValue());
                    }
                }
            } else {
                // 公交站点保持原有逻辑
                Id<Link> singleLinkId = stopToLinkMapping.get(stopId);
                if (singleLinkId != null) {
                    linkIds.add(singleLinkId);
                }
            }

            if (linkIds.isEmpty()) {
                System.out.println("⚠️ 跳过站点 " + stopId + "：未找到对应的link_id");
                continue;
            }

            // 为每个匹配的linkId创建站点设施
            for (int i = 0; i < linkIds.size(); i++) {
                Id<Link> linkId = linkIds.get(i);
                String linkIdStr = linkId.toString();
                String suffix = linkIds.size() > 1 ? "_" + (i + 1) : ""; // 多个链接时添加后缀

                Coord coord = new Coord(targetCoord.x, targetCoord.y);
                Id<TransitStopFacility> newStopId = Id.create(id + suffix + "_" + linkIdStr, TransitStopFacility.class);

                if (!stopMap.containsKey(newStopId.toString())) {
                    TransitStopFacility stop = factory.createTransitStopFacility(newStopId, coord, false);
                    stop.setLinkId(linkId);
                    stop.setName(name);
                    schedule.addStopFacility(stop);
                    stopMap.put(newStopId.toString(), stop);
                }
            }
        }
        it.close();
        dataStore.dispose();
    }



    /** 自动生成 minimalTransferTimes **/
    public void autoAddTransferTimes(Map<String, Id<Link>> stopToLinkMapping) throws Exception {
        System.out.println("🔹 Generating transfer times...");
        Map<String, List<TransitStopFacility>> groupedById = new HashMap<>();

        // 从 stopToLinkMapping 中分组站点
        for (Map.Entry<String, Id<Link>> entry : stopToLinkMapping.entrySet()) {
            String stopId = entry.getKey();
            // 从 stopMap 中查找对应的站点设施
            for (TransitStopFacility stop : stopMap.values()) {
                if (stop.getId().toString().startsWith(stopId + "_")) {
                    groupedById.computeIfAbsent(stopId, k -> new ArrayList<>()).add(stop);
                    break;
                }
            }
        }

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
// 修改 loadBusLinesFromShp 方法签名
    public void loadBusLinesFromShp(String busLineShp, Map<String, BusNetworkIntegrator.BusLinePathInfo> linePathInfos) throws Exception {
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
                BusNetworkIntegrator.BusLinePathInfo pathInfo = linePathInfos.get(lineName);
                if (pathInfo == null || pathInfo.fullPath.isEmpty()) continue;

                List<Id<Link>> routeLinks = pathInfo.fullPath;
                List<Integer> stopPositions = pathInfo.stopPositions;

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
                    // 确保既有下一个站点，又有对应的link
                    if (i < stops.size() - 1) {
                        double totalLength = calculatePathSegmentLength(routeLinks, stopPositions, i);
                        if (totalLength > 0) {
                            offset += totalLength / (12000.0 / 3600.0); // 平均速度12km/h
                        } else {
                            // 备用方案：如果无法计算路径长度，使用默认值
                            offset += 60.0; // 默认60秒
                        }
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
    /**
     * 计算两个相邻站点之间的路径段总长度
     *
     * @param routeLinks 整条线路的完整路径链接列表
//     * @param stops 按顺序排列的站点列表
     * @param stopIndex 当前站点索引
     * @return 从当前站点到下一站点的路径段总长度(米)
     */
    private double calculatePathSegmentLength(List<Id<Link>> routeLinks,
                                              List<Integer> stopPositions,
                                              int stopIndex) {
        if (stopIndex >= stopPositions.size() - 1) {
            return 0.0;
        }

        // 直接使用预计算的位置信息
        int currentPos = stopPositions.get(stopIndex);
        int nextPos = stopPositions.get(stopIndex + 1);

        // 边界检查
        if (currentPos < 0 || nextPos > routeLinks.size() || currentPos >= nextPos) {
            System.err.println("Warning: Invalid stop positions for path segment calculation");
            return 0.0;
        }

        // 计算路径段总长度
        double totalLength = 0.0;
        for (int i = currentPos; i < nextPos; i++) {
            Id<Link> linkId = routeLinks.get(i);
            Link link = network.getLinks().get(linkId);
            if (link != null) {
                totalLength += link.getLength();
            }
        }

        return totalLength;
    }


    /** 从 metro.shp 创建地铁线路 **/
    /** 从 metro.shp 创建地铁线路 **/
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

        // 用于跟踪已处理的FID_road，但对于FID_road=0需要特殊处理
        Set<String> processedFidRoads = new HashSet<>();
        int fidRoadZeroCounter = 0; // 用于跟踪FID_road=0的线路索引

        while (it.hasNext()) {
            SimpleFeature f = it.next();
            Object fldObj = f.getAttribute("FID_road");
            String fidRoad = (fldObj != null) ? fldObj.toString() : "NO_FID";

            // 对于FID_road=0的特殊处理
            if ("0".equals(fidRoad)) {
                // 为每个FID_road=0的线段创建独立线路
                fidRoadZeroCounter++;
                List<Id<Link>> routeLinks = findSpecificMetroRouteLink(linkPaths, fidRoad, fidRoadZeroCounter);
                List<Id<Link>> reverseRouteLinks = findSpecificMetroReverseRouteLink(linkPaths, fidRoad, fidRoadZeroCounter);

                if (routeLinks == null || routeLinks.isEmpty()) {
                    System.out.println("⚠️ 未找到地铁线路 FID_road=0 chain" + fidRoadZeroCounter + " 的路径信息");
                    continue;
                }

                processMetroLine("Metro_0_" + fidRoadZeroCounter + "_fwd", routeLinks, fidRoad);
                if (reverseRouteLinks != null && !reverseRouteLinks.isEmpty()) {
                    processMetroLine("Metro_0_" + fidRoadZeroCounter + "_rev", reverseRouteLinks, fidRoad);
                }
            } else {
                // 避免重复处理相同的FID_road（除了0）
                if (processedFidRoads.contains(fidRoad)) {
                    continue;
                }
                processedFidRoads.add(fidRoad);

                // 查找匹配的地铁线路路径
                List<Id<Link>> routeLinks = findMetroRouteLinks(linkPaths, fidRoad, "_fwd");
                List<Id<Link>> reverseRouteLinks = findMetroRouteLinks(linkPaths, fidRoad, "_rev");

                if (routeLinks == null || routeLinks.isEmpty()) {
                    System.out.println("⚠️ 未找到地铁线路 FID_road=" + fidRoad + " 的路径信息");
                    continue;
                }

                processMetroLine("Metro_" + fidRoad + "_fwd", routeLinks, fidRoad);
                if (reverseRouteLinks != null && !reverseRouteLinks.isEmpty()) {
                    processMetroLine("Metro_" + fidRoad + "_rev", reverseRouteLinks, fidRoad);
                }
            }
        }
        it.close();
        dataStore.dispose();
        System.out.println("✅ Metro lines loaded.");
    }

    // 处理单条地铁线路的通用方法
    // 修改 processMetroLine 方法中的站点处理逻辑
    private void processMetroLine(String lineId, List<Id<Link>> routeLinks, String fidRoad) {
        // 使用线路ID作为线路名称
        TransitLine line = factory.createTransitLine(Id.create(lineId, TransitLine.class));

        // 按照 routeLinks 的顺序查找和排序站点
        List<TransitStopFacility> stops = findAndOrderStopsForRoute(routeLinks);

        if (stops.size() < 2) return;

        TransitRouteStop[] routeStops = new TransitRouteStop[stops.size()];
        double offset = 0;
        double speed = (fidRoad.contains("14") || fidRoad.contains("16")) ? 160000.0 / 3600.0 : 40000.0 / 3600.0;
        for (int i = 0; i < stops.size(); i++) {
            routeStops[i] = factory.createTransitRouteStop(stops.get(i), offset, offset + 40);
            // 确保索引不越界
            if (i < stops.size() - 1 && i < routeLinks.size()) {
                Link l = network.getLinks().get(routeLinks.get(i));
                if (l != null) {
                    offset += l.getLength() / speed;
                }
            }
        }

        NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(
                routeLinks.get(0), routeLinks, routeLinks.get(routeLinks.size() - 1));

        TransitRoute tr = factory.createTransitRoute(Id.create(lineId + "_01", TransitRoute.class),
                route, Arrays.asList(routeStops), "train");

        int depId = 1;
        for (int t = 6 * 3600 + 30 * 60; t <= 22 * 3600 + 30 * 60; t += 5 * 60) {
            Departure dep = factory.createDeparture(Id.create("dep" + depId, Departure.class), t);
            dep.setVehicleId(Id.create(lineId + "_" + String.format("%03d", depId), org.matsim.vehicles.Vehicle.class));
            tr.addDeparture(dep);
            depId++;
        }

        line.addRoute(tr);
        // 检查是否已存在同名线路，避免重复添加
        if (!schedule.getTransitLines().containsKey(line.getId())) {
            schedule.addTransitLine(line);
        } else {
            System.out.println("⚠️ 线路 " + line.getId() + " 已存在，跳过添加");
        }
    }

    // 添加新方法：按 routeLinks 顺序查找和排序站点
    private List<TransitStopFacility> findAndOrderStopsForRoute(List<Id<Link>> routeLinks) {
        List<TransitStopFacility> orderedStops = new ArrayList<>();

        if (routeLinks.isEmpty()) {
            return orderedStops;
        }

        // 创建一个从 LinkId 到站点的映射，便于快速查找
        Map<Id<Link>, TransitStopFacility> linkToStopMap = new HashMap<>();
        for (TransitStopFacility stop : schedule.getFacilities().values()) {
            linkToStopMap.put(stop.getLinkId(), stop);
        }

        // 按照 routeLinks 的顺序查找站点
        TransitStopFacility previousStop = null;
        for (Id<Link> linkId : routeLinks) {
            TransitStopFacility stop = linkToStopMap.get(linkId);
            if (stop != null && !stop.equals(previousStop)) {
                // 只有当找到新站点或与前一个不同的站点时才添加
                orderedStops.add(stop);
                previousStop = stop;
            }
        }

        // 如果按链接查找没有找到足够的站点，则回退到原有的包含检查
        if (orderedStops.size() < 2) {
            orderedStops.clear();
            for (TransitStopFacility stop : schedule.getFacilities().values()) {
                if (routeLinks.contains(stop.getLinkId())) {
                    orderedStops.add(stop);
                }
            }
        }

        System.out.println("找到 " + orderedStops.size() + " 个站点");
        return orderedStops;
    }


   // 修改辅助方法来查找地铁线路路径
    private List<Id<Link>> findMetroRouteLinks(Map<String, List<Id<Link>>> linkPaths, String fidRoad, String direction) {
        // 查找所有以"FLD_" + fidRoad开头的路径
        List<Id<Link>> allLinks = new ArrayList<>();

        for (Map.Entry<String, List<Id<Link>>> entry : linkPaths.entrySet()) {
            String key = entry.getKey();
            // 检查键是否以"FLD_" + fidRoad开头且以指定方向结尾
            if (key.startsWith("FLD_" + fidRoad + "_chain") && key.endsWith(direction)) {
                allLinks.addAll(entry.getValue());
            }
        }

        // 如果找到了多个chain，需要按顺序合并
        return allLinks.isEmpty() ? null : allLinks;
    }

    // 查找特定的FID_road=0线路（正向）
    private List<Id<Link>> findSpecificMetroRouteLink(Map<String, List<Id<Link>>> linkPaths, String fidRoad, int chainIndex) {
        String key = "FLD_" + fidRoad + "_chain" + chainIndex + "_fwd";
        return linkPaths.get(key);
    }

    // 查找特定的FID_road=0线路（反向）
    private List<Id<Link>> findSpecificMetroReverseRouteLink(Map<String, List<Id<Link>>> linkPaths, String fidRoad, int chainIndex) {
        String key = "FLD_" + fidRoad + "_chain" + chainIndex + "_rev";
        return linkPaths.get(key);
    }



    /** 输出 XML **/
    public void writeSchedule(String outFile) {
        // 设置坐标系属性
        if (coordinateSystem != null && !coordinateSystem.isEmpty()) {
            schedule.getAttributes().putAttribute("coordinateReferenceSystem", coordinateSystem);
        }

        new TransitScheduleWriterV2(schedule).write(outFile);
        System.out.println("✅ Transit schedule written to: " + outFile);
    }


    private String safeString(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
