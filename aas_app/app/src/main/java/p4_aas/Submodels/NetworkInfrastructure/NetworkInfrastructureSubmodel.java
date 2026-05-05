// Copyright 2023 riccardo.bacca@studio.unibo.it
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//     http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package p4_aas.Submodels.NetworkInfrastructure;

import org.eclipse.basyx.submodel.metamodel.map.Submodel;
import org.eclipse.basyx.submodel.metamodel.map.submodelelement.dataelement.property.Property;

import p4_aas.StaticProperties;
import p4_aas.Submodels.AbstractSubmodel;

import java.util.List;

public class NetworkInfrastructureSubmodel extends AbstractSubmodel {

    public NetworkInfrastructureSubmodel() {
        super();
    }

    @Override
    public List<Submodel> createSubmodel() {
        Submodel topology = new Submodel();
        topology.setIdShort("NetworkTopology");

        topology.addSubmodelElement(new Property("Switches", "s1, s2"));
        topology.addSubmodelElement(new Property("Links", "modbusclient-s1, s1-s2, s2-modbusserver"));
        topology.addSubmodelElement(new Property("ManagementNetwork", "100.0.1.0/24"));
        topology.addSubmodelElement(new Property("SwitchManagementIPs", "s1=" + StaticProperties.SW1_MANAGEMENT_IP + ", s2=" + StaticProperties.SW2_MANAGEMENT_IP));
        topology.addSubmodelElement(new Property("ModbusClient", StaticProperties.MODBUS_CLIENT_IP));
        topology.addSubmodelElement(new Property("ModbusServer", StaticProperties.MODBUS_SERVER_IP));
        topology.addSubmodelElement(new Property("ModbusPort", StaticProperties.MODBUS_PORT));
        topology.addSubmodelElement(new Property("AASNetworks", "L=100.0.2.4, D=100.0.1.5, A=195.11.14.100, C=200.1.1.100"));

		return List.of(topology);
    }
}
