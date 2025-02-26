package org.matsim.network;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.collections.CollectionUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/*
 * "CreateSubwayNetwork" is aimed to adding subway network into road network.
 *  (networkV1 produced by class "Osm2NetworkUtil").
 *
 *
 *
 * To-do lists:
 * 1. Create a real multi-modal network whose road network's links' modes are correct.
 *
 * */
public class CreateSubwayNetwork {

    public static void main(String[] args) throws IOException {
//        the fundamental network
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile("D:\\【学术】\\【研究生】\\【仿真】MatSim" +
                "\\【案例】郑州市多模式交通仿真\\郑州市路网\\networkZhengV1.xml");

//        add the "bus" mode to links of the fundamental network.
        network = addLinkMode(network);

//        add the links belonging to "subway" mode.
        String filename = "D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市地铁\\1号线.csv";
        addSubwayElements(network, filename);

//        write the network to the file.

        new NetworkWriter(network).write("D:\\【学术】\\【研究生】\\【仿真】MatSim\\【案例】郑州市多模式交通仿真\\郑州市路网\\NetworkZhengMultiModal.xml");


    }

    public static Network addLinkMode(Network network) {
//      So, the first thing is adding "bus" to the mode attribute of every link.
//      This method is tested already!
        for (Link link : network.getLinks().values()) {
            link.setAllowedModes(CollectionUtils.stringToSet("pt,car"));

        }
        return network;
    }

    public static Network addSubwayElements(Network network, String filename) {
//        The second thing is add subway links to the network.
//      create nodes and links
        NetworkFactory networkFactory = network.getFactory();

//                to-do list: if you take multiple lines and transfering into account, this will not work well.
        List<Node> nodeList1 = new ArrayList<Node>();   // one way
        List<Node> nodeList2 = new ArrayList<Node>();   // another way

//        1. Nodes
        try {
            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(filename), "utf-8");
            BufferedReader reader = new BufferedReader(inputStreamReader);

//             Note: whenever the "reader.lines().count()" will read all rows,
//             therefore "readLine" subsequently will be disabled.
//            So, I will read the file again after "reader.lines().count()".

//            Firstly, just for counting the station number of the line.
            int stationNum = (int) reader.lines().count() - 1;
            System.out.println("the count of the station of the line is " + stationNum);

//            close the stream.
            reader.close();
            inputStreamReader.close();

//          Secondly, for parsing the csv.
            InputStreamReader inputStreamReader2 = new InputStreamReader(new FileInputStream(filename), "utf-8");
            BufferedReader reader2 = new BufferedReader(inputStreamReader2);

            //            title line.
            System.out.println(reader2.readLine());

            //
            String line;
            int k = 1;
            WGS2Mercator wgs2Mercator = new WGS2Mercator();  // coordination transformation.

            while ((line = reader2.readLine()) != null) {   // the "readLine" begins at the 2th row.
                String item[] = line.split(",");
                //                address
//                String address = item[0];
                    //                name
    //                String name = item[1];
                //                longitude
                String longitude = item[2];
                //                latitude
                String latitude = item[3];

                /* Dummy stations in metro:
                * 1. (Stations Number) all stations be viewed as 2n dummy stations when the station serves n lines.
                * 2. (Link) Loop lines will be placed at  original and terminal stations, but the end of the loop link is two dummy stations.
                * 3. Note: The circulation procedure will delete all local reference variables, such as node.
                * resulting to the fact that links connecting nodes belonging to different circulations can't be created.
                * So I will create a new "nodes" list, create nodes circularly and save them to the list.
                * */

//                new loop links at the original and terminal stations.
//                Naming Rule of Link Id: "S1-2-1-S11-14-8" the link from 1st line 2nd station 1st representation to 11th line 14th station 8th representation.
                if (k == 1 || k == stationNum) {   // original and terminal stations; Note: k is counted from 1.
//                    To-do list: If this method is applied to many lines, the Id creation should be adjusted.
//                    I can get the line name from the file name and apply it to the Id creation.
                    Id<Node> nodeId1 = Id.createNodeId("S" + "1" + "-" + String.valueOf(k) + "-" + "1");
                    Id<Node> nodeId2 = Id.createNodeId("S" + "1" + "-" + String.valueOf(k) + "-" + "2");

//                    Note that the stations coords should be transformed from WGS84 to Mercator.
                    Node node1 = networkFactory.createNode(nodeId1, wgs2Mercator.transform(new Coord(Double.parseDouble(longitude), Double.parseDouble(latitude))));
                    Node node2 = networkFactory.createNode(nodeId2, wgs2Mercator.transform(new Coord(Double.parseDouble(longitude), Double.parseDouble(latitude))));
                    network.addNode(node1);
                    network.addNode(node2);
//                    add nodes to arrayList.
                    nodeList1.add(node1);
                    nodeList2.add(node2);
//                    two ways, loop link.
                    Id<Link> linkId1 = Id.createLinkId("S" + "1" + "-" + String.valueOf(k) + "-" + "1"+"-"+"S" + "1" + "-" + String.valueOf(k) + "-" + "2");
                    Id<Link> linkId2 = Id.createLinkId("S" + "1" + "-" + String.valueOf(k) + "-" + "1"+"-"+"S" + "2" + "-" + String.valueOf(k) + "-" + "1");
                    Link linkLoop1 = networkFactory.createLink(linkId1, node1, node2);  // the length of loop link is zero by default.
                    Link linkLoop2 = networkFactory.createLink(linkId2, node2, node1);  // the length of loop link is zero by default.

                    linkLoop1.setLength(500);
                    linkLoop2.setLength(500);
                    linkLoop1.setAllowedModes(CollectionUtils.stringToSet("pt"));
                    linkLoop2.setAllowedModes(CollectionUtils.stringToSet("pt"));
                    network.addLink(linkLoop1);
                    network.addLink(linkLoop2);

                } else {//                for other intermediate stations, new nodes and links.
//                Naming Rule of Node Id: "S1-2-1" subway 1st line 2nd station 1st representation.
//                    To-do list: when dealing with multiple lines, U should take transfer stations into account, and adjust Id creation and their num.
                    System.out.println("这是一个中间站"+k);
                    Id<Node> nodeId1 = Id.createNodeId("S" + "1" + "-" + String.valueOf(k) + "-" + "1");
                    Id<Node> nodeId2 = Id.createNodeId("S" + "1" + "-" + String.valueOf(k) + "-" + "2");

                    Node node1 = networkFactory.createNode(Id.createNodeId(nodeId1), wgs2Mercator.transform(new Coord(Double.parseDouble(longitude), Double.parseDouble(latitude))));
                    Node node2 = networkFactory.createNode(Id.createNodeId(nodeId2), wgs2Mercator.transform(new Coord(Double.parseDouble(longitude), Double.parseDouble(latitude))));
                    network.addNode(node1);
                    network.addNode(node2);

//                    add nodes to arrayList.
                    nodeList1.add(node1);
                    nodeList2.add(node2);
                }

                k++;

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

//      2. links. new links one by one (in order).
        int k = 1;  // The k is list index rather than station index. It's starts from 2th station in reality.
        while(k<nodeList1.size()) {
//          two ways.
//          one way: 1 to 2, 2 to 3, 3 to 4, ……
            Id<Link> linkId1 = Id.createLinkId("S" + "1" + "-" + String.valueOf(k - 1 + 1) + "-" + "1" + "-" + "S" + "1" + "-" + String.valueOf(k+1) + "-" + "1");  // +1 is aimed to transferring list index to station index.
            Link link1 = networkFactory.createLink(linkId1,nodeList1.get(k-1),nodeList1.get(k));
//          another way: 2 to 1, 3 to 2, 4 to 3, ……
            Id<Link> linkId2 = Id.createLinkId("S" + "1" + "-" + String.valueOf(k+1) + "-" + "1" + "-" + "S" + "1" + "-" + String.valueOf(k-1+1) + "-" + "1");
            Link link2 = networkFactory.createLink(linkId2,nodeList1.get(k),nodeList1.get(k-1));

//          set the attributes of the links.
//          To-do list: The length and other attributes use the default values for the moment.
            link1.setAllowedModes(CollectionUtils.stringToSet("pt"));
            link2.setAllowedModes(CollectionUtils.stringToSet("pt"));

//          add links to the network.
            network.addLink(link1);
            network.addLink(link2);

            k++;
        }


        return network;
    }

    public static void Test(Network network) {
        int k = 1;
        for (Link link : network.getLinks().values()) {
//            if(k%100==0){
//            System.out.println(link.getAllowedModes());
//            }
//            k++;
        }

    }
}
