# Networking

## Cables and the component network

Computers, screens, keyboards, disk drives, adapters and redstone I/O blocks connect through **network cables** or
by touching. Every computer on a cable network sees the *network* components (screens, keyboards, drives, adapter
blocks, redstone blocks) in `component.list()`. The parts *inside* other computers stay private.

Connection rules:

- Cables carry everything.
- Screens, keyboards, disk drives, adapters and redstone I/O blocks show their components and pass the connection on.
- Computer cases and racks are endpoints: they join a network but don't connect two networks through themselves.
- **Relays** pass messages between the networks on their sides but not components, so you can share messages
  without sharing screens.

Changes are picked up within a tick or two (`component_added` / `component_removed`).

## Messages

A **network card** sends over cables (through relays); a **wireless card** sends through the air:

| Card | Range |
|---|---|
| Wireless tier 1 | up to `network.wirelessRange` blocks (400 by default), same dimension; `setStrength` lowers it |
| Wireless tier 2 | unlimited, any dimension (receivers in other dimensions need a tier 2 card too) |

```lua
local modem = component.modem          -- or component.proxy(address)
modem.open(5)                          -- ports 1-65535; up to 128 open
modem.send(targetAddress, 5, "hello", 1, true)
modem.broadcast(5, "anyone?")
local _, receiver, sender, port, distance, a, b, c = event.pull("modem_message")
```

A message has up to 8 values (nil, booleans, numbers, strings), at most 8192 bytes in total. Messages are delivered
at the end of the tick. Wired messages report distance 0.

### The net library and host names

```lua
local net = require("net")
net.open(port); net.send(address, port, ...); net.broadcast(port, ...)
local from, port, distance, ... = net.receive([timeout], [port])
net.discover([hostname], [timeout])  -- {address, hostname, distance} for hosts running the beacon service
net.resolve("printer")               -- address by host name
```

Set a computer's name with `hostname <name>`. Programs: `ping`, `msg send <host> <text>`, `msg listen`.

## Wireless peripherals (the Linker)

Like CC: Wireless Peripherals, but built in: a computer can use blocks that are **not** on its cable network.

1. Right-click a computer case or server rack with the **Linker** to select it.
2. Right-click blocks to link them (again to unlink). Lines show the links while you hold the Linker.
   Sneak-right-click the air to clear the selection.
3. Put a **wireless card** in the computer (each server in a rack needs its own).

Linked blocks show up as components exactly as if they were cabled:

- Computah blocks: screens, disk drives (with their floppy), adapters, redstone I/O blocks, keyboards.
- Anything else with an item, fluid or energy capability (chests, furnaces, tanks, batteries from other mods):
  `inventory`, `tank` and `energy` components.

Range: tier 1 wireless card – `network.linkRange` blocks (64 by default, 0 = unlimited), same dimension. Tier 2 –
unlimited, any loaded dimension. At most `network.maxLinks` links per computer or rack. Links are stored per
server world and survive restarts. Unloaded or removed blocks disappear from `component.list()` and come back when
loaded or replaced.

## Adapters

An **adapter** block on the network exposes the blocks next to it in the same way (`inventory`, `tank`, `energy`),
without linking.

```lua
local chest = component.proxy(component.list("inventory")())
chest.getInventorySize()
chest.getStackInSlot(1)      -- {name, label, count, maxCount, damage, maxDamage}
chest.pushItems(otherInventoryAddress, 1, 64)
```
