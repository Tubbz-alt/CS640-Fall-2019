package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.*;

class ICMPHandler {
    private Router router;

    ICMPHandler(Router router) {
        this.router = router;
    }

    private Data createData(IPv4 ipPacket) {
        byte[] icmpDataBytes = new byte[4 + ipPacket.getHeaderLength() * 4 + 8];
        System.arraycopy(ipPacket.serialize(), 0, icmpDataBytes, 4, icmpDataBytes.length - 4);
        return new Data(icmpDataBytes);
    }

    private ICMP createIcmpPacket(int icmpType, int icmpCode, Data data) {
        return (ICMP) new ICMP()
                .setIcmpType((byte) icmpType)
                .setIcmpCode((byte) icmpCode)
                .setPayload(data);
    }

    private IPv4 createIpPacket(IPv4 ipPacket, ICMP icmp) {
        return (IPv4) new IPv4()
                .setTtl((byte) 64)
                .setProtocol(IPv4.PROTOCOL_ICMP)
                .setDestinationAddress(ipPacket.getSourceAddress())
                .setPayload(icmp);
    }

    private Ethernet createEthernetPacket(Iface inIface, IPv4 ipPacket, IPv4 payload) {
        MACAddress destinationMACAddress = router.getMacByIp(ipPacket.getSourceAddress());
        if (destinationMACAddress == null) return null;

        return (Ethernet) new Ethernet()
                .setEtherType(Ethernet.TYPE_IPv4)
                .setSourceMACAddress(inIface.getMacAddress().toBytes())
                .setDestinationMACAddress(destinationMACAddress.toBytes())
                .setPayload(payload);
    }

    void sendMessage(Iface inIface, IPv4 ipPacket, int icmpType, int icmpCode) {
        Data data = createData(ipPacket);
        ICMP icmp = createIcmpPacket(icmpType, icmpCode, data);
        IPv4 payload = createIpPacket(ipPacket, icmp)
                .setSourceAddress(inIface.getIpAddress());
        Ethernet ether = createEthernetPacket(inIface, ipPacket, payload);
        if (ether != null) router.sendPacket(ether, inIface);
    }

    void sendEcho(Iface inIface, IPv4 ipPacket) {
        Data data = new Data(ipPacket.getPayload().getPayload().serialize());
        ICMP icmp = createIcmpPacket(0, 0, data);
        IPv4 payload = createIpPacket(ipPacket, icmp)
                .setSourceAddress(ipPacket.getDestinationAddress());
        Ethernet ether = createEthernetPacket(inIface, ipPacket, payload);
        if (ether != null) router.sendPacket(ether, inIface);
    }
}
