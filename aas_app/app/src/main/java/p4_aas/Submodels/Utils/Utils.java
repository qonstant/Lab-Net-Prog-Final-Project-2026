package p4_aas.Submodels.Utils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.basyx.submodel.metamodel.api.qualifier.haskind.ModelingKind;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.valuetype.ValueType;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.operation.OperationVariable;

public class Utils {
    public static final int GET_FIREWALL_RULES = 15;

    /**
     * @param values Map of String and ValueType (String, Integer, Boolean)
     * @return list of OperationVariables for WebUI with ID and Type given from Map
     */
    public List<OperationVariable> getCustomInputVariables(Map<String, ValueType> values) {
        List<OperationVariable> rList = new LinkedList<>();

        values.forEach((k, v) -> {
            Property prop = new Property(k, v);
            prop.setKind(ModelingKind.TEMPLATE);
            rList.add(new OperationVariable(prop));
        });
        return rList;
    }

    public List<OperationVariable> getCustomOutputVariables(Map<String, ValueType> values) {
        return getCustomInputVariables(values);
    }

    /**
     * @param idShort
     * @param n how many Operation variables
     * @return N simple operation variables with ValueType String and standard IdShort
     */
    public List<OperationVariable> getOperationVariables(int n, String idShort) {
        return Collections.nCopies(n, new Property(idShort, ValueType.String)).
            stream().
            peek(el -> el.setKind(ModelingKind.TEMPLATE)).
            map(el -> new OperationVariable(el)).
            collect(Collectors.toList());
    }
}
