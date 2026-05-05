package p4_aas.Submodels.Modbus;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.basyx.submodel.metamodel.map.submodelelement.SubmodelElement;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;

import p4_aas.StaticProperties;
import p4_aas.Submodels.Utils.SSHManager;

public class ModbusDeviceLambda {
    private final SSHManager sshManager;

    public ModbusDeviceLambda() {
        this.sshManager = new SSHManager();
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> testConnection() {
        return (args) -> output(runOnClient("test"));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> readCoils() {
        return (args) -> output(runOnClient(
            "read-coils --address " + getInt(args, "Address") + " --count " + getInt(args, "Count")));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> readDiscreteInputs() {
        return (args) -> output(runOnClient(
            "read-discrete-inputs --address " + getInt(args, "Address") + " --count " + getInt(args, "Count")));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> readHoldingRegisters() {
        return (args) -> output(runOnClient(
            "read-holding-registers --address " + getInt(args, "Address") + " --count " + getInt(args, "Count")));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> readRegister() {
        return (args) -> output(runOnClient("read --register " + getInt(args, "Register")));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> readInputRegisters() {
        return (args) -> output(runOnClient(
            "read-input-registers --address " + getInt(args, "Address") + " --count " + getInt(args, "Count")));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> writeCoil() {
        return (args) -> output(runOnClient(
            "write-coil --address " + getInt(args, "Address") + " --value " + getBoolean(args, "Value")));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> writeRegister() {
        return (args) -> output(runOnClient(
            "write --register " + getInt(args, "Register") + " --value " + getInt(args, "Value")));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> getStatus() {
        return (args) -> {
            String command = "/bin/sh -lc \"(pgrep -af 'server.py|server_tls.py' || true); (ss -ltn || netstat -ltn || true) | grep -E ':(502|5020)[[:space:]]' || true\"";
            return output(sshManager.executeSingleCommand(command, StaticProperties.MODBUS_SERVER_IP));
        };
    }

    private String runOnClient(String operation) {
        String command = "python /modbus_ops.py " + operation
            + " --host " + StaticProperties.MODBUS_SERVER_IP
            + " --port " + StaticProperties.MODBUS_PORT;
        return sshManager.executeSingleCommand(command, StaticProperties.MODBUS_CLIENT_IP);
    }

    private SubmodelElement[] output(String value) {
        return new SubmodelElement[] {
            new Property("Output", value)
        };
    }

    private int getInt(Map<String, SubmodelElement> args, String name) {
        Object value = args.get(name).getValue();
        if (value instanceof BigInteger) {
            return ((BigInteger) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean getBoolean(Map<String, SubmodelElement> args, String name) {
        Object value = args.get(name).getValue();
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
