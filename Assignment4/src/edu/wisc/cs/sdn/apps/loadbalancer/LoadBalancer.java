package edu.wisc.cs.sdn.apps.loadbalancer;

import edu.wisc.cs.sdn.apps.util.ArpServer;
import edu.wisc.cs.sdn.apps.util.Instructions;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.util.MACAddress;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.instruction.OFInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LoadBalancer implements IFloodlightModule, IOFSwitchListener,
		IOFMessageListener
{
	public static final String MODULE_NAME = LoadBalancer.class.getSimpleName();

	private static final byte TCP_FLAG_SYN = 0x02;

	private static final short IDLE_TIMEOUT = 20;

	// Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;

    // Interface to device manager service
    private IDeviceService deviceProv;

    // Switch table in which rules should be installed
    private byte table;

    // Set of virtual IPs and the load balancer instances they correspond with
    private Map<Integer,LoadBalancerInstance> instances;

    /**
     * Loads dependencies and initializes data structures.
     */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));

		// Obtain table number from config
		Map<String,String> config = context.getConfigParams(this);
        this.table = Byte.parseByte(config.get("table"));

        // Create instances from config
        this.instances = new HashMap<Integer,LoadBalancerInstance>();
        String[] instanceConfigs = config.get("instances").split(";");
        for (String instanceConfig : instanceConfigs)
        {
        	String[] configItems = instanceConfig.split(" ");
        	if (configItems.length != 3)
        	{
        		log.error("Ignoring bad instance config: " + instanceConfig);
        		continue;
        	}
        	LoadBalancerInstance instance = new LoadBalancerInstance(
        			configItems[0], configItems[1], configItems[2].split(","));
            this.instances.put(instance.getVirtualIP(), instance);
            log.info("Added load balancer instance: " + instance);
        }

		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);
	}

	/**
     * Subscribes to events and performs other startup tasks.
     */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.floodlightProv.addOFMessageListener(OFType.PACKET_IN, this);
	}

	/**
     * Event handler called when a switch joins the network.
     * @param DPID for the switch
     */
	@Override
	public void switchAdded(long switchId)
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));

		for (Integer virtualIp : instances.keySet()) {
			OFMatch matchTcp = new OFMatch()
					.setDataLayerType(OFMatch.ETH_TYPE_IPV4)
					.setNetworkDestination(virtualIp)
					.setNetworkProtocol(OFMatch.IP_PROTO_TCP);

			OFMatch matchArp = new OFMatch()
					.setDataLayerType(OFMatch.ETH_TYPE_ARP)
					.setNetworkDestination(virtualIp);

			List<OFInstruction> instructions = Instructions.redirectToController();

			SwitchCommands.removeRules(sw, table, matchTcp);
			SwitchCommands.removeRules(sw, table, matchArp);

			SwitchCommands.installRule(sw, table, (byte) 2, matchTcp, instructions);
			SwitchCommands.installRule(sw, table, (byte) 2, matchArp, instructions);
		}

		SwitchCommands.installRule(sw, table, SwitchCommands.DEFAULT_PRIORITY, new OFMatch(), Instructions.goToTable());
	}

	/**
	 * Handle incoming packets sent from switches.
	 * @param sw switch on which the packet was received
	 * @param msg message from the switch
	 * @param cntx the Floodlight context in which the message should be handled
	 * @return indication whether another module should also process the packet
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
	{
		// We're only interested in packet-in messages
		if (msg.getType() != OFType.PACKET_IN)
		{ return Command.CONTINUE; }
		OFPacketIn pktIn = (OFPacketIn)msg;

		// Handle the packet
		Ethernet ethPkt = new Ethernet();
		ethPkt.deserialize(pktIn.getPacketData(), 0,
				pktIn.getPacketData().length);

		switch (ethPkt.getEtherType()) {
			case OFMatch.ETH_TYPE_ARP:
				handleArpReply(sw, ethPkt, (short) pktIn.getInPort());
				break;
			case OFMatch.ETH_TYPE_IPV4:
				handleIpv4Received(sw, ethPkt);
				break;
		}

		return Command.CONTINUE;
	}


	private void handleArpReply(IOFSwitch sw, Ethernet inEthPkt, short inPort){
		ARP inArpPkt = (ARP) inEthPkt.getPayload();
		if (inArpPkt.getOpCode() != ARP.OP_REQUEST) return;

		int ip = IPv4.toIPv4Address(inArpPkt.getTargetProtocolAddress());
		LoadBalancerInstance instance = instances.get(ip);
		if (instance == null) return;
		byte[] mac = instance.getVirtualMAC();

		ARP outArpPkt = new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
				.setProtocolAddressLength((byte) 4)
				.setOpCode(ARP.OP_REPLY)
				.setTargetHardwareAddress(inArpPkt.getSenderHardwareAddress())
				.setTargetProtocolAddress(inArpPkt.getTargetProtocolAddress())
				.setSenderHardwareAddress(mac)
				.setSenderProtocolAddress(ip);

		Ethernet outEthPkt = (Ethernet) new Ethernet()
				.setEtherType(Ethernet.TYPE_ARP)
				.setSourceMACAddress(mac)
				.setDestinationMACAddress(inEthPkt.getSourceMACAddress())
				.setPayload(outArpPkt);

		SwitchCommands.sendPacket(sw, inPort, outEthPkt);
	}

	private void handleIpv4Received(IOFSwitch sw, Ethernet inEthPkt) {
		IPv4 inIpPkt = (IPv4) inEthPkt.getPayload();
		if (inIpPkt.getProtocol() != IPv4.PROTOCOL_TCP) return;
		TCP inTcpPkt = (TCP) inIpPkt.getPayload();
		if (inTcpPkt.getFlags() != TCP_FLAG_SYN) return;

		LoadBalancerInstance instance = instances.get(inIpPkt.getDestinationAddress());
		if (instance == null) return;

		byte protocol = inIpPkt.getProtocol();

		short clientPort = inTcpPkt.getSourcePort();
		short hostPort = inTcpPkt.getDestinationPort();

		int clientIp = inIpPkt.getSourceAddress();
		int virtualIp = inIpPkt.getDestinationAddress();
		int hostIp = instance.getNextHostIP();

		byte[] virtualMac = instance.getVirtualMAC();
		byte[] hostMac = getHostMACAddress(hostIp);

		OFMatch matchToHost = new OFMatch()
				.setDataLayerType(OFMatch.ETH_TYPE_IPV4)
				.setNetworkProtocol(protocol)
				.setNetworkSource(clientIp)
				.setNetworkDestination(virtualIp)
				.setTransportSource(clientPort)
				.setTransportDestination(hostPort);

		SwitchCommands.installRule(sw, table, SwitchCommands.MAX_PRIORITY, matchToHost,
				Instructions.rewriteDest(hostMac, hostIp), (short) 0, (short) 20);

		OFMatch matchFromHost = new OFMatch()
				.setDataLayerType(OFMatch.ETH_TYPE_IPV4)
				.setNetworkProtocol(protocol)
				.setNetworkSource(hostIp)
				.setNetworkDestination(clientIp)
				.setTransportSource(hostPort)
				.setTransportDestination(clientPort);

		SwitchCommands.installRule(sw, table, SwitchCommands.MAX_PRIORITY, matchFromHost,
				Instructions.rewriteSrc(virtualMac, virtualIp), (short) 0, (short) 20);
	}


	/**
	 * Returns the MAC address for a host, given the host's IP address.
	 * @param hostIPAddress the host's IP address
	 * @return the hosts's MAC address, null if unknown
	 */
	private byte[] getHostMACAddress(int hostIPAddress)
	{
		Iterator<? extends IDevice> iterator = this.deviceProv.queryDevices(
				null, null, hostIPAddress, null, null);
		if (!iterator.hasNext())
		{ return null; }
		IDevice device = iterator.next();
		return MACAddress.valueOf(device.getMACAddress()).toBytes();
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId)
	{ /* Nothing we need to do, since the switch is no longer active */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId)
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type)
	{ /* Nothing we need to do, since load balancer rules are port-agnostic */}

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId)
	{ /* Nothing we need to do */ }

    /**
     * Tell the module system which services we provide.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices()
	{ return null; }

	/**
     * Tell the module system which services we implement.
     */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
			getServiceImpls()
	{ return null; }

	/**
     * Tell the module system which modules we depend on.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>>
			getModuleDependencies()
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
	            new ArrayList<Class<? extends IFloodlightService>>();
        floodlightService.add(IFloodlightProviderService.class);
        floodlightService.add(IDeviceService.class);
        return floodlightService;
	}

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName()
	{ return MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name)
	{
		return (OFType.PACKET_IN == type
				&& (name.equals(ArpServer.MODULE_NAME)
					|| name.equals(DeviceManagerImpl.MODULE_NAME)));
	}

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name)
	{ return false; }
}
