package org.matsim.population;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;

import org.matsim.api.core.v01.Coord;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;

/**
 * DemandXmlGenerator using MATSim Population API
 *
 * Reads:
 *  - full_grid_cid(cid, x, y)       : WGS84 lon/lat
 *  - ind_move_all(...)             : trip records
 *
 * Output:
 *  - MATSim population XML (v6)
 */
public class DemandXmlGenerator {

    private static final String DEFAULT_DATE = "20201015";
    private static final int DEFAULT_MIN_START_HOUR = 7;
    private static final int DEFAULT_MAX_END_HOUR = 10;

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DEFAULT_DB_USER = "postgres";
    private static final String DEFAULT_DB_PASS = "postgres";
    private static final String DEFAULT_OUTPUT = "D:\\Luan\\2025-09\\MATSim\\guangzhoubaseline\\demand.xml";

    private static final String SRC_EPSG = "EPSG:4326";   // input WGS84
    private static final String TGT_EPSG = "EPSG:32649";  // target projection

    private final String dbUrl;
    private final String dbUser;
    private final String dbPass;

    private final String dateFilter;
    private final int minStartHour;
    private final int maxEndHour;

    private MathTransform transformToTarget;

    public DemandXmlGenerator(
            String dbUrl, String dbUser, String dbPass,
            String dateFilter, int minStartHour, int maxEndHour) {

        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPass = dbPass;

        this.dateFilter = dateFilter;
        this.minStartHour = minStartHour;
        this.maxEndHour = maxEndHour;

        try {
            // 获取坐标系时指定强制经纬度顺序
            org.geotools.api.referencing.crs.CoordinateReferenceSystem sourceCRS = CRS.decode(SRC_EPSG, true);  // true表示强制经纬度顺序
            CoordinateReferenceSystem targetCRS = CRS.decode(TGT_EPSG, true);
            transformToTarget = CRS.findMathTransform(sourceCRS, targetCRS, true);
            System.out.println("[CRS] Init transform WGS84 → EPSG:32649");
        } catch (Exception e) {
            System.err.println("[CRS] Transform init failed: " + e.getMessage());
            transformToTarget = null;
        }

    }

    public void generate(String outputFile) throws Exception {
        // 手动加载 PostgreSQL 驱动
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver not found", e);
        }

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Population population = scenario.getPopulation();

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {

            System.out.println("[DB] Connected: " + dbUrl);

            Map<Integer, Coordinate> gridMap = loadGridCoordinates(conn);
            System.out.println("[Grid] loaded = " + gridMap.size());

            List<TripRecord> trips = loadFilteredTrips(conn);
            System.out.println("[Trips] loaded = " + trips.size());

            Map<String, List<TripRecord>> tripsByInd = new LinkedHashMap<>();
            for (TripRecord t : trips) {
                tripsByInd.computeIfAbsent(t.ind, k -> new ArrayList<>()).add(t);
            }

            // Build population
            int personsWritten = 0;
            for (Map.Entry<String, List<TripRecord>> entry : tripsByInd.entrySet()) {
                buildPerson(population, entry.getKey(), entry.getValue(), gridMap);
                personsWritten++;
            }

            System.out.println("[Population] persons = " + personsWritten);

            PopulationUtils.writePopulation(population, outputFile);
            System.out.println("[Output] " + outputFile);
        }
    }

    // ------------------------ DB loading ---------------------------- //

    private Map<Integer, Coordinate> loadGridCoordinates(Connection conn) throws SQLException {
        Map<Integer, Coordinate> map = new HashMap<>();

        String sql = "SELECT cid, x, y FROM public.full_grid_cid";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int cid = rs.getInt("cid");
                double lon = rs.getDouble("x");
                double lat = rs.getDouble("y");

                map.put(cid, new Coordinate(lon, lat));
            }
        }
        return map;
    }

    private List<TripRecord> loadFilteredTrips(Connection conn) throws SQLException {
        List<TripRecord> list = new ArrayList<>();

        String sql = "SELECT ind, date, move_id, shour, sminute, ehour, eminute, mode, s_id, e_id " +
                "FROM public.ind_move_all " +
                "WHERE s_id<>e_id AND date=? AND shour>? AND ehour<? " +
                "ORDER BY ind, move_id";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(dateFilter));
            ps.setInt(2, minStartHour);
            ps.setInt(3, maxEndHour);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TripRecord t = new TripRecord();
                    t.ind = rs.getString("ind");
                    t.sHour = rs.getInt("shour");
                    t.sMinute = rs.getInt("sminute");
                    t.eHour = rs.getInt("ehour");
                    t.eMinute = rs.getInt("eminute");
                    t.mode = rs.getInt("mode");
                    t.sId = rs.getInt("s_id");
                    t.eId = rs.getInt("e_id");
                    list.add(t);
                }
            }
        }
        return list;
    }

    // ------------------------ build person ---------------------------- //

    private void buildPerson(Population population, String ind,
                             List<TripRecord> trips, Map<Integer, Coordinate> gridMap) {

        Person person = population.getFactory().createPerson(Id.createPersonId(ind));
        Plan plan = population.getFactory().createPlan();
        person.addPlan(plan);

        Coordinate firstCoord = gridMap.get(trips.get(0).sId);
        if (firstCoord == null) {
            System.err.println("Warning: Missing coordinate for sId=" + trips.get(0).sId + ", skipping person " + ind);
            return;
        }

        Coordinate first = transformCoordinate(firstCoord);

        // First home activity
        Activity home = population.getFactory().createActivityFromCoord(
                "home", new Coord(first.x, first.y));
        home.setEndTime(timeToSec(trips.get(0).sHour, trips.get(0).sMinute));
        plan.addActivity(home);

        // trips
        for (int i = 0; i < trips.size(); i++) {
            TripRecord t = trips.get(i);

            // 检查目的地坐标是否存在
            Coordinate destCoord = gridMap.get(t.eId);
            if (destCoord == null) {
                System.err.println("Warning: Missing coordinate for eId=" + t.eId + ", skipping trip for person " + ind);
                continue;
            }

            // Mode
            String mode = (t.mode == 4) ? "pt" : "car";
            Leg leg = population.getFactory().createLeg(mode);
            plan.addLeg(leg);

            // Destination
            Coordinate dest = transformCoordinate(destCoord);
            Activity act;
            if (i < trips.size() -1 ) {
                // 中间行程，设置为work类型并添加开始和结束时间
                TripRecord t1 = trips.get(i+1);
                act = population.getFactory().createActivityFromCoord(
                        "work", new Coord(dest.x, dest.y));
                act.setStartTime(timeToSec(t.eHour, t.eMinute)); // 到达时间
                act.setEndTime(timeToSec(t1.sHour, t1.sMinute)); // 下一次出发时间
            } else {
                // 最后一个行程，返回home
                act = population.getFactory().createActivityFromCoord(
                        "work", new Coord(dest.x, dest.y));
                act.setStartTime(timeToSec(t.eHour, t.eMinute));
                act.setEndTime(timeToSec(17, 0)); // 固定17:00结束工作
            }
            plan.addActivity(act);
        }
        plan.addActivity(home);

        population.addPerson(person);
    }


    // ------------------------ helpers ---------------------------- //

    private Coordinate transformCoordinate(Coordinate src) {
        if (transformToTarget == null) return src;
        try {
            return JTS.transform(src, null, transformToTarget);
        } catch (Exception e) {
            System.err.println("[CRS] transform failed: " + e.getMessage());
            return src;
        }
    }

    private static double timeToSec(int h, int m) {
        LocalTime t = LocalTime.of(h, m);
        return t.toSecondOfDay();
    }

    private static class TripRecord {
        String ind;
        int sHour, sMinute;
        int eHour, eMinute;
        int mode;
        int sId, eId;
    }

    // ------------------------ main ---------------------------- //

    public static void main(String[] args) throws Exception {

        String date = DEFAULT_DATE;
        int minStart = DEFAULT_MIN_START_HOUR;
        int maxEnd = DEFAULT_MAX_END_HOUR;
        String url = DEFAULT_DB_URL;
        String user = DEFAULT_DB_USER;
        String pass = DEFAULT_DB_PASS;
        String out = DEFAULT_OUTPUT;

        if (args.length > 0) date = args[0];
        if (args.length > 1) minStart = Integer.parseInt(args[1]);
        if (args.length > 2) maxEnd = Integer.parseInt(args[2]);
        if (args.length > 3) url = args[3];
        if (args.length > 4) user = args[4];
        if (args.length > 5) pass = args[5];
        if (args.length > 6) out = args[6];

        DemandXmlGenerator g = new DemandXmlGenerator(url, user, pass, date, minStart, maxEnd);
        g.generate(out);
    }
}
