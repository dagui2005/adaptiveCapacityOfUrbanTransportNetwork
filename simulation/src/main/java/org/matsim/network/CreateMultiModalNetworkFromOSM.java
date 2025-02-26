package org.matsim.network;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.algorithms.NetworkSimplifier;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt2matsim.run.Osm2MultimodalNetwork;

public class CreateMultiModalNetworkFromOSM {
    public static void main(String args[]){
//        convert OSM data into multi-modal networks. But it can only give the link information about modes, for example, foot or bike,
//        and this maybe very important when taking non-motorized traffic into account.

//      to-do lists:
//        1. delete links with rail or railway modes.
//        2. Maybe there are not enough public transport facilities in OSM.
//        For example, network from OSM data can't give any information about metro station, let alone processing the metro stations.
//        Therefore, to be on the safe side, I still choose the way that combine the road network of Open Street Map and public transport network from the other sources.
//        3. The coordination transformation may be wrong. Try Mercator coordination.
//        @author.Chunhong Li

        String osmFilePath = "D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市路网\\郑州市路网.osm";
        String outputFilePath = "D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市路网\\multiModalNetworkZhengV3FromMatsimUnprocessed.xml";
        Osm2MultimodalNetwork.run(osmFilePath,outputFilePath,"EPSG:25832");   // EPSG:4326 is WGS84 coordination.

//        read the network from the file.
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(outputFilePath);

//      The network can be simplified for improving the simulation performance.
//      "NetworkSimplifier" combines multiple links into one if they share the same attribute values. (i.e. same speed, flow capacity and number of lanes).
//       And U can set the threshold value of link length by other methods.
//        Shortcomings: Fewer links often result in faster simulations and fewer events, but might introduce artifacts: fewer u-turn possibilities, first/last link of trip is artificially long, straight lines in visualization.
        new NetworkSimplifier().run(network);

//        clean multi-modal network.
//        Note: NetworkCleaner detects the largest connected cluster of nodes and links, removes everything else.
//        A network should not contain any sources or sinks as nodes.\
//        So, if you destroy some links randomly or deliberately, you can't use this method.
        MultimodalNetworkCleaner multimodalNetworkCleaner = new MultimodalNetworkCleaner(network);

//        Removes nodes from the network that have no incoming or outgoing links attached to them.
        multimodalNetworkCleaner.removeNodesWithoutLinks();

//      Modifies the network such that the subnetwork containing only links that have at least
//      one of the specified transport modes.
        multimodalNetworkCleaner.run(CollectionUtils.stringToSet("subway,bus,car"));


//        write the network into files.
        new NetworkWriter(network).write("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市路网\\multiModalNetworkZhengV3FromMatsim.xml");

    }
}
