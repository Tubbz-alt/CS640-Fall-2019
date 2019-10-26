package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.*;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets                                             */

		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }

        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl()) {
			sendIcmpMessage(inIface, ipPacket, 11, 0);
			return;
		}

        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();

        // Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values()) {
			if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
				byte protocol = ipPacket.getProtocol();
				switch (protocol) {
					case IPv4.PROTOCOL_UDP:
					case IPv4.PROTOCOL_TCP:
						sendIcmpMessage(inIface, ipPacket, 3, 3);
						break;
					case IPv4.PROTOCOL_ICMP:
						ICMP icmp = (ICMP) ipPacket.getPayload();
						if (icmp.getIcmpType() == 8) {
							// TODO: echo reply
						}
						break;
				}
			}
        }

        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

	private MACAddress getMacByIp(int ipAddr) {
		// Find matching route table entry
		RouteEntry bestMatch = this.routeTable.lookup(ipAddr);

		// If no entry matched, do nothing
		if (null == bestMatch) { return null; }

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop) { nextHop = ipAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry) { return null; }

		return arpEntry.getMac();
	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
		if (null == bestMatch) {
			sendIcmpMessage(inIface, ipPacket, 3, 0);
			return;
		}

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry) {
			sendIcmpMessage(inIface, ipPacket, 3, 1);
			return;
		}
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

        this.sendPacket(etherPacket, outIface);
    }

	private void sendIcmpMessage(Iface inIface, IPv4 ipPacket, int icmpType, int icmpCode) {
		Ethernet ether = getEthernetPacket(inIface, ipPacket, icmpType, icmpCode);
		if (ether != null) sendPacket(ether, inIface);
	}

	private Data getIcmpData(IPv4 ipPacket) {
		Data data = new Data();
		int ipHeaderNumBytes = ipPacket.getHeaderLength() * 4;
		int icmpDataNumBytes = 4 + ipHeaderNumBytes + 8;
		byte[] icmpDataBytes = new byte[icmpDataNumBytes];
		byte[] ipPacketBytes = ipPacket.serialize();

		System.arraycopy(ipPacketBytes, 0, icmpDataBytes, 4, icmpDataNumBytes - 4);
		data.setData(icmpDataBytes);

		return data;
	}

	private ICMP getIcmpPacket(IPv4 ipPacket, int icmpType, int icmpCode) {
		ICMP icmp = new ICMP();
		icmp.setIcmpType((byte) icmpType);
		icmp.setIcmpCode((byte) icmpCode);
		icmp.setPayload(getIcmpData(ipPacket));
		return icmp;
	}

	private IPv4 getIpPacket(Iface inIface, IPv4 ipPacket, int icmpType, int icmpCode) {
		IPv4 ip = new IPv4();
		ip.setTtl((byte) 64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setSourceAddress(inIface.getIpAddress());
		ip.setDestinationAddress(ipPacket.getSourceAddress());
		ip.setPayload(getIcmpPacket(ipPacket, icmpType, icmpCode));
		return ip;
	}

	private Ethernet getEthernetPacket(Iface inIface, IPv4 ipPacket, int icmpType, int icmpCode) {
		Ethernet ether = new Ethernet();
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());

		MACAddress destinationMACAddress = getMacByIp(ipPacket.getSourceAddress());
		if (destinationMACAddress == null) return null;
		ether.setDestinationMACAddress(destinationMACAddress.toBytes());

		ether.setPayload(getIpPacket(inIface, ipPacket, icmpType, icmpCode));
		return ether;
	}
}
