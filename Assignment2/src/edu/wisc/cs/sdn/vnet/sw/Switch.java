package edu.wisc.cs.sdn.vnet.sw;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.HashMap;
import java.util.Map;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;



/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {
	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host hostname for the router
	 */

	public long TIMEOUT = 15000;

	private Map<MACAddress, SwitchTableEntry> switchTable = new HashMap<>();

	public Switch(String host, DumpFile logfile) {
		super(host, logfile);
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * 
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface     the interface on which the packet was received
	 */
 @Override
	public void handlePacket(Ethernet etherPacket, Iface inIface) {
		System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

		// Remove timeout
		switchTable.values().removeIf(value -> System.currentTimeMillis() - value.timeAdded > TIMEOUT);

		// Add source mac adress to table
		SwitchTableEntry srcEntry = switchTable.get(etherPacket.getSourceMAC());
		if (srcEntry != null) { // update timeout
			srcEntry.updateTimeout();
		} else { // add entry
			switchTable.put(etherPacket.getSourceMAC(), new SwitchTableEntry(inIface.getName()));
		}
		
		// Send the packet
		SwitchTableEntry destEntry = switchTable.get(etherPacket.getDestinationMAC());
		if (destEntry != null) { // send packet
			super.sendPacket(etherPacket, interfaces.get(destEntry.interfaceName));
		} else { // flood
			interfaces.values().forEach(e -> super.sendPacket(etherPacket, e));
		}
	}

	private static class SwitchTableEntry {
		String interfaceName;
		long timeAdded;

		public SwitchTableEntry(String name) {
			this.interfaceName = name;
			this.timeAdded = System.currentTimeMillis();
		}

		public void updateTimeout() {
			this.timeAdded = System.currentTimeMillis();
		}
	}
}
