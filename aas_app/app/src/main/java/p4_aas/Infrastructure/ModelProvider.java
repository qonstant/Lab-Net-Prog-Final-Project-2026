package p4_aas.Infrastructure;

import java.util.List;

import javax.servlet.http.HttpServlet;
import org.eclipse.basyx.aas.metamodel.map.AssetAdministrationShell;
import org.eclipse.basyx.aas.restapi.AASModelProvider;
import org.eclipse.basyx.aas.restapi.MultiSubmodelProvider;
import org.eclipse.basyx.components.configuration.BaSyxContextConfiguration;
import org.eclipse.basyx.submodel.metamodel.map.Submodel;
import org.eclipse.basyx.submodel.restapi.SubmodelProvider;
import org.eclipse.basyx.vab.modelprovider.api.IModelProvider;
import org.eclipse.basyx.vab.protocol.http.server.BaSyxContext;
import org.eclipse.basyx.vab.protocol.http.server.BaSyxHTTPServer;
import org.eclipse.basyx.vab.protocol.http.server.VABHTTPInterface;

public class ModelProvider {
    private MultiSubmodelProvider modelProvider;
    private BaSyxContext context;
    private HttpServlet modelServlet;

    public ModelProvider(int servletPort, String ACCESS_CONTROL_ALLOW_ORIGIN, String CONTEXT_PATH, AssetAdministrationShell shell, List<Submodel> submodels) {
        this.modelProvider = new MultiSubmodelProvider();
        this.modelProvider.setAssetAdministrationShell(new AASModelProvider(shell));

        for(Submodel s : submodels) {
             this.modelProvider.addSubmodel(new SubmodelProvider(s));
        }

        this.modelServlet = new VABHTTPInterface<IModelProvider>(modelProvider);
		this.context = getContext(servletPort, ACCESS_CONTROL_ALLOW_ORIGIN, CONTEXT_PATH);
        this.context.addServletMapping("/*", modelServlet);
    }

    private BaSyxContext getContext(int servletPort, String accessControlAllowOrigin, String contextPath) {
        BaSyxContextConfiguration config = new BaSyxContextConfiguration();

        config.setPort(servletPort);
        config.setAccessControlAllowOrigin(accessControlAllowOrigin);
        config.setContextPath(contextPath);

        return config.createBaSyxContext();
    }

    public void startLocalHTTPServer(String idShort) {
        BaSyxHTTPServer server = new BaSyxHTTPServer(this.context);
        server.start();
        System.out.println("HTTP server started for " + idShort);
    }
}