package org.matsim.network;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
//import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.io.NetworkWriter;

import org.matsim.core.network.io.MatsimNetworkReader;
//import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.api.core.v01.Scenario;

/**
 * Network loader/writer utilities.
 */
public class NetworkLoader {

    public static class LoadResult {
        public final Scenario scenario;
        public final Network network;
        public final String coordinateReferenceSystem; // may be null

        public LoadResult(Scenario scenario, Network network, String coordinateReferenceSystem) {
            this.scenario = scenario;
            this.network = network;
            this.coordinateReferenceSystem = coordinateReferenceSystem;
        }
    }

    /**
     * 读取 MATSim network 文件并返回 network + scenario。
     * 会尝试从 network.attributes 读取 coordinateReferenceSystem 属性（如果存在）。
     */
    public static LoadResult loadNetwork(String networkFile) {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        Network network = scenario.getNetwork();

        // 读取 network xml
        MatsimNetworkReader reader = new MatsimNetworkReader(scenario.getNetwork());
        reader.readFile(networkFile);

        // 读取 CRS 属性（如果在 file header 中写了）
        Object crsObj = network.getAttributes().getAttribute("coordinateReferenceSystem");
        String crs = (crsObj != null) ? crsObj.toString() : null;

        return new LoadResult(scenario, network, crs);
    }

    /**
     * 写出 network 到文件
     */
    public static void writeNetwork(Network network, String outFile) {
        new NetworkWriter(network).write(outFile);
    }
}
