package p4_aas;

import org.eclipse.basyx.aas.metamodel.api.parts.asset.AssetKind;

import p4_aas.AssetShells.IShell;
import p4_aas.AssetShells.ModbusAsset;
import p4_aas.AssetShells.NetworkControlPlane;
import p4_aas.AssetShells.NetworkInfrastructure;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class App {
    public static void main(String[] args) {
        waitForRegistry();

        IShell networkInfrastructure = new NetworkInfrastructure(
            StaticProperties.NetworkInfrastructurePort,
            "Network Infrastructure",
            "org.unibo.aas.networkInfrastructure",
            AssetKind.INSTANCE);

        IShell networkControlPlane = new NetworkControlPlane(
            StaticProperties.NetworkControlPlanePort,
            "Network Control Plane",
            "org.unibo.aas.networkControlPlane",
            AssetKind.INSTANCE);

        IShell modbusClient = new ModbusAsset(
            StaticProperties.ModbusClientPort,
            "ModbusClient",
            "org.unibo.aas.modbusClient",
            AssetKind.INSTANCE,
            "client");

        IShell modbusServer = new ModbusAsset(
            StaticProperties.ModbusServerPort,
            "ModbusServer",
            "org.unibo.aas.modbusServer",
            AssetKind.INSTANCE,
            "server");

        networkInfrastructure.createAndStartServlet();
        networkControlPlane.createAndStartServlet();
        modbusClient.createAndStartServlet();
        modbusServer.createAndStartServlet();
    }

    /**
     * Polling on Registry Url, waiting for a positive response to proceed on.
     */
    private static void waitForRegistry() {
        System.out.print("Waiting for Registry at " + StaticProperties.REGISTRY_POLLING_IP);
        while(!isServerAvailable(StaticProperties.REGISTRY_POLLING_IP)) {
            System.out.print(".");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
        }
        System.out.println();
    }

    private static Boolean isServerAvailable(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
