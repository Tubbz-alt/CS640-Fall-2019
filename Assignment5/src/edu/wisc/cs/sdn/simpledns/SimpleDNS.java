package edu.wisc.cs.sdn.simpledns;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SimpleDNS {
    public static void main(String[] args) throws IOException {
        String rootServerIp = args[1];
        String ec2Csv = args[3];

        List<SubNet> ec2Region = parseCsv(ec2Csv);
    }

    private static List<SubNet> parseCsv(String filename) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
        List<SubNet> result = new ArrayList<>();
        for (String line : lines) {
            result.add(new SubNet(line));
        }
        return result;
    }
}

class SubNet {
    private String region;
    private int ipValue;
    private int mask;

    SubNet(String line) {
        String[] parts = line.split("[,/]");
        this.ipValue = parseIp(parts[0]);
        this.mask = prefixToMask(Short.parseShort(parts[1]));
        this.region = parts[2];
    }

    private int prefixToMask(short prefix) {
        return -(1 << (32 - prefix));
    }

    private int parseIp(String ipString) {
        String[] parts = ipString.split("\\.");
        int result = 0;

        for (String part : parts) {
            result <<= 8;
            result += Short.parseShort(part);
        }

        return result;
    }

    public boolean inRange(String ipString) {
        return (parseIp(ipString) & this.mask) == (this.ipValue & this.mask);
    }

    public String toString(String ipString) {
        return this.region + '-' + ipString;
    }
}