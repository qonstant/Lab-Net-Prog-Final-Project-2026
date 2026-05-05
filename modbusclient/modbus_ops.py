from pymodbus.client import AsyncModbusTcpClient
import argparse
import asyncio
import sys


UNIT_ID = 0x01

FUNCTION_CODE_BY_OPERATION = {
    "read-coils": 1,
    "read-discrete-inputs": 2,
    "read-holding-registers": 3,
    "read-input-registers": 4,
    "write-coil": 5,
    "write-register": 6,
}

OPERATION_ALIASES = {
    "test": "test",
    "1": "read-coils",
    "fc1": "read-coils",
    "read-coils": "read-coils",
    "2": "read-discrete-inputs",
    "fc2": "read-discrete-inputs",
    "read-discrete-inputs": "read-discrete-inputs",
    "3": "read-holding-registers",
    "fc3": "read-holding-registers",
    "read": "read-holding-registers",
    "read-register": "read-holding-registers",
    "read-holding-registers": "read-holding-registers",
    "4": "read-input-registers",
    "fc4": "read-input-registers",
    "read-input-registers": "read-input-registers",
    "5": "write-coil",
    "fc5": "write-coil",
    "write-coil": "write-coil",
    "6": "write-register",
    "fc6": "write-register",
    "write": "write-register",
    "write-register": "write-register",
    "write-single-register": "write-register",
}


def normalize_operation(operation):
    return OPERATION_ALIASES[operation]


def operation_from_function_code(function_code):
    for operation, code in FUNCTION_CODE_BY_OPERATION.items():
        if code == function_code:
            return operation
    raise ValueError(f"Unsupported function code {function_code}")


def parse_bool(value):
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized in ("1", "true", "t", "yes", "y", "on"):
        return True
    if normalized in ("0", "false", "f", "no", "n", "off"):
        return False
    raise ValueError(f"Invalid boolean value {value!r}")


async def call_modbus(client, method_name, *args):
    method = getattr(client, method_name)
    last_error = None
    for unit_argument in ("unit", "slave"):
        try:
            return await method(*args, **{unit_argument: UNIT_ID})
        except TypeError as exc:
            last_error = exc

    try:
        return await method(*args)
    except TypeError:
        if last_error is not None:
            raise last_error
        raise


def response_is_error(response):
    return response is None or (hasattr(response, "isError") and response.isError())


def response_error(operation, response):
    function_code = FUNCTION_CODE_BY_OPERATION[operation]
    return f"ERROR fc={function_code} {operation}: {response}"


async def execute_modbus_operation(client, operation, address=0, count=1, value=0):
    if operation == "read-coils":
        response = await call_modbus(client, "read_coils", address, count)
        if response_is_error(response):
            return 3, response_error(operation, response)
        bits = [bool(bit) for bit in response.bits[:count]]
        return 0, f"OK fc=1 read-coils address={address} count={count} values={bits}"

    if operation == "read-discrete-inputs":
        response = await call_modbus(client, "read_discrete_inputs", address, count)
        if response_is_error(response):
            return 3, response_error(operation, response)
        bits = [bool(bit) for bit in response.bits[:count]]
        return 0, f"OK fc=2 read-discrete-inputs address={address} count={count} values={bits}"

    if operation == "read-holding-registers":
        response = await call_modbus(client, "read_holding_registers", address, count)
        if response_is_error(response):
            return 3, response_error(operation, response)
        registers = response.registers[:count]
        return 0, f"OK fc=3 read-holding-registers address={address} count={count} values={registers}"

    if operation == "read-input-registers":
        response = await call_modbus(client, "read_input_registers", address, count)
        if response_is_error(response):
            return 3, response_error(operation, response)
        registers = response.registers[:count]
        return 0, f"OK fc=4 read-input-registers address={address} count={count} values={registers}"

    if operation == "write-coil":
        coil_value = parse_bool(value)
        response = await call_modbus(client, "write_coil", address, coil_value)
        if response_is_error(response):
            return 4, response_error(operation, response)
        return 0, f"OK fc=5 write-coil address={address} value={coil_value}"

    if operation == "write-register":
        register_value = int(value)
        response = await call_modbus(client, "write_register", address, register_value)
        if response_is_error(response):
            return 4, response_error(operation, response)
        return 0, f"OK fc=6 write-register address={address} value={register_value}"

    return 1, f"ERROR unsupported operation {operation}"


async def run(args):
    client = AsyncModbusTcpClient(args.host, args.port, retries=0)
    connected = await client.connect()
    if not connected:
        print(f"ERROR unable to connect to {args.host}:{args.port}")
        return 2

    try:
        if args.operation == "test":
            print(f"OK connected to {args.host}:{args.port}")
            return 0

        operation = normalize_operation(args.operation)
        exit_code, message = await execute_modbus_operation(
            client,
            operation,
            address=args.address,
            count=args.count,
            value=args.value,
        )
        print(message)
        return exit_code
    finally:
        client.close()

    print(f"ERROR unsupported operation {args.operation}")
    return 1


def parse_args():
    parser = argparse.ArgumentParser(description="Small Modbus TCP helper for the AAS operations")
    parser.add_argument("operation", choices=sorted(OPERATION_ALIASES.keys()))
    parser.add_argument("--host", default="200.1.1.7")
    parser.add_argument("--port", type=int, default=502)
    parser.add_argument("--address", "--register", dest="address", type=int, default=0)
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--value", default="0")
    args = parser.parse_args()
    if args.count < 1:
        parser.error("--count must be greater than zero")
    return args


if __name__ == "__main__":
    sys.exit(asyncio.run(run(parse_args())))
