package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;

import java.util.LinkedList;
import java.util.Queue;

class ArpRequestQueue {
    private ArpHandler arpHandler;
    private Iface inIface;
    private int IpAddress;
    private Queue<Ethernet> queue;
    private Runnable runnable;
    private Thread thread;


    ArpRequestQueue(final ArpHandler arpHandler, final Iface inIface, final int ipAddress) {
        this.arpHandler = arpHandler;
        this.inIface = inIface;
        this.IpAddress = ipAddress;
        this.queue = new LinkedList<>();
        this.runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3; i++) {
                    arpHandler.sendRequest(inIface, ipAddress);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };

        this.thread = new Thread(this.runnable);
    }

    void startSendingRequest() {
        this.thread.start();
    }


}
