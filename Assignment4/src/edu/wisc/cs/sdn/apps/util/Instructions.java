package edu.wisc.cs.sdn.apps.util;

import edu.wisc.cs.sdn.apps.l3routing.L3Routing;
import org.openflow.protocol.OFOXMField;
import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionGotoTable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Instructions {

    private static List<OFInstruction> rewrite(OFOXMFieldType macType, byte[] mac, OFOXMFieldType ipType, int ip) {
        return Arrays.asList(
                new OFInstructionApplyActions().setActions(Arrays.asList(
                        (OFAction) new OFActionSetField().setField(new OFOXMField(macType, mac)),
                        (OFAction) new OFActionSetField().setField(new OFOXMField(ipType, ip))
                )),
                new OFInstructionGotoTable(L3Routing.table)
        );
    }

    public static List<OFInstruction> rewriteDest(byte[] mac, int ip) {
        return rewrite(OFOXMFieldType.ETH_DST, mac, OFOXMFieldType.IPV4_DST, ip);
    }

    public static List<OFInstruction> rewriteSrc(byte[] mac, int ip) {
        return rewrite(OFOXMFieldType.ETH_SRC, mac, OFOXMFieldType.IPV4_SRC, ip);
    }

    public static List<OFInstruction> redirectToPort(int portNumber) {
        return Collections.singletonList(
                (OFInstruction) new OFInstructionApplyActions().setActions(Collections.singletonList(
                        (OFAction) new OFActionOutput().setPort(portNumber)
                ))
        );
    }

    public static List<OFInstruction> redirectToController() {
        return redirectToPort(OFPort.OFPP_CONTROLLER.getValue());
    }

    public static List<OFInstruction> goToTable() {
        return Collections.singletonList((OFInstruction) new OFInstructionGotoTable(L3Routing.table));
    }
}
