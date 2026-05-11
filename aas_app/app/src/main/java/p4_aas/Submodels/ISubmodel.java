package p4_aas.Submodels;

import java.util.List;

import org.eclipse.basyx.submodel.metamodel.map.Submodel;

public interface ISubmodel {
    /**
     * @return list of submodels for a specific AAS.
     */
    List<Submodel> createSubmodel();
}