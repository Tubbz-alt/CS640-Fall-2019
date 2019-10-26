package edu.wisc.cs.sdn.vnet.rt;

import java.util.LinkedList;
import java.util.Queue;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

class ArpRequestQueueElement{
    Ethernet ethernetPacket;
    Iface inIface;

    public ArpRequestQueueElement(Ethernet ethernetPacket, Iface inIface) {
        this.ethernetPacket = ethernetPacket;
        this.inIface = inIface;
    }
}

class ArpRequestQueue {
    private ArpHandler arpHandler;
    private Iface outIface;
    private final Queue<ArpRequestQueueElement> queue;
    private Thread thread;

    ArpRequestQueue(final ArpHandler arpHandler, final int ipAddress, final Iface outIface) {
        this.arpHandler = arpHandler;
        this.outIface = outIface;
        this.queue = new LinkedList<>();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++) {
                    arpHandler.sendRequest(outIface, ipAddress);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                
                for (ArpRequestQueueElement e : queue){
                    IPv4 ipPacket = (IPv4) e.ethernetPacket.getPayload();
                    arpHandler.router.icmpHandler.sendMessage(e.inIface, ipPacket, 3, 1);
                }

                arpHandler.removeQueueFromTable(ipAddress);
            }
        };

        this.thread = new Thread(runnable);
        this.thread.start();
    }

    void handleResponse(MACAddress macAddress){
        this.thread.interrupt();
        for (ArpRequestQueueElement e : queue){
            e.ethernetPacket.setDestinationMACAddress(macAddress.toBytes());
            arpHandler.router.sendPacket(e.ethernetPacket, outIface);
        }
    }

    void add(Ethernet ethernetPacket, Iface inIface) {
        synchronized (queue) {
            queue.add(new ArpRequestQueueElement(ethernetPacket, inIface));
        }
    }
}
