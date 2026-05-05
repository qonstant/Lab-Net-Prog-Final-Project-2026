package p4_aas.Submodels.SwitchRuntime;

import p4_aas.StaticProperties;
import p4_aas.Submodels.Utils.SSHManager;

public class SwitchCliClient {
    private final SSHManager sshManager;

    public SwitchCliClient() {
        this.sshManager = new SSHManager();
    }

    public String runCliCommand(int switchId, String cliCommand) {
        String host = getSwitchHost(switchId);
        if (host == null) {
            return "Unsupported switch: " + switchId;
        }

        return sshManager.executeSingleCommand("echo \"" + cliCommand + "\" | simple_switch_CLI", host);
    }

    private String getSwitchHost(int switchId) {
        if (switchId == 1) {
            return StaticProperties.SW1_MANAGEMENT_IP;
        }
        if (switchId == 2) {
            return StaticProperties.SW2_MANAGEMENT_IP;
        }
        return null;
    }
}
