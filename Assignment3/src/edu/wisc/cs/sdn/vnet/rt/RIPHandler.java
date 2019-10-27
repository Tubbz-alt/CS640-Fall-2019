package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.*;

class RIPHandler {
    private Router router;

    private final static int RIP_IP_ADDRESS = IPv4.toIPv4Address("224.0.0.9");

    RIPHandler(Router router) {
        this.router = router;

        for (Iface iface : router.getInterfaces().values()) {
            int mask = iface.getSubnetMask();
            int ip = iface.getIpAddress();
            int subnet = mask & ip;
            router.getRouteTable().insert(subnet, 0, mask, iface);
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
                .setDestinationAddress(RIP_IP_ADDRESS)
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

    void sendRequest(Iface inIface) {
        RIPv2 ripPacket = getRipPacket();
        UDP udpPacket = getUdpPacket(ripPacket);
        for (Iface iface : router.getInterfaces().values()) {
            IPv4 ipPacket = getIpPacket(iface, udpPacket);
            Ethernet ethernetPacket = getEthernetPacket(inIface, ipPacket);
            router.sendPacket(ethernetPacket, inIface);
        }
    }

    void handleResponse(IPv4 ipPacket) {
        RIPv2 ripPacket = (RIPv2) ipPacket.getPayload().getPayload();
        for (RIPv2Entry entry : ripPacket.getEntries()) {

        }

    }

    boolean isRipPacket(IPv4 ipPacket) {
        return ipPacket.getDestinationAddress() == RIPHandler.RIP_IP_ADDRESS &&
                ipPacket.getProtocol() == IPv4.PROTOCOL_UDP &&
                ((UDP) ipPacket.getPayload()).getDestinationPort() == UDP.RIP_PORT;
    }
}
