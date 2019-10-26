package edu.wisc.cs.sdn.vnet.rt;

import java.util.LinkedList;
import java.util.Queue;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;

class ArpRequestQueueElement{
    Ethernet ethernet;
    Iface inIface;
}

class ArpRequestQueue {
    private ArpHandler arpHandler;
    private int ipAddress;
    private final Queue<Ethernet> queue;
    private Runnable runnable;
    private Thread thread;


    ArpRequestQueue(final ArpHandler arpHandler, final int ipAddress) {
        this.arpHandler = arpHandler;
        this.ipAddress = ipAddress;
        this.queue = new LinkedList<>();
        this.runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++) {
                    // Iter through all iface
                    // arpHandler.sendRequest(inIface, ipAddress);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                arpHandler.removeQueue(ipAddress);
                // arpHandler.router.icmpHandler.sendMessage(inIface, ipPacket, icmpType, icmpCode);
                // TODO: send ICMP unreachable for all pkt in Q
            }
        };

        this.thread = new Thread(this.runnable);
    }

    void startSendingArpRequest() {
        this.thread.start();
    }

    void stopSendingArpRequest(){
        this.thread.interrupt();
    }

    void add(Ethernet etherPacket) {
        synchronized (queue) {
            queue.add(etherPacket);
        }
    }


}
