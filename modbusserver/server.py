import asyncio

from pymodbus.datastore import ModbusSequentialDataBlock, ModbusServerContext
try:
    from pymodbus.datastore import ModbusSlaveContext
except ImportError:
    from pymodbus.datastore import ModbusDeviceContext as ModbusSlaveContext
from pymodbus.server import StartAsyncTcpServer


DATASTORE_SIZE = 100


def create_slave_context():
    coils = [False] * DATASTORE_SIZE
    coils[0] = True
    discrete_inputs = [(index % 2) == 0 for index in range(DATASTORE_SIZE)]
    holding_registers = [0] * DATASTORE_SIZE
    holding_registers[0] = 43
    input_registers = [1000 + index for index in range(DATASTORE_SIZE)]

    datastore = {
        "di": ModbusSequentialDataBlock(0, discrete_inputs),
        "co": ModbusSequentialDataBlock(0, coils),
        "hr": ModbusSequentialDataBlock(0, holding_registers),
        "ir": ModbusSequentialDataBlock(0, input_registers),
    }

    try:
        return ModbusSlaveContext(**datastore, zero_mode=True)
    except TypeError:
        return ModbusSlaveContext(**datastore)


def create_server_context():
    slave_context = create_slave_context()
    try:
        return ModbusServerContext(slaves=slave_context, single=True)
    except TypeError:
        return ModbusServerContext(devices=slave_context, single=True)


async def run_async_server():
    context = create_server_context()
    address = ("200.1.1.7", 502)
    print('starting Modbus TCP server with FC1-FC6 datastore...')
    await StartAsyncTcpServer(context=context, address=address)


async def main():
    await run_async_server()


if __name__ == "__main__":
    asyncio.run(main(), debug=True)
