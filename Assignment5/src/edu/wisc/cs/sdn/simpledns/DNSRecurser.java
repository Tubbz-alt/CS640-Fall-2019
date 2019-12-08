package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DNSRecurser {
    private DNS origPacket;

    DNSRecurser(DNS origPacket) {
        this.origPacket = origPacket;
    }

    DNS recurse(InetAddress dnsServer, DNSQuestion question) throws IOException {
        DNS resultPacket = createNewPacket(question);
        DNS responsePacket = DNSServer.askDNSServer(dnsServer, resultPacket);

        // Check answer
        boolean found = false;
        for (DNSResourceRecord answer : responsePacket.getAnswers()) {
            if (answer.getType() == question.getType()) {
                resultPacket.addAnswer(answer);
                if (answer.getType() == DNS.TYPE_A) {
                    String hostAddress = getAddressFromRecord(answer).getHostAddress();
                    addEc2TXT(resultPacket, hostAddress);
                }
                found = true;
            } else if (answer.getType() == DNS.TYPE_CNAME) {
                resultPacket.addAnswer(answer);
            }
        }
        if (found) return resultPacket;

        for (DNSResourceRecord answer : new ArrayList<>(resultPacket.getAnswers())) {
            if (!answer.getName().equals(question.getName()))
                resultPacket.getAnswers().remove(answer);
        }

        // Check CNAME
        for (DNSResourceRecord answer : responsePacket.getAnswers()) {
            if (answer.getType() != DNS.TYPE_CNAME) continue;
            if (!answer.getName().equals(question.getName())) continue;
            String name = ((DNSRdataName) answer.getData()).getName();
            DNS newResponse = recurse(DNSServer.rootServer, new DNSQuestion(name, question.getType()));
            for (DNSResourceRecord ans : newResponse.getAnswers()) {
                resultPacket.addAnswer(ans);
            }
            return resultPacket;
        }

        List<DNSResourceRecord> authorities = new ArrayList<>(responsePacket.getAuthorities());

        // Check Authorities in Additional Section
        for (DNSResourceRecord authority : responsePacket.getAuthorities()) {
            if (authority.getType() != DNS.TYPE_NS) continue;
            String name = ((DNSRdataName) authority.getData()).getName();
            InetAddress address = findNameServerAddressInAdditional(name, responsePacket);

            if (address == null) // cannot find in the additional section
                continue;

            authorities.remove(authority);
            DNS newPacket = recurse(address, question);
            for (DNSResourceRecord answer : newPacket.getAnswers()) {
                if (answer.getType() == question.getType())
                    return newPacket;
            }
        }

        // Find Authorities' IP from Root
        for (DNSResourceRecord authority : authorities) {
            if (authority.getType() != DNS.TYPE_NS) continue;
            String name = ((DNSRdataName) authority.getData()).getName();
            DNS newResponse = recurse(DNSServer.rootServer, new DNSQuestion(name, DNS.TYPE_A));
            InetAddress address = getARecordFromDNSPacket(newResponse);
            if (address == null) // cannot find the Authorities' IP from Root
                continue;
            DNS newPacket = recurse(address, question);
            for (DNSResourceRecord answer : newPacket.getAnswers()) {
                if (answer.getType() == question.getType())
                    return newPacket;
            }
        }

        return origPacket;
    }

    private InetAddress getAddressFromRecord(DNSResourceRecord answer) {
        return ((DNSRdataAddress) answer.getData()).getAddress();
    }

    private InetAddress getARecordFromDNSPacket(DNS responseDNSPacket) {
        for (DNSResourceRecord answer : responseDNSPacket.getAnswers()) {
            if (answer.getType() != DNS.TYPE_A) continue;
            return getAddressFromRecord(answer);
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


    private void addEc2TXT(DNS returnDNSPacket, String hostAddress) {
        String hostName = origPacket.getQuestions().get(0).getName();
        for (Subnet subnet : DNSServer.ec2Region) {
            if (!subnet.inRange(hostAddress)) continue;
            DNSRdataString string = new DNSRdataString(subnet.toString(hostAddress));
            DNSResourceRecord record = new DNSResourceRecord(hostName, DNS.TYPE_TXT, string);
            returnDNSPacket.addAnswer(record);
            return;
        }
    }

    private DNS createNewPacket(DNSQuestion question) {
        DNS newPacket = origPacket.clone();
        newPacket.setQuestions(Collections.singletonList(question));
        newPacket.setRecursionDesired(false);
        return newPacket;
    }
}
