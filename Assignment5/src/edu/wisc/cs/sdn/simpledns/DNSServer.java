package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

class DNSServer {
    private static final int BUFFER_SIZE = 8192;
    private static final int DNS_PORT = 53;
    private static final int PORT = 8053;

    static InetAddress rootServer;
    static List<Subnet> ec2Region;

    private DatagramSocket clientSocket;


    DNSServer(String rootServerIp, String ec2Csv) throws IOException {
        rootServer = Inet4Address.getByName(rootServerIp);
        ec2Region = CSVParser.parse(ec2Csv);
        this.clientSocket = new DatagramSocket(PORT);
    }

    void start() throws IOException {
        while (true) {
            DNS dnsPacket = receiveDNSPacket(this.clientSocket);
            if (dnsPacket.getOpcode() != DNS.OPCODE_STANDARD_QUERY) continue;
            handleDNSPacket(dnsPacket);
        }
    }

    private static DNS receiveDNSPacket(DatagramSocket socket) throws IOException {
        DatagramPacket datagramPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
        socket.receive(datagramPacket);
        socket.connect(datagramPacket.getSocketAddress());
        return DNS.deserialize(datagramPacket.getData(), BUFFER_SIZE);
    }

    private static void sendDNSPacket(DatagramSocket socket, DNS dnsPacket) throws IOException {
        byte[] buffer = dnsPacket.serialize();
        socket.send(new DatagramPacket(buffer, buffer.length));
        socket.disconnect();
    }


    static DNS askDNSServer(InetAddress dnsServer, DNS dnsPacket) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(DNS_PORT)) {
            socket.connect(dnsServer, DNS_PORT);
            sendDNSPacket(socket, dnsPacket);
            DNS dns = receiveDNSPacket(socket);
            System.out.println(" ================== Requested " + dnsServer + " ================== ");
            System.out.println(dns);
            return dns;
        }
    }

    private void handleDNSPacket(DNS inDNSPacket) throws IOException {
        if (inDNSPacket.getQuestions().size() == 0) return;
        switch (inDNSPacket.getQuestions().get(0).getType()) {
            case DNS.TYPE_A:
            case DNS.TYPE_AAAA:
            case DNS.TYPE_NS:
            case DNS.TYPE_CNAME:
                if (inDNSPacket.isRecursionDesired()) {
                    DNSQuestion question =  inDNSPacket.getQuestions().get(0);
                    DNS packet = new DNSRecurser(inDNSPacket).recurse(DNSServer.rootServer, question);
                    sendDNSPacket(this.clientSocket, packet);
                } else {
                    sendDNSPacket(this.clientSocket, askDNSServer(rootServer, inDNSPacket));
                }
                break;
            default:
                this.clientSocket.disconnect();
        }
    }
}
