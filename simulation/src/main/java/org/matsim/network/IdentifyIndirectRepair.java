package org.matsim.network;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.util.Set;

/**
 * @author: Chunhong li
 * @date: 2023年04月02日 10:02
 * @Description: Consider "Direct failures", "network", "direct repairs" and "other parameters" as inputs; we want indirect repairs.
 * NOTE: This code is based on "IdentifyIndirectFailures.java". Direct failures is repaired as direct repairs, but indirect failures is repaired as indirect repairs.
 * NOTE: indirect failures (repairs) are different between road network and public transit network. *
 */
public class IdentifyIndirectRepair {
    /* input the original road network, direct failures and direct repairs, output: indirect repairs in road network */
    public static Set<Id<Node>> identifyRoadIndirectRepair(Network roadNetwork, Set<Id<Node>> directFailedNodes, Set<Id<Node>> directRepairNodes){
//        Indirect failures before repair.
        Set<Id<Node>> roadIndirectFailures0 = IdentifyIndirectFailures.identifyRoadIndirectFailures(roadNetwork, directFailedNodes);
//        Indirect failures after repair.
        directFailedNodes.removeAll(directRepairNodes);
        Set<Id<Node>> roadIndirectFailures1 = IdentifyIndirectFailures.identifyRoadIndirectFailures(roadNetwork, directFailedNodes);
//        Indirect repairs.
        roadIndirectFailures0.removeAll(roadIndirectFailures1);
        return roadIndirectFailures0;
    }

    /* input the original public transit network, transit schedule, direct failures and direct repairs, output: indirect repairs in public transit network */
    public static Set<Id<Node>> identifyPtIndirectRepair(Network ptNetwork, Set<Id<Node>> directFailedNodes, TransitSchedule transitSchedule, Set<Id<Node>> directRepairNodes){
//        Indirect failures before repair.
        Set<Id<Node>> ptIndirectFailures0 = IdentifyIndirectFailures.identifyPtIndirectFailures(ptNetwork, directFailedNodes, transitSchedule);
//        Indirect failures after repair.
        directFailedNodes.removeAll(directRepairNodes);
        Set<Id<Node>> ptIndirectFailures1 = IdentifyIndirectFailures.identifyPtIndirectFailures(ptNetwork, directFailedNodes, transitSchedule);
//        Indirect repairs.
        ptIndirectFailures0.removeAll(ptIndirectFailures1);
        return ptIndirectFailures0;
    }
}
