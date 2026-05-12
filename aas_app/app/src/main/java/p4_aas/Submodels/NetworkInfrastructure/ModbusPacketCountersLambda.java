package p4_aas.Submodels.NetworkInfrastructure;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.basyx.submodel.metamodel.map.submodelelement.SubmodelElement;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;

import p4_aas.Submodels.SwitchRuntime.SwitchCliClient;

public class ModbusPacketCountersLambda {
    private static final String REGISTER_NAME = "modbus_function_code_packet_counts";
    private static final Pattern REGISTER_VALUES_PATTERN = Pattern.compile("=\\s*([0-9]+(?:,\\s*[0-9]+)*)");
    private final SwitchCliClient switchCliClient;

    public ModbusPacketCountersLambda() {
        this.switchCliClient = new SwitchCliClient();
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> readCounters() {
        return (args) -> {
            int switchId = getInt(args, "Switch");
            String cliOutput = switchCliClient.runCliCommand(switchId, "register_read " + REGISTER_NAME);
            String[] counts = parseCounts(cliOutput);
            return new SubmodelElement[] {
                new Property("ObservationSwitch", String.valueOf(switchId)),
                new Property("FC1Count", counts[0]),
                new Property("FC2Count", counts[1]),
                new Property("FC3Count", counts[2]),
                new Property("FC4Count", counts[3]),
                new Property("FC5Count", counts[4]),
                new Property("FC6Count", counts[5])
            };
        };
    }

    private String[] parseCounts(String cliOutput) {
        String[] fallback = new String[] {
            "parse-error",
            "parse-error",
            "parse-error",
            "parse-error",
            "parse-error",
            "parse-error"
        };

        Matcher matcher = REGISTER_VALUES_PATTERN.matcher(cliOutput);
        String matchedValues = null;
        while (matcher.find()) {
            matchedValues = matcher.group(1);
        }

        if (matchedValues == null) {
            return fallback;
        }

        String[] rawValues = matchedValues.split(",\\s*");
        if (rawValues.length < 6) {
            return fallback;
        }

        return new String[] {
            rawValues[0],
            rawValues[1],
            rawValues[2],
            rawValues[3],
            rawValues[4],
            rawValues[5]
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
}
