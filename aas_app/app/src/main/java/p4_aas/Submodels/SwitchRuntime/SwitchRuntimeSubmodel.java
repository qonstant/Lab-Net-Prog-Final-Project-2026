package p4_aas.Submodels.SwitchRuntime;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.basyx.submodel.metamodel.map.Submodel;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.valuetype.ValueType;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.operation.Operation;

import p4_aas.Submodels.AbstractSubmodel;

public class SwitchRuntimeSubmodel extends AbstractSubmodel {
    private final SwitchRuntimeLambda lambdaProvider;

    public SwitchRuntimeSubmodel() {
        super();
        this.lambdaProvider = new SwitchRuntimeLambda();
    }

    @Override
    public List<Submodel> createSubmodel() {
        Submodel switchRuntime = new Submodel();
        switchRuntime.setIdShort("SwitchRuntime");

        switchRuntime.addSubmodelElement(showTables());
        switchRuntime.addSubmodelElement(dumpTable());
        switchRuntime.addSubmodelElement(readRegister());

        return List.of(switchRuntime);
    }

    private Operation showTables() {
        Operation showTables = new Operation("ShowTables");
        showTables.setInputVariables(getUtils().getCustomInputVariables(Map.of("Switch", ValueType.Integer)));
        showTables.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        showTables.setWrappedInvokable(lambdaProvider.showTables());
        return showTables;
    }

    private Operation dumpTable() {
        Operation dumpTable = new Operation("DumpTable");
        Map<String, ValueType> inputVariables = new LinkedHashMap<>();
        inputVariables.put("Switch", ValueType.Integer);
        inputVariables.put("Table", ValueType.String);
        dumpTable.setInputVariables(getUtils().getCustomInputVariables(inputVariables));
        dumpTable.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        dumpTable.setWrappedInvokable(lambdaProvider.dumpTable());
        return dumpTable;
    }

    private Operation readRegister() {
        Operation readRegister = new Operation("ReadRegister");
        Map<String, ValueType> inputVariables = new LinkedHashMap<>();
        inputVariables.put("Switch", ValueType.Integer);
        inputVariables.put("Register", ValueType.String);
        readRegister.setInputVariables(getUtils().getCustomInputVariables(inputVariables));
        readRegister.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        readRegister.setWrappedInvokable(lambdaProvider.readRegister());
        return readRegister;
    }
}
