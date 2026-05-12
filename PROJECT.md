# Challenge 3 Project Report

## Goal

Implement Modbus function-code monitoring in the P4 data plane and expose the packet counts through a new AAS submodel in the Network Infrastructure shell.

The requested function codes are:

- FC1: Read Coils
- FC2: Read Discrete Inputs
- FC3: Read Holding Registers
- FC4: Read Input Registers
- FC5: Write Single Coil
- FC6: Write Single Register

## Step 1: Inspect the Existing Environment

The repository already contains the core pieces needed for the challenge:

- `s1/industrial_aes.p4` and `s2/industrial_aes.p4`: the BMv2/P4 programs used by the two switches.
- `s1.startup` and `s2.startup`: startup scripts that compile the P4 program and launch `simple_switch`.
- `aas_app/`: the Java BaSyx application.
- `p4_aas/Submodels/SwitchRuntime/*`: existing code that can already read BMv2 tables and registers through `simple_switch_CLI`.

This existing switch-runtime bridge means the challenge can be solved without building a separate telemetry service.

## Step 2: Counting Strategy

Each Modbus packet crosses both `s1` and `s2`, so counting and summing both switches would double the totals.

Design decision:

- implement the same counter register on both switches, so the P4 program stays consistent across the topology
- use `s1` as the default observation point in the AAS submodel

This keeps the deployment symmetric while still providing a correct end-to-end total when the operator reads switch 1.

## Step 3: P4 Counter Design

The P4 program already parses Modbus TCP traffic by checking TCP port `502`.

Implementation approach:

- store the Modbus function code in parser metadata before the payload is extracted
- add a six-slot BMv2 register called `modbus_function_code_packet_counts`
- increment slot `0..5` when the parsed function code is `1..6`
- also map Modbus exception responses `129..134` back to `1..6`, so exception replies are still counted as packets related to those function codes

The counter is incremented in ingress only when the packet is recognized as Modbus TCP.

## Step 4: AAS Submodel Design

The existing `NetworkTopology` submodel is static, so it is not suitable for live counters by itself.

Implementation approach:

- add a new submodel named `ModbusPacketCounters`
- place it in the Network Infrastructure shell
- expose a `ReadCounters` operation
- reuse the existing SSH + `simple_switch_CLI` access path already used by the switch-runtime submodel

The operation accepts a switch identifier and returns:

- `ObservationSwitch`
- `FC1Count`
- `FC2Count`
- `FC3Count`
- `FC4Count`
- `FC5Count`
- `FC6Count`

## Step 5: Source Changes

The following files were updated or added:

- `s1/industrial_aes.p4`
- `s2/industrial_aes.p4`
- `aas_app/app/src/main/java/p4_aas/AssetShells/NetworkInfrastructure.java`
- `aas_app/app/src/main/java/p4_aas/Submodels/Utils/Utils.java`
- `aas_app/app/src/main/java/p4_aas/Submodels/NetworkInfrastructure/ModbusPacketCountersLambda.java`
- `aas_app/app/src/main/java/p4_aas/Submodels/NetworkInfrastructure/ModbusPacketCountersSubmodel.java`

## Step 6: Build and Verification Plan

Verification targets:

1. rebuild the Java AAS artifact with Gradle
2. check that the new submodel is registered in the Network Infrastructure AAS
3. generate Modbus traffic using the existing `modbus_ops.py` helper for FC1 through FC6
4. read the BMv2 register and verify that the correct slots increased
5. invoke the AAS `ReadCounters` operation and confirm the same values are exposed

## Step 7: Current Verification Status

Local host capabilities found during the implementation:

- Java is available on the host
- `docker` is available on the host
- `p4c-bm2-ss` is not available directly on the host path
- `kathara` is not available on the host path

Because of that, local verification is split:

- Java build can be tested directly on the host
- P4 compilation can be tested inside the already-available lab Docker image
- full traffic generation and AAS Web UI interaction still need the complete Kathara runtime

## Step 8: Executed Verification

### 8.1 Java AAS Build

Command used:

```bash
cd aas_app
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:shadowJar
```

Result:

- build completed successfully
- the updated jar was generated at `aas_project/P4_AAS.jar`

### 8.2 Jar Content Check

Command used:

```bash
unzip -l aas_project/P4_AAS.jar | rg "ModbusPacketCounters|NetworkInfrastructure"
```

Result:

- the jar contains:
  - `p4_aas/Submodels/NetworkInfrastructure/ModbusPacketCountersLambda.class`
  - `p4_aas/Submodels/NetworkInfrastructure/ModbusPacketCountersSubmodel.class`
  - the rebuilt `NetworkInfrastructure.class`

This confirms that the new AAS submodel code is packaged into the runtime artifact.

### 8.3 P4 Compilation Check

Command used:

```bash
docker run --rm --entrypoint sh \
  -v /home/kali/Lab-Net-Prog-Final-Project-2026:/src:ro \
  loriringhio97/p4 \
  -lc 'cd /src/s1 && p4c-bm2-ss --emit-externs -o /tmp/industrial_aes.json industrial_aes.p4'
```

Result:

- the command exited successfully with code `0`
- `s1/industrial_aes.p4` compiles in the lab P4 image
- `s2/industrial_aes.p4` is byte-identical to `s1/industrial_aes.p4`, so the same counter logic is present on both switches

## Step 9: Remaining End-to-End Validation

The following checks are still intended for the full Kathara lab runtime:

1. start the topology
2. generate FC1 through FC6 traffic from the client
3. inspect `register_read modbus_function_code_packet_counts` on `s1`
4. invoke the new AAS `ModbusPacketCounters.ReadCounters` operation with `Switch=1`
5. confirm that the AAS values match the BMv2 register values

Suggested commands:

```bash
kathara lstart --noterminals
kathara exec modbusclient "python modbus_ops.py fc1 --address 0 --count 8 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc2 --address 0 --count 8 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc3 --address 0 --count 1 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc5 --address 0 --value true --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc6 --address 0 --value 45 --host 200.1.1.7 --port 502"
kathara exec s1 "sh -lc 'echo register_read modbus_function_code_packet_counts | simple_switch_CLI'"
```

Expected result after one successful request per function code on switch 1:

- `FC1Count = 2`
- `FC2Count = 2`
- `FC3Count = 2`
- `FC4Count = 2`
- `FC5Count = 2`
- `FC6Count = 2`

Reason:

- one request packet and one response packet are counted for each successful Modbus operation
- both are observed on switch 1

## Step 10: Final Outcome

The challenge implementation is complete at the source level:

- the P4 dataplane now counts packets related to Modbus function codes `1..6`
- exception responses `129..134` are mapped back to the originating function code buckets
- the Network Infrastructure AAS now includes a new `ModbusPacketCounters` submodel
- the new `ReadCounters` operation exposes the live register values through the existing switch management path

The remaining work is only the runtime validation inside a full Kathara session.
