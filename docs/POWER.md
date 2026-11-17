# Power

Power is **off by default**: computers just run. Turn it on in the server config (`power.enabled = true` in
`computah-server.toml`) for packs with an energy mod.

With power on:

- Computer cases and racks accept **Forge Energy (FE)** from any side, from any mod's cables or generators.
- A case stores `power.caseBuffer` FE (20,000) and a running computer uses `power.computerCost` FE per tick (4).
- A rack stores `power.rackBuffer` FE (80,000), shared by its servers; each running server uses
  `power.serverCost` FE per tick (6).
- A computer without energy won't start (`not enough energy`) and stops when it runs out.
- `computer.energy()` and `computer.maxEnergy()` report the buffer.
