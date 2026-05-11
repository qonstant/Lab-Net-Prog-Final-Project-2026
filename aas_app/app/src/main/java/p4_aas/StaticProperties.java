package p4_aas;

/**
 * To manage System properties, used to mantain info throughout the app.
 */
public class StaticProperties {
    public static final String REGISTRY_POLLING_IP = "http://100.0.2.1:4000/registry/api/v1/registry";
    public static final String REGISTRYPATH = "http://100.0.2.1:4000/registry/";
    public static final String AAS_PUBLIC_HOST = System.getenv().getOrDefault("AAS_PUBLIC_HOST", "10.0.2.15");

    public static final String SW1_MANAGEMENT_IP = "100.0.1.11";
    public static final String SW2_MANAGEMENT_IP = "100.0.1.12";

    public static final String MODBUS_CLIENT_IP = "195.11.14.5";
    public static final String MODBUS_SERVER_IP = "200.1.1.7";
    public static final int MODBUS_PORT = 502;
    public static final int MODBUS_TLS_PORT = 5020;

    public static final int NetworkInfrastructurePort = 6001;
    public static final int NetworkControlPlanePort = 6002;
    public static final int ModbusClientPort = 6003;
    public static final int ModbusServerPort = 6004;
}
