package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

class DNSServer {
    private static final int BUFFER_SIZE = 8192;
    private static final int DNS_PORT = 53;
    private static final int PORT = 8053;

    private InetAddress rootServer;
    private DatagramSocket clientSocket;
    private List<Subnet> ec2Region;

    DNSServer(String rootServerIp, String ec2Csv) throws IOException {
        this.rootServer = Inet4Address.getByName(rootServerIp);
        this.ec2Region = CSVParser.parse(ec2Csv);
        this.clientSocket = new DatagramSocket(PORT);
    }

    void start() throws IOException {
        while (true) {
            DNS dnsPacket = receiveDNSPacket(this.clientSocket);
            if (dnsPacket.getOpcode() != DNS.OPCODE_STANDARD_QUERY) continue;
            handleDNSPacket(dnsPacket);
        }
    }

    private DNS receiveDNSPacket(DatagramSocket socket) throws IOException {
        DatagramPacket datagramPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
        socket.receive(datagramPacket);
        socket.connect(datagramPacket.getSocketAddress());
        return DNS.deserialize(datagramPacket.getData(), BUFFER_SIZE);
    }

    private void sendDNSPacket(DatagramSocket socket, DNS dnsPacket) throws IOException {
        byte[] buffer = dnsPacket.serialize();
        socket.send(new DatagramPacket(buffer, buffer.length));
        socket.disconnect();
    }


    private DNS askDNSServer(InetAddress dnsServer, DNS dnsPacket) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(DNS_PORT)) {
            socket.connect(dnsServer, DNS_PORT);
            sendDNSPacket(socket, dnsPacket);
            DNS dns = receiveDNSPacket(socket);
            System.out.println(" ================== Requested " + dnsServer + " ================== ");
            System.out.println(dns);
            return dns;
        }
    }

    private DNS recurse(DNS inDNSPacket) throws IOException {
        final DNSQuestion inQuestion = inDNSPacket.getQuestions().get(0);
        DNS returnDNSPacket = new DNS();
        returnDNSPacket.setQuestions(inDNSPacket.getQuestions());
        returnDNSPacket.setId(inDNSPacket.getId());

        DNS responseDNSPacket = askDNSServer(rootServer, inDNSPacket);
        while (true) {
            boolean found = false;
            for (DNSResourceRecord answer : responseDNSPacket.getAnswers()) {
                if (answer.getType() == inQuestion.getType()) {
                    returnDNSPacket.addAnswer(answer);
                    if (answer.getType() == DNS.TYPE_A) {
                        String hostAddress = ((DNSRdataAddress) answer.getData()).getAddress().getHostAddress();
                        String hostName = inQuestion.getName();
                        addEc2TXT(returnDNSPacket, hostAddress, hostName);
                    }
                    found = true;
                } else if (answer.getType() == DNS.TYPE_CNAME) {
                    returnDNSPacket.addAnswer(answer);
                }
            }
            if (found) return returnDNSPacket;

            for (DNSResourceRecord answer : responseDNSPacket.getAnswers()) {
                if (answer.getType() != DNS.TYPE_CNAME) continue;
                String name = ((DNSRdataName) answer.getData()).getName();
                DNSQuestion question = new DNSQuestion(name, inQuestion.getType());
                inDNSPacket.setQuestions(Collections.singletonList(question));
                responseDNSPacket = askDNSServer(rootServer, inDNSPacket);
            }

            responseDNSPacket = resolveNSRecord(inDNSPacket, responseDNSPacket);
            if (responseDNSPacket == null) return returnDNSPacket;
        }
    }

    private InetAddress getARecordFromDNSPacket(DNS responseDNSPacket) {
        for (DNSResourceRecord answer : responseDNSPacket.getAnswers()) {
            if (answer.getType() != DNS.TYPE_A) continue;
            return ((DNSRdataAddress) answer.getData()).getAddress();
        }
        return null;
    }

    private DNS resolveNSRecord(DNS origDNSPacket, DNS responseDNSPacket) throws IOException {
        for (DNSResourceRecord authority : responseDNSPacket.getAuthorities()) {
            if (authority.getType() != DNS.TYPE_NS) continue;
            String name = ((DNSRdataName) authority.getData()).getName();
            InetAddress address = findNameServerAddressInAdditional(name, responseDNSPacket);
            if (address != null) return askDNSServer(address, origDNSPacket);
        }

        for (DNSResourceRecord authority : responseDNSPacket.getAuthorities()) {
            if (authority.getType() != DNS.TYPE_NS) continue;
            String name = ((DNSRdataName) authority.getData()).getName();
            DNS inDNSPacket = origDNSPacket.clone();
            DNSQuestion question = new DNSQuestion(name, DNS.TYPE_A);
            inDNSPacket.setQuestions(Collections.singletonList(question));
            InetAddress address = getARecordFromDNSPacket(recurse(inDNSPacket));
            if (address != null) return askDNSServer(address, origDNSPacket);
        }
        return null;
    }

    private InetAddress findNameServerAddressInAdditional(String name, DNS responseDNSPacket) {
        for (DNSResourceRecord record : responseDNSPacket.getAdditional()) {
            if (record.getType() != DNS.TYPE_A) continue;
            if (!record.getName().equals(name)) continue;
            return ((DNSRdataAddress) record.getData()).getAddress();
        }
        return null;
    }

    private void addEc2TXT(DNS returnDNSPacket, String hostAddress, String hostName) {
        for (Subnet subnet : this.ec2Region) {
            if (!subnet.inRange(hostAddress)) continue;
            DNSRdataString string = new DNSRdataString(subnet.toString(hostAddress));
            DNSResourceRecord record = new DNSResourceRecord(hostName, DNS.TYPE_TXT, string);
            returnDNSPacket.addAnswer(record);
            return;
        }
    }

    private void handleDNSPacket(DNS inDNSPacket) throws IOException {
        if (inDNSPacket.getQuestions().size() == 0) return;
        switch (inDNSPacket.getQuestions().get(0).getType()) {
            case DNS.TYPE_A:
            case DNS.TYPE_AAAA:
            case DNS.TYPE_NS:
            case DNS.TYPE_CNAME:
                if (inDNSPacket.isRecursionDesired())
                    sendDNSPacket(this.clientSocket, recurse(inDNSPacket));
                else
                    sendDNSPacket(this.clientSocket, askDNSServer(rootServer, inDNSPacket));
                break;
            default:
                this.clientSocket.disconnect();
        }
    }
}
