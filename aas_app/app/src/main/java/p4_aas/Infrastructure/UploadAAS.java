package p4_aas.Infrastructure;

import org.eclipse.basyx.aas.metamodel.map.AssetAdministrationShell;
import org.eclipse.basyx.submodel.metamodel.map.Submodel;

import java.util.List;

public interface UploadAAS {

    /**
     * Registers AAS Submodels descriptors in the respective AAS Registry proxy
     * @param PORT on which instantiate the AAS in localhost
     * @param submodels
     * @param shell
     */
    void registerAASSMDescriptors(Integer PORT, List<Submodel> submodels, AssetAdministrationShell shell);
}