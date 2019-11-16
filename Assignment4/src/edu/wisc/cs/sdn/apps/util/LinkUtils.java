package edu.wisc.cs.sdn.apps.util;

import net.floodlightcontroller.routing.Link;

public class LinkUtils {

    public static int getOtherPort(Link link, long thisSwitch){
        return link.getSrc() == thisSwitch ? link.getDstPort() : link.getSrcPort();
    }

    public static long getOther(Link link, long thisSwitch) {
        return link.getSrc() == thisSwitch ? link.getDst() : link.getSrc();
    }
}
