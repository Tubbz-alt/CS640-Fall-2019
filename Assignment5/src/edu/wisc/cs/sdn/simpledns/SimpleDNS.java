package edu.wisc.cs.sdn.simpledns;

import java.io.IOException;

public class SimpleDNS {
    public static void main(String[] args) throws IOException {
        String rootServerIp = args[1];
        String ec2Csv = args[3];
        DNSServer server = new DNSServer(rootServerIp, ec2Csv);
        server.start();
    }
}