package p4_aas.AssetShells;

import org.eclipse.basyx.aas.metamodel.api.parts.asset.AssetKind;

import p4_aas.Submodels.SwitchRuntime.SwitchRuntimeSubmodel;

/**
 * Extends AbstractShell for creating a Network Control Plane AAS
 * This AAS represetns the Control Plane in a Network Configuration,
 * allowing to exchange informations with Controllers and Firewalls
 */
public class NetworkControlPlane extends AbstractShell {

    public NetworkControlPlane(Integer PORT, String idShort, String pathId, AssetKind kind) {
        super(PORT);

        this.shell = this.createShell(idShort, pathId, kind);
        this.createSubmodels();
    }

    private void createSubmodels() {
        this.submodels.addAll(new SwitchRuntimeSubmodel().createSubmodel());
    }
}
