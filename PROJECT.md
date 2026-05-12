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

The final solution has two main parts:

1. dataplane monitoring in the P4 switches
2. management-plane exposure in the AAS application

## Step 1: Inspect the Existing Environment

The repository already contains the core pieces needed for the challenge:

- `s1/industrial_aes.p4` and `s2/industrial_aes.p4`: the BMv2/P4 programs used by the two switches.
- `s1.startup` and `s2.startup`: startup scripts that compile the P4 program and launch `simple_switch`.
- `aas_app/`: the Java BaSyx application.
- `p4_aas/Submodels/SwitchRuntime/*`: existing code that can already read BMv2 tables and registers through `simple_switch_CLI`.

This existing switch-runtime bridge means the challenge can be solved without building a separate telemetry service.

### 1.1 Topology Understanding

The active traffic path is:

```text
modbusclient -> s1 -> s2 -> modbusserver
modbusserver -> s2 -> s1 -> modbusclient
```

This matters because every successful Modbus transaction normally produces:

- one request packet
- one response packet

Therefore, if packet counting is done on a single switch, one successful Modbus operation should increase the corresponding function-code counter by `2`.

### 1.2 Existing Components Reused

The implementation intentionally reused existing project components instead of introducing new services:

- the P4 parser already recognizes Modbus TCP traffic on port `502`
- the switch startup scripts already compile and launch the switch program
- the Java AAS app already exposes submodels
- `SwitchCliClient` already executes `simple_switch_CLI` commands on `s1` and `s2` through SSH

This reduced the solution to a focused extension of the current design.

## Step 2: Counting Strategy

Each Modbus packet crosses both `s1` and `s2`, so counting and summing both switches would double the totals.

Design decision:

- implement the same counter register on both switches, so the P4 program stays consistent across the topology
- use `s1` as the default observation point in the AAS submodel

This keeps the deployment symmetric while still providing a correct end-to-end total when the operator reads switch 1.

### 2.1 Why `s1` Is the Default Observation Point

Both switches see the same end-to-end Modbus exchange:

- `s1` sees traffic entering and leaving the client-side segment
- `s2` sees traffic entering and leaving the server-side segment

If the AAS summed `s1 + s2`, then every request and response would be counted twice from the perspective of the whole path. Using `s1` as the default observation point prevents this inflation while still allowing `s2` to be queried for troubleshooting.

## Step 3: P4 Counter Design

The P4 program already parses Modbus TCP traffic by checking TCP port `502`.

Implementation approach:

- store the Modbus function code in parser metadata before the payload is extracted
- add a six-slot BMv2 register called `modbus_function_code_packet_counts`
- increment slot `0..5` when the parsed function code is `1..6`
- also map Modbus exception responses `129..134` back to `1..6`, so exception replies are still counted as packets related to those function codes

The counter is incremented in ingress only when the packet is recognized as Modbus TCP.

### 3.1 Meaning of Function Codes 1 Through 6

The challenge specifically targets the standard Modbus function codes already supported by the client helper:

- `1`: Read Coils
- `2`: Read Discrete Inputs
- `3`: Read Holding Registers
- `4`: Read Input Registers
- `5`: Write Single Coil
- `6`: Write Single Register

These functions are also the ones exposed through `modbusclient/modbus_ops.py`.

### 3.2 Detailed P4 Changes

The P4 implementation was added in both:

- `s1/industrial_aes.p4`
- `s2/industrial_aes.p4`

The following logic was introduced:

#### Metadata fields

New metadata fields were added:

- `has_modbus_function_code`
- `modbus_function_code`

Purpose:

- `has_modbus_function_code` signals that the parser successfully saw a Modbus PDU byte
- `modbus_function_code` stores the first Modbus PDU byte, which is the function code

Why metadata was used:

- the existing program already extracts the Modbus payload as a generic varbit payload
- storing the first byte in metadata avoids rewriting the payload layout and reduces the risk of breaking the encryption path

#### Parser update

Inside `parse_payload_modbus`, the first byte of the Modbus payload is captured using:

- `packet.lookahead<bit<8>>()`

This allows reading the function code before extracting the whole payload.

Why `lookahead` was chosen:

- it reads the next byte without consuming it
- the existing payload extraction stays unchanged
- the encryption/decryption code can continue to use the same payload structure

#### Register addition

A six-element BMv2 register was added:

- `register<bit<32>>(6) modbus_function_code_packet_counts;`

Register layout:

- index `0` -> FC1
- index `1` -> FC2
- index `2` -> FC3
- index `3` -> FC4
- index `4` -> FC5
- index `5` -> FC6

#### Counting action

The new ingress action is:

- `count_modbus_function_code_packet()`

Its logic is:

1. verify that a Modbus function code was captured
2. if the code is `1..6`, map it directly to register index `code - 1`
3. if the code is `129..134`, map it to exception response buckets `code - 129`
4. read the current register value
5. write back `current + 1`

Why exception responses are mapped:

- Modbus exception replies use the original function code with bit 7 set
- for example, FC3 exception reply becomes `131`
- counting them in the same bucket preserves the interpretation "packets related to FC3"

#### Apply block integration

The counting action runs only when:

- `hdr.tcp.isValid()`
- `hdr.modbus_tcp.isValid()`

This keeps non-Modbus traffic out of the counters.

### 3.3 Function-Level Explanation of the Added P4 Logic

#### `count_modbus_function_code_packet()`

Purpose:

- increment the correct BMv2 register slot for FC1..FC6 traffic

Inputs used:

- `meta.has_modbus_function_code`
- `meta.modbus_function_code`

Outputs:

- updates `modbus_function_code_packet_counts`

Edge cases handled:

- packets without a captured function code are ignored
- packets outside FC1..FC6 and exception codes `129..134` are ignored

### 3.4 Why the Existing Crypto Logic Was Left Untouched

The project already contains encryption-related actions:

- `cipher()`
- `decipher()`

These depend on the current payload extraction model. The new monitoring logic was designed to avoid interfering with:

- payload length calculations
- payload extraction
- encryption/decryption extern calls
- checksum recomputation

This was a deliberate safety choice.

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

### 4.1 New Java Classes and Their Roles

Two new classes were added under:

- `aas_app/app/src/main/java/p4_aas/Submodels/NetworkInfrastructure/`

They are:

- `ModbusPacketCountersSubmodel`
- `ModbusPacketCountersLambda`

### 4.2 Detailed AAS Integration

#### `NetworkInfrastructure.java`

This existing shell class was extended so the Network Infrastructure AAS now registers two submodels:

- `NetworkTopology`
- `ModbusPacketCounters`

Before the change, only the static topology submodel was exposed.

#### `ModbusPacketCountersSubmodel`

Purpose:

- define the AAS-facing structure of the new counter submodel

Main method:

- `createSubmodel()`

What it creates:

- a new submodel with `idShort = "ModbusPacketCounters"`
- informational properties
- an operation named `ReadCounters`

Static properties added:

- `CountedFunctionCodes`
- `DefaultObservationSwitch`
- `CountingNotes`

Reason for these properties:

- they make the submodel self-describing in the Web UI
- they explain why switch `1` is the default observation point

#### `readCounters()` in `ModbusPacketCountersSubmodel`

Purpose:

- define the AAS operation contract

Input variables:

- `Switch` as `Integer`

Output variables:

- `ObservationSwitch`
- `FC1Count`
- `FC2Count`
- `FC3Count`
- `FC4Count`
- `FC5Count`
- `FC6Count`

Why an operation was used instead of only properties:

- these values are runtime state, not static topology data
- operations fit the existing application style for live switch interaction
- the Web UI can trigger them on demand

#### `ModbusPacketCountersLambda`

Purpose:

- implement the runtime behavior behind the `ReadCounters` operation

It reuses:

- `SwitchCliClient`

This means the new submodel reads the BMv2 register through the same management path already used elsewhere in the project.

### 4.3 Function-Level Explanation of the Added Java Methods

#### `ModbusPacketCountersLambda.readCounters()`

Purpose:

- execute `register_read modbus_function_code_packet_counts` on the selected switch
- parse the BMv2 CLI output
- return the result as AAS output properties

Detailed behavior:

1. read the `Switch` input argument
2. run `register_read modbus_function_code_packet_counts`
3. parse the returned comma-separated values
4. build seven output properties

Returned fields:

- selected switch identifier
- one field for each function-code counter

#### `ModbusPacketCountersLambda.parseCounts(String cliOutput)`

Purpose:

- extract the numeric register contents from `simple_switch_CLI` textual output

Why parsing is needed:

- BMv2 does not return raw JSON here
- `simple_switch_CLI` prints a text line that contains the register values

Parsing approach:

- use a regex to find the final `= value1, value2, ...` segment
- split the values on comma
- return the first six entries

Error handling:

- if parsing fails, all outputs become `parse-error`

This avoids hiding failures behind misleading zeros.

#### `ModbusPacketCountersLambda.getInt(...)`

Purpose:

- safely convert BaSyx operation input values to Java `int`

Reason:

- BaSyx may provide numeric inputs as `BigInteger`, generic `Number`, or string-like values

#### `Utils.getCustomOutputVariables(...)`

Purpose:

- create output variable definitions using the same pattern already used for custom input variables

Why it was added:

- the new operation returns multiple named outputs
- reusing the input-variable helper keeps the code consistent and small

### 4.4 Existing Methods Reused Without Modification

The following existing methods are central to the new behavior even though they were not rewritten:

#### `SwitchCliClient.runCliCommand(int switchId, String cliCommand)`

Purpose:

- SSH into the selected switch
- pipe the provided command into `simple_switch_CLI`

This is the core bridge between the AAS layer and the BMv2 runtime.

## Step 5: Source Changes

The following files were updated or added:

- `s1/industrial_aes.p4`
- `s2/industrial_aes.p4`
- `aas_app/app/src/main/java/p4_aas/AssetShells/NetworkInfrastructure.java`
- `aas_app/app/src/main/java/p4_aas/Submodels/Utils/Utils.java`
- `aas_app/app/src/main/java/p4_aas/Submodels/NetworkInfrastructure/ModbusPacketCountersLambda.java`
- `aas_app/app/src/main/java/p4_aas/Submodels/NetworkInfrastructure/ModbusPacketCountersSubmodel.java`

### 5.1 Exact Functional Changes by File

#### `s1/industrial_aes.p4`

Added:

- Modbus function-code metadata
- parser lookahead logic
- six-slot counter register
- counting action
- ingress call to the counting action

#### `s2/industrial_aes.p4`

Added the same logic as `s1/industrial_aes.p4`.

Reason:

- both switches compile their own local copy of the switch program
- keeping them identical avoids behavioral drift

#### `aas_app/.../AssetShells/NetworkInfrastructure.java`

Changed:

- the shell now includes the new `ModbusPacketCounters` submodel

#### `aas_app/.../Submodels/Utils/Utils.java`

Changed:

- added `getCustomOutputVariables(...)`

#### `aas_app/.../Submodels/NetworkInfrastructure/ModbusPacketCountersSubmodel.java`

Added:

- the structural definition of the new AAS submodel
- the `ReadCounters` operation contract

#### `aas_app/.../Submodels/NetworkInfrastructure/ModbusPacketCountersLambda.java`

Added:

- the runtime implementation of the AAS operation
- CLI output parsing logic

#### `aas_project/P4_AAS.jar`

Changed as a build artifact:

- rebuilt after the Java source changes

#### `docs/screenshots/aas-ui-home.png`

Added:

- first screenshot artifact captured from the local AAS Web UI

## Step 6: Build and Verification Plan

Verification targets:

1. rebuild the Java AAS artifact with Gradle
2. check that the new submodel is registered in the Network Infrastructure AAS
3. generate Modbus traffic using the existing `modbus_ops.py` helper for FC1 through FC6
4. read the BMv2 register and verify that the correct slots increased
5. invoke the AAS `ReadCounters` operation and confirm the same values are exposed

### 6.1 Expected Runtime Behavior

After one successful execution of each Modbus operation from FC1 to FC6:

- the selected switch counter register should show `2` for every bucket
- the AAS `ReadCounters` operation should return the same six values

This expectation is based on counting:

- one request packet
- one response packet

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

### 7.1 What Was Verified in This Session

Verified directly:

- Java code compiles and packages
- modified P4 source compiles in the lab image
- AAS Web UI is reachable on `http://localhost:3001`
- a first headless screenshot was captured successfully

Not yet fully verified in this session:

- live BMv2 register increments after traffic generation
- AAS `ReadCounters` invocation against live switch state
- Wireshark evidence for the function-code packets

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

Why this matters:

- it proves the new Java classes integrate with the existing BaSyx application
- it confirms there are no Java compilation or packaging errors in the added submodel code

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

Why this matters:

- the added P4 syntax is accepted by the same compiler family used by the lab image
- the key dataplane change is therefore validated before runtime testing

### 8.4 AAS UI Reachability Check

Command used:

```bash
curl --max-time 5 http://localhost:3001
```

Result:

- the AAS UI main HTML page was returned successfully

Why this matters:

- it confirms the Web UI service is up locally
- it provides a target for screenshot-based evidence collection

### 8.5 Screenshot Capture Check

Command used:

```bash
chromium --headless --disable-gpu --no-sandbox \
  --screenshot=/tmp/aas-ui.png \
  --window-size=1440,1200 \
  http://localhost:3001
```

Result:

- screenshot capture succeeded
- the image was stored in the repository as `docs/screenshots/aas-ui-home.png`

Why this matters:

- it proves screenshot collection is technically possible in the current environment
- it provides the first report artifact

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

### 9.1 Additional Evidence Recommended for the Final Submission

The final report should ideally contain screenshots of:

1. the running topology or service state
2. FC1..FC6 traffic generation from the Modbus client
3. `register_read modbus_function_code_packet_counts` on `s1`
4. the AAS `ModbusPacketCounters` submodel
5. the `ReadCounters` operation output
6. optional Wireshark evidence for Modbus TCP packets

These screenshots make the report stronger because they connect:

- dataplane behavior
- management-plane visibility
- user-facing proof

### 9.2 Screenshot Artifact Captured So Far

Current screenshot artifact:

- [aas-ui-home.png](/home/kali/Lab-Net-Prog-Final-Project-2026/docs/screenshots/aas-ui-home.png)

Suggested final screenshot directory layout:

- `docs/screenshots/aas-ui-home.png`
- `docs/screenshots/modbus-fc-tests.png`
- `docs/screenshots/s1-register-counters.png`
- `docs/screenshots/aas-readcounters-result.png`
- `docs/screenshots/wireshark-modbus-fc.png`

## Step 10: Final Outcome

The challenge implementation is complete at the source level:

- the P4 dataplane now counts packets related to Modbus function codes `1..6`
- exception responses `129..134` are mapped back to the originating function code buckets
- the Network Infrastructure AAS now includes a new `ModbusPacketCounters` submodel
- the new `ReadCounters` operation exposes the live register values through the existing switch management path

The remaining work is only the runtime validation inside a full Kathara session.

## Step 11: Complete Session Log

This section records the concrete work performed during this implementation session.

### 11.1 Repository Inspection

Performed:

- enumerated repository files
- identified the actual P4 switch sources
- identified the Java AAS application
- identified the existing switch CLI integration classes

Main goal:

- find the minimum-change implementation path

### 11.2 P4 Implementation

Performed:

- modified `s1/industrial_aes.p4`
- mirrored the same logic into `s2/industrial_aes.p4`

Main goal:

- count packets related to Modbus function codes `1..6`

### 11.3 AAS Implementation

Performed:

- created `ModbusPacketCountersSubmodel.java`
- created `ModbusPacketCountersLambda.java`
- updated `NetworkInfrastructure.java`
- extended `Utils.java`

Main goal:

- expose live counter values through a new Network Infrastructure submodel

### 11.4 Report Creation

Performed:

- created this `PROJECT.md`
- documented design choices
- documented verification commands
- documented expected runtime outputs

Main goal:

- produce a submission-ready engineering report instead of only code changes

### 11.5 Build Verification

Performed:

- rebuilt the AAS jar with Gradle
- inspected the jar contents
- compiled the P4 source in the lab Docker image

Main goal:

- ensure the source changes are not only theoretically correct but also buildable

### 11.6 Screenshot Preparation

Performed:

- confirmed the local AAS UI is reachable
- tested headless Chromium screenshot capture
- saved the first screenshot artifact into the repository

Main goal:

- establish a repeatable path to gather visual evidence for the final report

## Step 12: Summary of Added or Changed Functions

This section focuses specifically on functions and methods introduced or altered by this work.

### P4 Logic

#### Added metadata fields

- `has_modbus_function_code`
- `modbus_function_code`

#### Added P4 action

- `count_modbus_function_code_packet()`

### Java Logic

#### Added methods

- `ModbusPacketCountersSubmodel.createSubmodel()`
- `ModbusPacketCountersSubmodel.readCounters()`
- `ModbusPacketCountersLambda.readCounters()`
- `ModbusPacketCountersLambda.parseCounts(String cliOutput)`
- `ModbusPacketCountersLambda.getInt(...)`
- `Utils.getCustomOutputVariables(...)`

#### Modified existing method behavior through integration

- `NetworkInfrastructure.createSubmodels()`

It now adds the new runtime counter submodel in addition to the original topology submodel.
