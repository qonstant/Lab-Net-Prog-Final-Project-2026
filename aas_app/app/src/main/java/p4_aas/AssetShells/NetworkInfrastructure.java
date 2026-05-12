package p4_aas.AssetShells;

import org.eclipse.basyx.aas.metamodel.api.parts.asset.AssetKind;

import p4_aas.Submodels.NetworkInfrastructure.ModbusPacketCountersSubmodel;
import p4_aas.Submodels.NetworkInfrastructure.NetworkInfrastructureSubmodel;

/**
 * Extends AbstractShell to create Network Infrastructure AAS
 * This AAS represents the Infrastructure level in a Network Configuration.
 */
public class NetworkInfrastructure extends AbstractShell {
    public NetworkInfrastructure(Integer PORT, String idShort, String pathId, AssetKind kind) {
        super(PORT);

        this.shell = this.createShell(idShort, pathId, kind);
        this.createSubmodels();
    }

    private void createSubmodels() {
        this.submodels.addAll(new NetworkInfrastructureSubmodel().createSubmodel());
        this.submodels.addAll(new ModbusPacketCountersSubmodel().createSubmodel());
    }
}
