package p4_aas.AssetShells;

import org.eclipse.basyx.aas.metamodel.api.parts.asset.AssetKind;

import p4_aas.Submodels.Modbus.ModbusDeviceSubmodel;

public class ModbusAsset extends AbstractShell {
    private final String role;

    public ModbusAsset(Integer PORT, String idShort, String pathId, AssetKind kind, String role) {
        super(PORT);

        this.role = role;
        this.shell = this.createShell(idShort, pathId, kind);
        this.createSubmodels();
    }

    private void createSubmodels() {
        this.submodels.addAll(new ModbusDeviceSubmodel(this.role).createSubmodel());
    }
}
