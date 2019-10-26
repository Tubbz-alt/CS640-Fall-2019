package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;

class ArpHandler {
    private ARP getReplyHeader(Iface inIface, ARP inArpPacket) {
        return new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(inIface.getMacAddress().toBytes())
                .setSenderProtocolAddress(inIface.getIpAddress())
                .setTargetHardwareAddress(inArpPacket.getSenderHardwareAddress())
                .setTargetProtocolAddress(inArpPacket.getSenderProtocolAddress());
    }

    Ethernet getReplyPayload(Iface inIface, Ethernet etherPacket, ARP arpPacket) {
        return (Ethernet) new Ethernet()
                .setEtherType(Ethernet.TYPE_ARP)
                .setSourceMACAddress(inIface.getMacAddress().toBytes())
                .setDestinationMACAddress(etherPacket.getSourceMACAddress())
                .setPayload(getReplyHeader(inIface, arpPacket));
    }
}
