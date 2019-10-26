package edu.wisc.cs.sdn.vnet.rt;

import java.util.Hashtable;
import java.util.Map;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;

class ArpHandler {
    Router router;
    private final Map<Integer, ArpRequestQueue> requestMap;

    ArpHandler(Router router) {
        this.router = router;
        this.requestMap = new Hashtable<>();
    }

    private ARP getHeader(Iface inIface) {
        return new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
                .setProtocolAddressLength((byte) 4)
                .setSenderHardwareAddress(inIface.getMacAddress().toBytes())
                .setSenderProtocolAddress(inIface.getIpAddress());
    }

    private ARP getReplyHeader(Iface inIface, ARP inArpPacket) {
        return getHeader(inIface)
                .setOpCode(ARP.OP_REPLY)
                .setTargetHardwareAddress(inArpPacket.getSenderHardwareAddress())
                .setTargetProtocolAddress(inArpPacket.getSenderProtocolAddress());
    }

    private ARP getRequestHeader(Iface inIface, int ipAddr) {
        return getHeader(inIface)
                .setOpCode(ARP.OP_REQUEST)
                .setTargetHardwareAddress(new byte[Ethernet.DATALAYER_ADDRESS_LENGTH])
                .setTargetProtocolAddress(ipAddr);
    }

    private Ethernet getReplyPayload(Iface inIface) {
        return new Ethernet()
                .setEtherType(Ethernet.TYPE_ARP)
                .setSourceMACAddress(inIface.getMacAddress().toBytes());
    }

    void sendReply(Iface inIface, Ethernet etherPacket, ARP arpPacket) {
        Ethernet ether = (Ethernet) getReplyPayload(inIface)
                .setDestinationMACAddress(etherPacket.getSourceMACAddress())
                .setPayload(getReplyHeader(inIface, arpPacket));
        router.sendPacket(ether, inIface);
    }

    void sendRequest(Iface inIface, int ipAddr) {
        Ethernet ether = (Ethernet) getReplyPayload(inIface)
                .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
                .setPayload(getRequestHeader(inIface, ipAddr));
        router.sendPacket(ether, inIface);
    }

    void handleResponse(int ipAddr, MACAddress macAddress){
        synchronized (requestMap) {
            ArpRequestQueue arpRequestQueue = requestMap.get(ipAddr);
            if (arpRequestQueue == null) return;
            arpRequestQueue.handleResponse(macAddress);
            requestMap.remove(ipAddr);
        }
    }

    void removeQueueFromTable(int ipAddr){
        synchronized (requestMap) {
            requestMap.remove(ipAddr);
        }
    }

    void generateRequest(Ethernet ethernetPacket, int ipAddr, Iface inIface, Iface outIface) {
        synchronized (requestMap) {
            ArpRequestQueue arpRequestQueue = requestMap.get(ipAddr);
            if (arpRequestQueue == null) {
                arpRequestQueue = new ArpRequestQueue(this, ipAddr, outIface);
                requestMap.put(ipAddr, arpRequestQueue);
            }
            arpRequestQueue.add(ethernetPacket, inIface);
        }
    }
}
