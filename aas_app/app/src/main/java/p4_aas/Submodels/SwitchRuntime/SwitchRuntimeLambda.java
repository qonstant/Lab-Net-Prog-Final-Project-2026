package p4_aas.Submodels.SwitchRuntime;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.basyx.submodel.metamodel.map.submodelelement.SubmodelElement;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;

public class SwitchRuntimeLambda {
    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9_.]+";
    private final SwitchCliClient switchCliClient;

    public SwitchRuntimeLambda() {
        this.switchCliClient = new SwitchCliClient();
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> showTables() {
        return (args) -> output(switchCliClient.runCliCommand(getInt(args, "Switch"), "show_tables"));
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> dumpTable() {
        return (args) -> {
            String table = getIdentifier(args, "Table");
            if (table == null) {
                return output("Invalid table name");
            }
            return output(switchCliClient.runCliCommand(getInt(args, "Switch"), "table_dump " + table));
        };
    }

    public Function<Map<String, SubmodelElement>, SubmodelElement[]> readRegister() {
        return (args) -> {
            String register = getIdentifier(args, "Register");
            if (register == null) {
                return output("Invalid register name");
            }
            return output(switchCliClient.runCliCommand(getInt(args, "Switch"), "register_read " + register));
        };
    }

    private SubmodelElement[] output(String value) {
        return new SubmodelElement[] {
            new Property("Output", value)
        };
    }

    private String getIdentifier(Map<String, SubmodelElement> args, String name) {
        Object value = args.get(name).getValue();
        String identifier = String.valueOf(value);
        if (!identifier.matches(IDENTIFIER_PATTERN)) {
            return null;
        }
        return identifier;
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
