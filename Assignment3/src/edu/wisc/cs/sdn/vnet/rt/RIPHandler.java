package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.UDP;

class RIPHandler {
    private Router router;
    private RouteTable routeTable;


    RIPHandler(Router router) {
        this.router = router;
        this.routeTable = router.getRouteTable();

        for (Iface iface : router.getInterfaces().values()) {
            int mask = iface.getSubnetMask();
            int ip = iface.getIpAddress();
            int subnet = mask & ip;
            this.routeTable.insert(subnet, 0, mask, iface);
        }
    }

    private RIPv2 getRipPacket() {
        return null;
    }

    private UDP getUdpPacket(RIPv2 payload) {
        return (UDP) new UDP()
                .setSourcePort(UDP.RIP_PORT)
                .setDestinationPort(UDP.RIP_PORT)
                .setPayload(payload);
    }

    private IPv4 getIpPacket(Iface iface, UDP payload) {
        return (IPv4) new IPv4()
                .setTtl((byte) 64)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setDestinationAddress("224.0.0.9")
                .setSourceAddress(iface.getIpAddress())
                .setPayload(payload);
    }

    private Ethernet getEthernetPacket(Iface inIface, IPv4 payload) {
        return (Ethernet) new Ethernet()
                .setEtherType(Ethernet.TYPE_IPv4)
                .setSourceMACAddress(inIface.getMacAddress().toBytes())
                .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
                .setPayload(payload);
    }

    void sendRequest(Iface inIface, int ipAddr) {
        RIPv2 ripPacket = getRipPacket();
        UDP udpPacket = getUdpPacket(ripPacket);
        for (Iface iface : router.getInterfaces().values()) {
            IPv4 ipPacket = getIpPacket(iface, udpPacket);
            Ethernet ethernetPacket = getEthernetPacket(inIface, ipPacket);
            router.sendPacket(ethernetPacket, inIface);
        }

    }
}
