package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.UDP;

class RIPHandler {
    private Router router;
    private RouteTable routeTable; 

    private final static int RIP_IP_ADDRESS = IPv4.toIPv4Address("224.0.0.9");

    RIPHandler(Router router) {
        this.router = router;
        this.routeTable = router.getRouteTable();

        for (Iface iface : router.getInterfaces().values()) {
            int mask = iface.getSubnetMask();
            int ip = iface.getIpAddress();
            int subnet = mask & ip;
            routeTable.insert(subnet, 0, mask, iface);
        }

        sendRequest();

        new Thread(new Runnable(){
            @Override
            public void run() {
                while(true){
                    try {
                        for (Iface iface : router.getInterfaces().values()) {
                            sendResponse(iface);
                        }
						Thread.sleep(10000);
					} catch (InterruptedException e) {}
                }
            }
        }).run();

        new Thread(new Runnable(){
            @Override
            public void run() {
                while(true){
                    try {
                        // TODO: remove expired entry from this.router.getRouteTable().entries
						Thread.sleep(30000);
					} catch (InterruptedException e) {}
                }
            }
        }).run();
    }

    private RIPv2 createRipResponsePacket() {
        RIPv2 requestPacket = new RIPv2();
        for (RouteEntry routeEntry : routeTable.entries) {
            // TODO: requestPacket.addEntry(entry);
        }

        requestPacket.setCommand(RIPv2.COMMAND_RESPONSE);
        return requestPacket;
    }

    private RIPv2 createRipReqestPacket() {
        RIPv2 requestPacket = new RIPv2();
        requestPacket.setCommand(RIPv2.COMMAND_REQUEST);
        return requestPacket;
    }

    private UDP createUdpPacket(RIPv2 payload) {
        return (UDP) new UDP()
                .setSourcePort(UDP.RIP_PORT)
                .setDestinationPort(UDP.RIP_PORT)
                .setPayload(payload);
    }

    private IPv4 createIpPacket(Iface iface, UDP payload) {
        return (IPv4) new IPv4()
                .setTtl((byte) 64)
                .setProtocol(IPv4.PROTOCOL_UDP)
                .setDestinationAddress(RIP_IP_ADDRESS)
                .setSourceAddress(iface.getIpAddress())
                .setPayload(payload);
    }

    private Ethernet createEthernetPacket(Iface inIface, IPv4 payload) {
        return (Ethernet) new Ethernet()
                .setEtherType(Ethernet.TYPE_IPv4)
                .setSourceMACAddress(inIface.getMacAddress().toBytes())
                .setDestinationMACAddress("FF:FF:FF:FF:FF:FF")
                .setPayload(payload);
    }

    void sendRequest() {
        RIPv2 ripPacket = createRipReqestPacket();
        UDP udpPacket = createUdpPacket(ripPacket);
        for (Iface iface : router.getInterfaces().values()) {
            IPv4 ipPacket = createIpPacket(iface, udpPacket);
            Ethernet ethernetPacket = createEthernetPacket(iface, ipPacket);
            router.sendPacket(ethernetPacket, iface);
        }
    }

    void sendResponse(Iface iface){
        RIPv2 ripPacket = createRipResponsePacket();
        UDP udpPacket = createUdpPacket(ripPacket);
        IPv4 ipPacket = createIpPacket(iface, udpPacket);
        Ethernet ethernetPacket = createEthernetPacket(iface, ipPacket);
        router.sendPacket(ethernetPacket, iface);
    }

    boolean isRipPacket(IPv4 ipPacket) {
        return ipPacket.getDestinationAddress() == RIPHandler.RIP_IP_ADDRESS &&
                ipPacket.getProtocol() == IPv4.PROTOCOL_UDP &&
                ((UDP) ipPacket.getPayload()).getDestinationPort() == UDP.RIP_PORT;
    }

    void handlePacket(IPv4 ipPacket, Iface iface) {
        RIPv2 ripPacket = (RIPv2) ipPacket.getPayload().getPayload();
        switch (ripPacket.getCommand()) {
            case RIPv2.COMMAND_REQUEST:
                handleRequset(iface);
                break;
            case RIPv2.COMMAND_RESPONSE:
                handleResponse(iface);
                break;
        } 
    }

    void handleRequset(Iface iface){
        sendResponse(iface);
    }

    void handleResponse(Iface iface){
        for (RouteEntry routeEntry : routeTable.entries) {
            // TODO: Update Route table
        }
    }
}
