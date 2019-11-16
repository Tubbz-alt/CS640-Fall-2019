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

        @Override
        public String toString() {
            return "LinkDistancePair{" +
                    "distance=" + distance +
                    ", link=" + link +
                    '}';
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
            Map<Long, LinkDistancePair> srcEntry = table.get(link.getSrc());
            if (srcEntry == null) {
                srcEntry = new HashMap<Long, LinkDistancePair>();
                table.put(link.getSrc(), srcEntry);
            }
            srcEntry.put(link.getDst(), new LinkDistancePair(link, 1));

            Map<Long, LinkDistancePair> destEntry = table.get(link.getDst());
            if (destEntry == null) {
                destEntry = new HashMap<Long, LinkDistancePair>();
                table.put(link.getDst(), destEntry);
            }
            destEntry.put(link.getSrc(), new LinkDistancePair(link, 1));
        }

        while (true) {
            boolean updated = false;
            // for each link
            for (Link link : links) {
                for (long dest : table.keySet()) {
                    if (dest == link.getSrc() || dest == link.getDst())
                        continue;
                    LinkDistancePair leftToDest = table.get(link.getSrc()).get(dest);
                    LinkDistancePair rightToDest = table.get(link.getDst()).get(dest);
                    if (leftToDest == null && rightToDest == null) {
                        continue;
                    } else if (leftToDest == null) {
                        table.get(link.getSrc()).put(dest, new LinkDistancePair(link, rightToDest.distance + 1));
                    } else if (rightToDest == null) {
                        table.get(link.getDst()).put(dest, new LinkDistancePair(link, leftToDest.distance + 1));
                    } else if (leftToDest.distance > rightToDest.distance + 1) {
                        leftToDest.link = link;
                        leftToDest.distance = rightToDest.distance + 1;
                    } else if (rightToDest.distance > leftToDest.distance + 1) {
                        rightToDest.link = link;
                        rightToDest.distance = leftToDest.distance + 1;
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
        table.put(switchId, new HashMap<Long, LinkDistancePair>());
    }

    void removeSwitch(Long switchId) {
        table.remove(switchId);

        for (Map<Long, LinkDistancePair> map : table.values()) {
            map.remove(switchId);
        }
    }

    @Override
    public String toString() {
        return table.toString();
    }
}
