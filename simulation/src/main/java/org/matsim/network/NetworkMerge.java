package org.matsim.network;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.network.NetworkUtils;

/**
 * @author: Chunhong li
 * @date: 2023年03月22日 11:33
 * @Description:  该程序用于融合南京市的公交网络（从公共交通时刻表中提取的）和道路网络（从原耦合网络中提取），以构造和开源场景类似的网络文件。
 */
public class NetworkMerge {
    public static void main(String[] args) {
        Network coupledNet = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\coupled network.xml");
        Network ptNet = NetworkUtils.readNetwork("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\nanjingBasePtNetwork.xml");

//        从耦合网络中只提取道路网络（保留 mode = car, mode = car, bus  的网络），添加到 ptNet
        for (Link link : coupledNet.getLinks().values()) {
            if (link.getAllowedModes().contains("car")){
                ptNet.addNode(link.getFromNode());
                ptNet.addNode(link.getToNode());
                ptNet.addLink(link);
            }
        }

        new NetworkWriter(ptNet).write("D:\\【学术】\\【研究生】\\【方向】多模式交通网络韧性-new floods data\\【数据】路网数据\\After flood damages\\xml20230221\\new coupled Network 20230322.xml");
    }
}
