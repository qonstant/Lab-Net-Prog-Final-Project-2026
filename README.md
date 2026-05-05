# Laboratory of Network Programmability and Automation: Final Project

This repository contains a Kathara-based Modbus TCP lab with two P4/BMv2 switches and an Eclipse BaSyx AAS layer. The main lab runs a Modbus client and server through a P4 network path, exposes AAS shells through BaSyx, and provides scripts for manual testing and automated benchmarks.

## Repository Layout

```text
lab.conf
```

Main Kathara topology. It defines the nodes, links, Docker images, bridged ports, and exposed services.

```text
s1.startup
s2.startup
```

Kathara startup scripts for the two P4 switches. They configure interfaces, start SSH, compile the P4 program, build the extern library, start `simple_switch`, and load `commands.txt`.

```text
s1/
s2/
```

Switch-specific files:

- `industrial_aes.p4`: P4 dataplane program.
- `commands.txt`: BMv2 CLI commands loaded at switch startup.
- `change_key.sh`: helper to update key registers at runtime.
- `retrieve_info.sh`: helper to read switch timing registers.
- `extern_lib/`: C++ extern implementation and build files.
- `sshd_config`: SSH configuration copied into the switch container.

```text
modbusclient/
modbusserver/
```

Modbus endpoint code and SSH configuration. The client contains helpers for direct terminal tests and AAS-triggered operations, including Modbus function codes 1 through 6.

```text
aas_app/
```

Java source code for the AAS application. This is the Gradle project used to generate `P4_AAS.jar`.

```text
aas_project/
```

Runtime directory for the AAS container. The generated `P4_AAS.jar` is copied here by the Gradle build.

```text
registry.startup
server.startup
webui.startup
aas_project.startup
```

Startup scripts for the BaSyx registry, BaSyx server, Web UI, and AAS application.

```text
auto_test.sh
```

Automated benchmark runner. It starts/stops Kathara, rewrites switch command files for selected modes, runs Modbus tests, retrieves switch metrics, and stores results.

```text
docker/
```

Dockerfiles to build the images used by the lab.

```text
shared/
```

Shared runtime directory used by containers for logs, startup markers, and benchmark output.

```text
lab2_App_source/
lab2_P4_AAS/
```

Legacy/standalone material from a different Lab 2 setup. These directories are not referenced by the main `lab.conf`, startup scripts, or `auto_test.sh`. They are not required to run this Modbus lab, but they may be useful as reference material.

## Main Topology

The main packet path is:

```text
modbusclient -- s1 -- s2 -- modbusserver
```

The AAS container is connected to the management/data networks so it can expose AAS shells and reach the relevant lab nodes.

Important IP addresses:

```text
modbusclient: 195.11.14.5
modbusserver: 200.1.1.7
s1 management: 100.0.1.11
s2 management: 100.0.1.12
aas_project: 100.0.2.4, 100.0.1.5, 195.11.14.100, 200.1.1.100
```

Host ports:

```text
BaSyx Web UI:        http://localhost:3001
BaSyx Registry:      http://localhost:8082/registry
AAS NetworkInfra:    http://localhost:6001/aas
AAS ControlPlane:    http://localhost:6002/aas
AAS ModbusClient:    http://localhost:6003/aas
AAS ModbusServer:    http://localhost:6004/aas
```

## Requirements

You need:

- Kathara
- Docker
- Java/Gradle only if you rebuild the AAS application
- The Docker images referenced in `lab.conf`

The Gradle wrapper is already included in `aas_app/`, so you normally run `./gradlew` instead of installing Gradle globally.

## Start The Lab

Run commands from the repository root:

```bash
cd /home/lorenzo/Downloads/lab1_P4_modbus
```

Clean old lab state:

```bash
kathara lclean
kathara wipe -f
```

Start the lab:

```bash
kathara lstart --noterminals
```

Check running nodes:

```bash
kathara linfo
```

Check the startup marker:

```bash
cat shared/startup.temp
```

You should see at least:

```text
modbusclient modbusserver s1 s2
```

Stop the lab:

```bash
kathara lclean
```

## Basic Modbus Tests

Use these commands to check the Modbus path without involving the AAS Web UI.

Connection test:

```bash
kathara exec modbusclient "python modbus_ops.py test --host 200.1.1.7 --port 502"
```

The AAS helper accepts operation names plus `fc1`...`fc6` aliases:

```bash
kathara exec modbusclient "python modbus_ops.py fc1 --address 0 --count 8 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc2 --address 0 --count 8 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc3 --address 0 --count 1 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc4 --address 0 --count 1 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc5 --address 0 --value true --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py fc6 --address 0 --value 45 --host 200.1.1.7 --port 502"
```

The old single-register aliases are still available:

```bash
kathara exec modbusclient "python modbus_ops.py read --register 0 --host 200.1.1.7 --port 502"
kathara exec modbusclient "python modbus_ops.py write --register 0 --value 45 --host 200.1.1.7 --port 502"
```

`modbus_client.py` and `tls_client.py` also expose `--fc {1..6}` for direct manual runs. Example:

```bash
kathara exec modbusclient "python modbus_client.py --fc 1 --address 0 --count 8"
```

If these commands fail, debug the network/P4/Modbus path before debugging the AAS.

## Switch CLI

Each switch runs BMv2 `simple_switch`. You can inspect tables through `simple_switch_CLI`.

Open a shell on `s1`:

```bash
kathara exec s1 bash
```

Run a CLI command:

```bash
simple_switch_CLI
```

Useful commands inside the CLI:

```text
show_tables
table_dump ipv4_lpm
table_dump modbus_sec
register_read keys
```

Run a one-shot command without opening an interactive shell:

```bash
kathara exec s1 "echo 'show_tables' | simple_switch_CLI"
```

Switch startup commands are stored in:

```text
s1/commands.txt
s2/commands.txt
```

Important: `auto_test.sh` rewrites these files when it runs benchmarks.

## AAS Web UI

Start the lab, then open:

```text
http://localhost:3001
```

The registry should show these shells:

- `Network Infrastructure`
- `Network Control Plane`
- `ModbusClient`
- `ModbusServer`

Useful operations:

```text
Network Control Plane -> SwitchRuntime -> ShowTables
Input:
Switch = 1 or 2
```

```text
Network Control Plane -> SwitchRuntime -> DumpTable
Input:
Switch = 1 or 2
Table = modbus_sec
```

```text
Network Control Plane -> SwitchRuntime -> ReadRegister
Input:
Switch = 1 or 2
Register = keys
```

```text
ModbusClient -> ModbusEndpoint -> TestConnection
```

```text
ModbusClient -> ModbusEndpoint -> ReadCoils
Input:
Address = 0
Count = 8
```

```text
ModbusClient -> ModbusEndpoint -> ReadDiscreteInputs
Input:
Address = 0
Count = 8
```

```text
ModbusClient -> ModbusEndpoint -> ReadHoldingRegisters
Input:
Address = 0
Count = 1
```

```text
ModbusClient -> ModbusEndpoint -> ReadRegister
Input:
Register = 0
```

```text
ModbusClient -> ModbusEndpoint -> ReadInputRegisters
Input:
Address = 0
Count = 1
```

```text
ModbusClient -> ModbusEndpoint -> WriteCoil
Input:
Address = 0
Value = true
```

```text
ModbusClient -> ModbusEndpoint -> WriteRegister
Input:
Register = 0
Value = 45
```

## Rebuild The AAS JAR

Rebuild the AAS JAR whenever you modify:

- Java files in `aas_app/app/src/main/java/`
- resources in `aas_app/app/src/main/resources/`
- `aas_app/app/build.gradle.kts`
- Gradle dependencies or build configuration

You do not need to rebuild the JAR when you only modify:

- P4 files
- switch `commands.txt` files
- Kathara startup files
- `lab.conf`

Build command:

```bash
cd /home/lorenzo/Downloads/lab1_P4_modbus/aas_app
./gradlew :app:shadowJar
```

Expected result:

```text
BUILD SUCCESSFUL
```

The generated JAR is copied automatically to:

```text
/home/lorenzo/Downloads/lab1_P4_modbus/aas_project/P4_AAS.jar
```

If the wrapper is not executable:

```bash
chmod +x gradlew
./gradlew :app:shadowJar
```

If the lab is already running, restart it after rebuilding:

```bash
cd /home/lorenzo/Downloads/lab1_P4_modbus
kathara lclean
kathara lstart --noterminals
```

The first Gradle build may download dependencies. Later builds should use the local cache.

## Editing The P4 Switches

The P4 programs are:

```text
s1/industrial_aes.p4
s2/industrial_aes.p4
```

At switch startup, `s1.startup` and `s2.startup` run:

```text
p4c-bm2-ss --emit-externs -o industrial_aes.json industrial_aes.p4
cd extern_lib && make
simple_switch ...
simple_switch_CLI <<< $(cat commands.txt)
```

So, after changing a P4 file or extern file, restart the lab:

```bash
kathara lclean
kathara lstart --noterminals
```

To change only table entries, edit:

```text
s1/commands.txt
s2/commands.txt
```

Then restart the lab, or apply equivalent commands manually with `simple_switch_CLI`.

## Automated Benchmarks

Run one configuration:

```bash
./auto_test.sh --128 --rtt --noterminals
```

Run all configurations:

```bash
./auto_test.sh --all --noterminals
```

Available flags:

```text
--all
--no-encryption
--tls
--128
--160
--192
--224
--256
--rtt
--ppt
--deq
--noterminals
```

If no measurement option is selected, the script enables all measurements.

Benchmark output is written under `shared/` during execution and then moved to `results/` when the script finishes.

## Logs And Runtime Files

Useful files:

```text
shared/startup.temp
```

Startup marker written by containers.

```text
shared/s1.log
```

BMv2 log for `s1`.

```text
shared/results_*
```

Temporary benchmark output.

Generated files can appear during runtime, for example P4 JSON outputs, compiled extern objects, Gradle build outputs, and Python `__pycache__` directories.

## Can The Lab 2 Directories Be Removed?

For the main Modbus lab, `lab2_App_source/` and `lab2_P4_AAS/` are not required. The main topology does not reference them.

Keep them if you want examples from the older Lab 2 AAS/P4 setup. Remove them if you only care about this repository's main Modbus lab and want a cleaner tree.

Before deleting them, make sure you do not need them as reference material for reports or future exercises.

## Troubleshooting Checklist

1. Check that Docker is running.
2. Clean old Kathara state with `kathara lclean`.
3. Start with `kathara lstart --noterminals`.
4. Check nodes with `kathara linfo`.
5. Check `shared/startup.temp`.
6. Test Modbus from `modbusclient` with `modbus_ops.py`.
7. Inspect switch tables with `simple_switch_CLI`.
8. If Java/AAS code changed, rebuild with `./gradlew :app:shadowJar`.
9. Restart Kathara after rebuilding the JAR.

If Modbus terminal tests fail, debug the dataplane, routes, ARP entries, and switch tables first.

If Modbus terminal tests pass but the Web UI fails, debug the AAS container, JAR build, BaSyx registry, or SSH connectivity from `aas_project` to the target nodes.
