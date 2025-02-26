package org.matsim.analysis.event;

import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * @author: Chunhong li
 * @date: 2022年12月21日 21:24
 * @Description:
 */
public class RunVolumesAnalyzer {

    public static void main(String[] args) throws FileNotFoundException {
//        Baseline scenario. Input: network.xml path and events.xml path. Output: linksVolumesPath.txt path
        String networkPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\无洪水\\output01_demand.noex20.0.27.100pct\\output_network.xml.gz";
        String eventsPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】交通仿真\\无洪水\\output01_demand.noex20.0.27.100pct\\output_events.xml.gz";
        String linksVolumesPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络恢复\\【分析】节点恢复顺序\\【场景】洪水场景\\【输入】网络路段流量\\links volumes baseline.txt";

        run(networkPath, eventsPath, linksVolumesPath);
    }

    public static void run(String networkPath, String eventsPath, String linksVolumesPath) throws FileNotFoundException {
        Network network = NetworkUtils.readNetwork(networkPath);
//      1. create an event object
        EventsManager eventsManager = EventsUtils.createEventsManager();
//      2. create the handler and add it
        VolumesAnalyzer volumesAnalyzer = new VolumesAnalyzer(3600, 24 * 3600 - 1, network); // timeBinSize: 1h; 0-24h
        eventsManager.addHandler(volumesAnalyzer);
//      3. create the reader and read the file.  "EventsUtils.readEvents" == "new MatsimEventsReader(events).readFile(filename) ;"
        //   一定等所有的 handler 加上之后再读取 events 文件。
        EventsUtils.readEvents(eventsManager, eventsPath);
//        4. write to txt.
        PrintStream ps = new PrintStream(linksVolumesPath);
        System.setOut(ps);
        System.out.println("linkId; [volumes0-1, volumes1-2, volumes2-3, ......., volumes23-24]");
        for (Id<Link> linkId : network.getLinks().keySet()) {
            double[] volumesPerHourForLink = volumesAnalyzer.getVolumesPerHourForLink(linkId);
            System.out.print(linkId.toString() + "; ");
            System.out.print(Arrays.toString(volumesPerHourForLink) + "\n");
        }
        ps.close();
    }
}
