package edu.wisc.cs.sdn.apps.l3routing;

import net.floodlightcontroller.routing.Link;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Graph {
    static class LinkDistancePair {
        int distance;
        Link link;

        LinkDistancePair(Link link, int distance) {
            this.link = link;
            this.distance = distance;
        }
    }

    Graph(Collection<Long> switchIds) {
        initTable(switchIds);
    }

    private Map<Long, Map<Long, LinkDistancePair>> table;

    Map<Long, Map<Long, LinkDistancePair>> getTable() {
        return table;
    }

    private void initTable(Collection<Long> switchIds) {
        table = new HashMap<Long, Map<Long, LinkDistancePair>>();
        for (long switchId : switchIds)
            table.put(switchId, new HashMap<Long, LinkDistancePair>());
    }

    void recomputeTable(Collection<Link> links) {
        initTable(new ArrayList<Long>(table.keySet()));
        updateTable(links);
    }

    void updateTable(Collection<Link> links) {
        for (Link link : links) {
            table.get(link.getSrc()).put(link.getDst(), new LinkDistancePair(link, 1));
            table.get(link.getDst()).put(link.getSrc(), new LinkDistancePair(link, 1));
        }

        while (true) {
            boolean updated = false;
            // for each link
            for (Link link : links) {
                for (long dest : table.keySet()) {
                    LinkDistancePair linkSrcToDest = table.get(link.getSrc()).get(dest);
                    LinkDistancePair linkDestToDest = table.get(link.getDst()).get(dest);
                    if (linkSrcToDest == null && linkDestToDest == null) {
                        continue;
                    } else if (linkSrcToDest == null) {
                        table.get(link.getSrc()).put(dest, new LinkDistancePair(link, linkDestToDest.distance + 1));
                    } else if (linkDestToDest == null) {
                        table.get(link.getDst()).put(dest, new LinkDistancePair(link, linkSrcToDest.distance + 1));
                    } else if (linkSrcToDest.distance > linkDestToDest.distance + 1) {
                        linkSrcToDest.link = link;
                        linkSrcToDest.distance = linkDestToDest.distance + 1;
                    } else if (linkDestToDest.distance > linkSrcToDest.distance + 1) {
                        linkDestToDest.link = link;
                        linkDestToDest.distance = linkSrcToDest.distance + 1;
                    } else {
                        continue;
                    }
                    updated = true;
                }
            }

            if (!updated) {
                break;
            }
        }
    }

    void addSwitch(Long switchId) {
        table.put(switchId, new HashMap<>());
    }

    void removeSwitch(Long switchId) {
        table.remove(switchId);

        for (Map<Long, LinkDistancePair> map : table.values()) {
            map.remove(switchId);
        }
    }
}
