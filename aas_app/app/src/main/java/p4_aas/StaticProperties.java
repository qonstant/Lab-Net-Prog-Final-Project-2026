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

package p4_aas;

/**
 * To manage System properties, used to mantain info throughout the app.
 */
public class StaticProperties {
    public static final String REGISTRY_POLLING_IP = "http://100.0.2.1:4000/registry/api/v1/registry";
    public static final String REGISTRYPATH = "http://100.0.2.1:4000/registry/";

    public static final String SW1_MANAGEMENT_IP = "100.0.1.11";
    public static final String SW2_MANAGEMENT_IP = "100.0.1.12";

    public static final String MODBUS_CLIENT_IP = "195.11.14.5";
    public static final String MODBUS_SERVER_IP = "200.1.1.7";
    public static final int MODBUS_PORT = 502;
    public static final int MODBUS_TLS_PORT = 5020;

    public static final int NetworkInfrastructurePort = 6001;
    public static final int NetworkControlPlanePort = 6002;
    public static final int ModbusClientPort = 6003;
    public static final int ModbusServerPort = 6004;
}
