package edu.wisc.cs.sdn.simpledns;

public class Subnet {
    private String region;
    private int ipValue;
    private int mask;

    Subnet(String line) {
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