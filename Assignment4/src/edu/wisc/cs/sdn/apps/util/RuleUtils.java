package edu.wisc.cs.sdn.apps.util;

import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;

import java.util.ArrayList;
import java.util.List;

public class RuleUtils {


    public static List<OFInstruction> getRedirectToPortInstructions(int portNumber) {
        OFActionOutput actionOutput = new OFActionOutput().setPort(portNumber);

        OFInstructionApplyActions instruction = new OFInstructionApplyActions();
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(actionOutput);
        instruction.setActions(actions);

        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        instructions.add(instruction);
        return instructions;
    }

    public static List<OFInstruction> getRedirectToControllerInstructions() {
        return getRedirectToPortInstructions(OFPort.OFPP_CONTROLLER.getValue());
    }
}
