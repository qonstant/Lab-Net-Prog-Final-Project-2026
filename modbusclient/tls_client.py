from pymodbus.client import AsyncModbusTlsClient
import asyncio
import time
import argparse
import random
import sys

from modbus_ops import call_modbus, execute_modbus_operation, operation_from_function_code

parser = argparse.ArgumentParser(description="ModBus sample client")
parser.add_argument(
    "--fc",
    help="Run a single Modbus/TLS operation by function code: 1 read coils, 2 read discrete inputs, 3 read holding registers, 4 read input registers, 5 write coil, 6 write register",
    type=int,
    choices=range(1, 7),
    metavar="{1..6}",
)
parser.add_argument(
    "--address",
    "--register",
    dest="address",
    help="Modbus address/register for --fc operations",
    type=int,
    default=0,
)
parser.add_argument(
    "--count",
    help="Number of values to read for --fc read operations",
    type=int,
    default=1,
)
parser.add_argument(
    "--value",
    help="Value for --fc write operations. FC5 accepts true/false or 1/0; FC6 accepts an integer.",
    default="0",
)
parser.add_argument(
    "--test-rtt-write",
    help="Test the Round Trip Time (write) for 100000 times",
    action="store_true",
    required=False
)
parser.add_argument(
    "--test-rtt-read",
    help="Test the Round Trip Time (read) for 100000 times",
    action="store_true",
    required=False
)
parser.add_argument(
    "--test-read",
    help="Test read from a register for 100000 times",
    action="store_true",
    required=False
)
parser.add_argument(
    "--test-write",
    help="Test write from a register for 100000 times",
    action="store_true",
    required=False
)


args = parser.parse_args()

if args.count < 1:
    parser.error("--count must be greater than zero")


async def run_function_code():
    client = AsyncModbusTlsClient("200.1.1.7", 5020, certfile="./cert.crt", keyfile="./key.key")
    await client.connect()
    operation = operation_from_function_code(args.fc)
    exit_code, message = await execute_modbus_operation(
        client,
        operation,
        address=args.address,
        count=args.count,
        value=args.value,
    )
    print(message)
    client.close()
    return exit_code


async def test_rtt_write():
    client = AsyncModbusTlsClient("200.1.1.7", 5020, certfile="./cert.crt", keyfile="./key.key")
    await client.connect()
    register_id = 0
    new_value = 45

    ## WRITE
    with open(f"/shared/results_10*10000_tls_write.txt", "w") as results_file:
        for j in range(10):
            for i in range(10000):
                new_value = random.randint(0, 100)
                time_start = time.time()
                await call_modbus(client, "write_register", register_id, new_value)
                time_end = time.time()
                results_file.write("%s\n" % (time_end - time_start))
                print(f"RTT - Value written into the register {register_id}: {new_value} - {i} - Test for tls")
    client.close()

async def test_rtt_read():
    client = AsyncModbusTlsClient("200.1.1.7", 5020, certfile="./cert.crt", keyfile="./key.key")
    await client.connect()
    register_id = 0
    new_value = 45
    

    ## READ
    with open(f"/shared/results_10*10000_tls_read.txt", "w") as results_file:
        for j in range(10):
            for i in range(10000):
                time_start = time.time()
                response = await call_modbus(client, "read_holding_registers", register_id, 1)
                time_end = time.time()
                results_file.write("%s\n" % (time_end - time_start))
                print(f"RTT - Value read from the register {register_id}: {response.registers[0]} - {i} - Test for tls")
    client.close()

async def test_read():
    client = AsyncModbusTlsClient("200.1.1.7", 5020, certfile="./cert.crt", keyfile="./key.key")
    await client.connect()
    register_id = 0
    new_value = 45
    

    for j in range(10):
        for i in range(10000):
            response = await call_modbus(client, "read_holding_registers", register_id, 1)
            print(f"PPT-DEQ - Value read from the register {register_id}: {response.registers[0]} - {i} - Test for tls")
    client.close()

async def test_write():
    client = AsyncModbusTlsClient("200.1.1.7", 5020, certfile="./cert.crt", keyfile="./key.key")
    await client.connect()
    register_id = 0
    new_value = 45
    
    
    for j in range(10):
        for i in range(10000):
            new_value = random.randint(0, 100)
            await call_modbus(client, "write_register", register_id, new_value)
            print(f"PPT-DEQ - Value written into the register {register_id}: {new_value} - {i} - Test for tls")
    client.close()
    
# async def main():
#     client = AsyncModbusTlsClient("200.1.1.7", 5020, certfile="./cert.crt", keyfile="./key.key")
#     await client.connect()
#     register_id = 0
#     new_value = 45
#     with open("/shared/results_tls_read.txt", "w") as results_file:
#         for j in range(10):
#             for i in range(10000):
#                 #new_value = random.randint(0, 100)
#                 time_start = time.time()
#                 #await client.write_register(register_id, new_value, unit=0x01) 
#                 await client.read_holding_registers(register_id, 1, unit=0x01) #response = await client.read_holding_registers(register_id, 1, unit=0x01)
#                 time_end = time.time()
#                 results_file.write("%s\n" % (time_end - time_start))
#                 print(f"Valore letto dal registro  - {i}")
#                 #print(f"Valore scritto nel registro {register_id}: {new_value} - {i}")
#     client.close()

# asyncio.run(main())


###### TEST OPEN CONNECTION ######
# async def main():
#     client = AsyncModbusTlsClient("200.1.1.7", 5020, certfile="./cert.crt", keyfile="./key.key")
#     with open("/shared/results_conn_tls.txt", "w") as results_file:
#         for i in range(10000):
#             time_start = time.time()
#             await client.connect()
#             time_end = time.time()
#             results_file.write("%s\n" % (time_end - time_start))
#             client.close()
#             print(f"Test connection number {i}")
# asyncio.run(main())


if args.fc is not None:
    sys.exit(asyncio.run(run_function_code()))
elif args.test_rtt_write:
    asyncio.run(test_rtt_write())
elif args.test_rtt_read:
    asyncio.run(test_rtt_read())
elif args.test_read:
    asyncio.run(test_read())
elif args.test_write:
    asyncio.run(test_write())
