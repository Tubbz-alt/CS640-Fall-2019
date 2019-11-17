package edu.wisc.cs.sdn.apps.util;

import org.openflow.protocol.OFOXMField;
import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;

import java.util.ArrayList;
import java.util.List;

public class RuleUtils {

    public static List<OFInstruction> getRewriteDestinationInstructions(byte[] mac, int ip) {
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionSetField().setField(new OFOXMField(OFOXMFieldType.ETH_DST, mac)));
        actions.add(new OFActionSetField().setField(new OFOXMField(OFOXMFieldType.IPV4_DST, ip)));
        return makeInstructionsFromActions(actions);
    }

    public static List<OFInstruction> getRewriteSourceInstructions(byte[] mac, int ip) {
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionSetField().setField(new OFOXMField(OFOXMFieldType.ETH_SRC, mac)));
        actions.add(new OFActionSetField().setField(new OFOXMField(OFOXMFieldType.IPV4_SRC, ip)));
        return makeInstructionsFromActions(actions);
    }

    public static List<OFInstruction> getRedirectToPortInstructions(int portNumber) {
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput().setPort(portNumber));
        return makeInstructionsFromActions(actions);
    }

    public static List<OFInstruction> getRedirectToControllerInstructions() {
        return getRedirectToPortInstructions(OFPort.OFPP_CONTROLLER.getValue());
    }

    private static List<OFInstruction> makeInstructionsFromActions(List<OFAction> actions) {
        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        instructions.add(new OFInstructionApplyActions().setActions(actions));
        return instructions;
    }
}
