package p4_aas.Submodels.NetworkInfrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.basyx.submodel.metamodel.map.Submodel;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.valuetype.ValueType;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.operation.Operation;

import p4_aas.Submodels.AbstractSubmodel;

public class ModbusPacketCountersSubmodel extends AbstractSubmodel {
    private final ModbusPacketCountersLambda lambdaProvider;

    public ModbusPacketCountersSubmodel() {
        super();
        this.lambdaProvider = new ModbusPacketCountersLambda();
    }

    @Override
    public List<Submodel> createSubmodel() {
        Submodel counters = new Submodel();Doc
        counters.setIdShort("ModbusPacketCounters");

        counters.addSubmodelElement(new Property("CountedFunctionCodes", "1, 2, 3, 4, 5, 6"));
        counters.addSubmodelElement(new Property("DefaultObservationSwitch", "1"));
        counters.addSubmodelElement(new Property("CountingNotes", "Counts packets observed on the selected switch since switch startup. Use switch 1 for end-to-end totals without double counting."));
        counters.addSubmodelElement(readCounters());

        return List.of(counters);
    }

    private Operation readCounters() {
        Operation readCounters = new Operation("ReadCounters");
        Map<String, ValueType> inputVariables = new LinkedHashMap<>();
        inputVariables.put("Switch", ValueType.Integer);
        readCounters.setInputVariables(getUtils().getCustomInputVariables(inputVariables));

        Map<String, ValueType> outputVariables = new LinkedHashMap<>();
        outputVariables.put("ObservationSwitch", ValueType.String);
        outputVariables.put("FC1Count", ValueType.String);
        outputVariables.put("FC2Count", ValueType.String);
        outputVariables.put("FC3Count", ValueType.String);
        outputVariables.put("FC4Count", ValueType.String);
        outputVariables.put("FC5Count", ValueType.String);
        outputVariables.put("FC6Count", ValueType.String);
        readCounters.setOutputVariables(getUtils().getCustomOutputVariables(outputVariables));
        readCounters.setWrappedInvokable(lambdaProvider.readCounters());
        return readCounters;
    }
}
