package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

import java.util.LinkedList;
import java.util.Queue;

class ArpRequestQueue {
    static private class QueueElement {
        Ethernet ethernetPacket;
        Iface inIface;

        QueueElement(Ethernet ethernetPacket, Iface inIface) {
            this.ethernetPacket = ethernetPacket;
            this.inIface = inIface;
        }
    }

    private ArpHandler arpHandler;
    private Iface outIface;
    private final Queue<QueueElement> queue;
    private Thread thread;

    ArpRequestQueue(final ArpHandler arpHandler, final int ipAddress, final Iface outIface) {
        this.arpHandler = arpHandler;
        this.outIface = outIface;
        this.queue = new LinkedList<>();
        this.thread = new Thread(new Runnable() {
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

                for (QueueElement e : queue) {
                    IPv4 ipPacket = (IPv4) e.ethernetPacket.getPayload();
                    arpHandler.icmpHandler.sendMessage(e.inIface, ipPacket, 3, 1);
                }

                arpHandler.removeQueueFromTable(ipAddress);
            }
        });

        this.thread.start();
    }

    void handleResponse(MACAddress macAddress) {
        this.thread.interrupt();
        for (QueueElement e : queue) {
            e.ethernetPacket.setDestinationMACAddress(macAddress.toBytes());
            arpHandler.router.sendPacket(e.ethernetPacket, outIface);
        }
        queue.clear();
    }

    void add(Ethernet ethernetPacket, Iface inIface) {
        queue.add(new QueueElement(ethernetPacket, inIface));
    }
}
