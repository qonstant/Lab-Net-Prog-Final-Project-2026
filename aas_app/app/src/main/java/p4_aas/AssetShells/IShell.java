package p4_aas.AssetShells;

import java.util.List;

import org.eclipse.basyx.aas.metamodel.api.parts.asset.AssetKind;
import org.eclipse.basyx.aas.metamodel.map.AssetAdministrationShell;
import org.eclipse.basyx.submodel.metamodel.map.Submodel;

import p4_aas.Infrastructure.ModelProvider;

/**
 * Interface for creating Asset Administration Shells
 */
public interface IShell {

    /**
     * @return void
     * Creates and start http server for each AAS
     */
    void createAndStartServlet();

    /**
     * @return interger Port to create HTTP server on
     */
    int getPORT();

    /**
     * @return created AAS
     */
    AssetAdministrationShell getShell();

    /**
     * @return ModelProvider for this specific AAS
     */
    ModelProvider getModelProvider();

    /**
     * @return list of submodels for this specific AAS
     */
    List<Submodel> getSubmodels();

    /**
     * Creates Descriptors (needed for ModelProvider) for each Submodel
     */
    void createDescriptors();

    /**
     * Add a submodel to the list
     */
    void addSubmodels();

    /**
     * @param idShort the simple id for AAS
     * @param pathId 
     * @param kind Instance or Type
     * @return a new AAS created with those parameters
     */
    AssetAdministrationShell createShell(String idShort, String pathId, AssetKind kind);

    /**
     * Creates model provider for this specifif AAS
     */
    void createModelProvider();

}