package org.matsim.network;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Chunhong li
 * @date: 2022年06月06日 16:58
 * @Description:
 */
public class DeleteArtificialLinks {
    /* Delete artificial links and return those */
    public static List<Link> run(Network mappedNetwork) {
        List<Link> artificialLinks = new ArrayList<>();
        for (Link link : mappedNetwork.getLinks().values()) {
            if (link.getAllowedModes().contains("artificial")) {
                artificialLinks.add(link);
            }
        }

//        delete artificial links from the mapped network
        for (Link artificialLink : artificialLinks) {
            mappedNetwork.removeLink(artificialLink.getId());
        }

//        return the artificial links
        return artificialLinks;
    }
}
