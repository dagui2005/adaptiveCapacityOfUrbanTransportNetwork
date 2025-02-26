package org.matsim.network;

import org.matsim.analysis.network.SimpleAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.core.network.NetworkUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2024年10月02日 9:05
 * @Description: duplicate links for setting bus-only lanes, emergency lanes.
 * 1. duplicate the init nodes, term nodes, ffs,
 * 2. update capacity, available modes
 */
public class DuplicateLinks {

    public static void splitLinks(Network network){
        NetworkFactory networkFactory = network.getFactory();

        // primary roads FFS = 50 km/h; secondary roads FFS = 40 km/h
        double minFFS = 45 / 3.6;  // m/s
        Set<String> modes1 = new HashSet<>();
        modes1.add("bus");
        Set<String> modes2 = new HashSet<>();
        modes2.add("car");

        for (Link link : network.getLinks().values()) {
            // link-supported modes in Nanjing's network:
            // "pt",
            // "car,bus",  (To be split)
            // "car"   (To be split)
            // "bus,stopFacilityLink,artificial"
            // "artificial,bus"

            if (link.getAllowedModes().contains("car") && link.getFreespeed() > minFFS) {
                // add link (lane) only for pt and emergency vehicles
                Link link1 = NetworkUtils.createAndAddLink(network,
                        Id.createLinkId(link.getId() + "_r"),
                        link.getFromNode(),
                        link.getToNode(),
                        link.getLength(),
                        link.getFreespeed(),
                        link.getCapacity() / link.getNumberOfLanes(),
                        1.0
                        );
                link1.setAllowedModes(modes1);

                // update the residual information
                link.setCapacity(link.getCapacity() - (link.getCapacity() / link.getNumberOfLanes()));
                link.setAllowedModes(modes2);
            }
        }
    }


    public static void main(String[] args) {
        String inputNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold3.0m.s.xml";
        String outNetPath = "D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml\\residual coupled network.rp100.thereshold3.0m.s.emergency management.xml";
        Network network = NetworkUtils.readNetwork(inputNetPath);
        SimpleAnalyzer simpleAnalyzer = new SimpleAnalyzer();
        System.out.println("Before split: ");
        simpleAnalyzer.modeAnalysis(network);

        splitLinks(network);
        System.out.println("After split: ");
        simpleAnalyzer.modeAnalysis(network);
        NetworkUtils.writeNetwork(network, outNetPath);
    }
}
