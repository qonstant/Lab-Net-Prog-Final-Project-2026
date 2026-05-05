package p4_aas.Submodels.Modbus;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.basyx.submodel.metamodel.map.Submodel;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.valuetype.ValueType;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.operation.Operation;

import p4_aas.StaticProperties;
import p4_aas.Submodels.AbstractSubmodel;

public class ModbusDeviceSubmodel extends AbstractSubmodel {
    private final String role;
    private final ModbusDeviceLambda lambdaProvider;

    public ModbusDeviceSubmodel(String role) {
        super();
        this.role = role;
        this.lambdaProvider = new ModbusDeviceLambda();
    }

    @Override
    public List<Submodel> createSubmodel() {
        Submodel endpoint = new Submodel();
        endpoint.setIdShort("ModbusEndpoint");
        endpoint.addSubmodelElement(new Property("Role", role));
        endpoint.addSubmodelElement(new Property("Transport", "Modbus TCP"));
        endpoint.addSubmodelElement(new Property("Port", StaticProperties.MODBUS_PORT));
        endpoint.addSubmodelElement(new Property("SupportedFunctionCodes", "1, 2, 3, 4, 5, 6"));

        if ("client".equals(role)) {
            endpoint.addSubmodelElement(new Property("IPAddress", StaticProperties.MODBUS_CLIENT_IP));
            endpoint.addSubmodelElement(new Property("ServerIPAddress", StaticProperties.MODBUS_SERVER_IP));
            endpoint.addSubmodelElement(testConnection());
            endpoint.addSubmodelElement(readCoils());
            endpoint.addSubmodelElement(readDiscreteInputs());
            endpoint.addSubmodelElement(readHoldingRegisters());
            endpoint.addSubmodelElement(readRegister());
            endpoint.addSubmodelElement(readInputRegisters());
            endpoint.addSubmodelElement(writeCoil());
            endpoint.addSubmodelElement(writeRegister());
        } else {
            endpoint.addSubmodelElement(new Property("IPAddress", StaticProperties.MODBUS_SERVER_IP));
            endpoint.addSubmodelElement(new Property("TLSPort", StaticProperties.MODBUS_TLS_PORT));
            endpoint.addSubmodelElement(getStatus());
        }

        return List.of(endpoint);
    }

    private Operation testConnection() {
        Operation testConnection = new Operation("TestConnection");
        testConnection.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        testConnection.setWrappedInvokable(lambdaProvider.testConnection());
        return testConnection;
    }

    private Operation readCoils() {
        Operation readCoils = new Operation("ReadCoils");
        readCoils.setInputVariables(getUtils().getCustomInputVariables(addressCountInputs()));
        readCoils.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        readCoils.setWrappedInvokable(lambdaProvider.readCoils());
        return readCoils;
    }

    private Operation readDiscreteInputs() {
        Operation readDiscreteInputs = new Operation("ReadDiscreteInputs");
        readDiscreteInputs.setInputVariables(getUtils().getCustomInputVariables(addressCountInputs()));
        readDiscreteInputs.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        readDiscreteInputs.setWrappedInvokable(lambdaProvider.readDiscreteInputs());
        return readDiscreteInputs;
    }

    private Operation readHoldingRegisters() {
        Operation readHoldingRegisters = new Operation("ReadHoldingRegisters");
        readHoldingRegisters.setInputVariables(getUtils().getCustomInputVariables(addressCountInputs()));
        readHoldingRegisters.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        readHoldingRegisters.setWrappedInvokable(lambdaProvider.readHoldingRegisters());
        return readHoldingRegisters;
    }

    private Operation readRegister() {
        Operation readRegister = new Operation("ReadRegister");
        readRegister.setInputVariables(getUtils().getCustomInputVariables(Map.of("Register", ValueType.Integer)));
        readRegister.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        readRegister.setWrappedInvokable(lambdaProvider.readRegister());
        return readRegister;
    }

    private Operation readInputRegisters() {
        Operation readInputRegisters = new Operation("ReadInputRegisters");
        readInputRegisters.setInputVariables(getUtils().getCustomInputVariables(addressCountInputs()));
        readInputRegisters.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        readInputRegisters.setWrappedInvokable(lambdaProvider.readInputRegisters());
        return readInputRegisters;
    }

    private Operation writeCoil() {
        Operation writeCoil = new Operation("WriteCoil");
        Map<String, ValueType> inputVariables = new LinkedHashMap<>();
        inputVariables.put("Address", ValueType.Integer);
        inputVariables.put("Value", ValueType.Boolean);
        writeCoil.setInputVariables(getUtils().getCustomInputVariables(inputVariables));
        writeCoil.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        writeCoil.setWrappedInvokable(lambdaProvider.writeCoil());
        return writeCoil;
    }

    private Operation writeRegister() {
        Operation writeRegister = new Operation("WriteRegister");
        Map<String, ValueType> inputVariables = new LinkedHashMap<>();
        inputVariables.put("Register", ValueType.Integer);
        inputVariables.put("Value", ValueType.Integer);
        writeRegister.setInputVariables(getUtils().getCustomInputVariables(inputVariables));
        writeRegister.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        writeRegister.setWrappedInvokable(lambdaProvider.writeRegister());
        return writeRegister;
    }

    private Operation getStatus() {
        Operation getStatus = new Operation("GetStatus");
        getStatus.setOutputVariables(getUtils().getOperationVariables(1, "Output"));
        getStatus.setWrappedInvokable(lambdaProvider.getStatus());
        return getStatus;
    }

    private Map<String, ValueType> addressCountInputs() {
        Map<String, ValueType> inputVariables = new LinkedHashMap<>();
        inputVariables.put("Address", ValueType.Integer);
        inputVariables.put("Count", ValueType.Integer);
        return inputVariables;
    }
}
