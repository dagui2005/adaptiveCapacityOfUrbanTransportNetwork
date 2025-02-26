package org.matsim.network;

/*
* @author: Chunhong Li'21.1114
* @Description: 此程序已经可以废弃，目前可以通过两种方式分等级修改 road network 的通行能力，推荐的是用 matsim 的扩展包 multi-modal network 重新提取并修改；另一种是目前使用的，用 osmnx 重新提取，删边，分等级完善信息。
* */

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class LinkModification {

//    读取的网络文件
    private static String networkPath = "scenarios/nanjingMultiModal_0104/network.xml";

//    输出的网络文件
    private static String networkPath2 = "scenarios/nanjingMultiModal_0104/network.xml";

    public static void main(String[] args){
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        new MatsimNetworkReader(network).readFile(networkPath);

        for(Link link:network.getLinks().values()){
            if(link.getAllowedModes().contains(TransportMode.pt) && (!link.getAllowedModes().contains(TransportMode.car))){       // equals 比较的是内存，相同的字符串，java只保存一份；== 比较的是引用，即使字符串相同，但内存地址不同，所以用 == 一定返回 false
                link.setCapacity(1);
//                System.out.println("...这是一个 metro link ...");
                link.setFreespeed(60);  // m/s
            }
            else{
//                System.out.println("...这是一个 road link...");
                link.setFreespeed(link.getFreespeed() / 3.6);  // m/s
            }
        }
        new NetworkWriter(network).write(networkPath2);

    }
}
