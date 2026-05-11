package p4_aas.Infrastructure;

import p4_aas.StaticProperties;

import org.eclipse.basyx.aas.metamodel.map.AssetAdministrationShell;
import org.eclipse.basyx.aas.metamodel.map.descriptor.AASDescriptor;
import org.eclipse.basyx.aas.metamodel.map.descriptor.SubmodelDescriptor;
import org.eclipse.basyx.aas.registration.proxy.AASRegistryProxy;
import org.eclipse.basyx.submodel.metamodel.map.Submodel;

import java.util.List;

public class UploadAASImpl implements UploadAAS {
    private String REGISTRYPATH;
    private AASRegistryProxy registryProxy;

    public UploadAASImpl(String REGISTRYPATH) {
        this.REGISTRYPATH = REGISTRYPATH;
        this.registryProxy = new AASRegistryProxy(this.REGISTRYPATH);
    }

    @Override
    public void registerAASSMDescriptors(Integer PORT, List<Submodel> submodels, AssetAdministrationShell shell){
        String IP = this.generateAASIP(PORT);
        AASDescriptor aasDescriptor = new AASDescriptor(shell, IP);
        
        for (Submodel sm : submodels) {
            aasDescriptor.addSubmodelDescriptor(new SubmodelDescriptor(sm, IP + "/submodels/" + sm.getIdShort()));
        }
        
        this.registryProxy.register(aasDescriptor);
    }

    private String generateAASIP(Integer port) {
        return String.format("http://%s:%d/aas", StaticProperties.AAS_PUBLIC_HOST, port);
    }
}
