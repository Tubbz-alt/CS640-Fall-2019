package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;

import java.util.Hashtable;
import java.util.Map;

class ArpHandler {
    ICMPHandler icmpHandler;
    Router router;
    private final Map<Integer, ArpRequestQueue> map;

    ArpHandler(Router router, ICMPHandler icmpHandler) {
        this.router = router;
        this.icmpHandler = icmpHandler;
        this.map = new Hashtable<>();
    }

    private ARP createArpPacket(Iface inIface) {
        return new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
                .setProtocolAddressLength((byte) 4)
                .setSenderHardwareAddress(inIface.getMacAddress().toBytes())
                .setSenderProtocolAddress(inIface.getIpAddress());
    }

    private ARP createArpReplyPacket(Iface inIface, ARP inArpPacket) {
        return createArpPacket(inIface)
                .setOpCode(ARP.OP_REPLY)
                .setTargetHardwareAddress(inArpPacket.getSenderHardwareAddress())
                .setTargetProtocolAddress(inArpPacket.getSenderProtocolAddress());
    }

    private ARP createArpRequestPacket(Iface inIface, int ipAddr) {
        return createArpPacket(inIface)
                .setOpCode(ARP.OP_REQUEST)
                .setTargetHardwareAddress(new byte[Ethernet.DATALAYER_ADDRESS_LENGTH])
                .setTargetProtocolAddress(ipAddr);
    }

    private Ethernet createEthernetPacket(Iface inIface) {
        return new Ethernet()
                .setEtherType(Ethernet.TYPE_ARP)
                .setSourceMACAddress(inIface.getMacAddress().toBytes());
    }

    void sendReply(Iface inIface, Ethernet etherPacket, ARP arpPacket) {
        Ethernet ether = (Ethernet) createEthernetPacket(inIface)
                .setDestinationMACAddress(etherPacket.getSourceMACAddress())
                .setPayload(createArpReplyPacket(inIface, arpPacket));
        router.sendPacket(ether, inIface);
    }

    void sendRequest(Iface inIface, int ipAddr) {
        Ethernet ether = (Ethernet) createEthernetPacket(inIface)
                .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
                .setPayload(createArpRequestPacket(inIface, ipAddr));
        router.sendPacket(ether, inIface);
    }

    void handleResponse(int ipAddr, MACAddress macAddress) {
        synchronized (map) {
            ArpRequestQueue arpRequestQueue = map.get(ipAddr);
            if (arpRequestQueue == null) return;
            arpRequestQueue.handleResponse(macAddress);
            map.remove(ipAddr);
        }
    }

    void removeQueueFromTable(int ipAddr) {
        synchronized (map) {
            map.remove(ipAddr);
        }
    }

    void generateRequest(Ethernet ethernetPacket, int ipAddr, Iface inIface, Iface outIface) {
        synchronized (map) {
            ArpRequestQueue queue = map.get(ipAddr);
            if (queue == null) {
                queue = new ArpRequestQueue(this, ipAddr, outIface);
                map.put(ipAddr, queue);
            }
            queue.add(ethernetPacket, inIface);
        }
    }
}
