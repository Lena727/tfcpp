# Draught: what sets the heat of a hearth

Code: `tfcpp.metallurgy.Draught` (the math, no Minecraft types), `DraughtFuel` (the table), `DraughtState` (one device), `Blowing` (the people with pipes), `mixin/DraughtMixins`. Tests: `DraughtTest`, `SandBathTest`, `GuideDataTest`, `DraughtGameTests`.

## Physics

The adiabatic flame of every carbon fuel is about 2000 C. What fixes the temperature of a real hearth is its air supply, not its fuel. Without forced draught a charcoal hearth reaches about 1000 C, and that only in fresh air, which is why copper (1080 C) was always smelted with forced air: first blowpipes (Egyptian tomb paintings show rows of men blowing through reeds with clay tips; experiments reach about 1200 C), then bellows. The fuel matters at a low supply, through its reactivity: porous charcoal burns readily on natural draught, bituminous coal has to distil its volatiles first, coke is unreactive and hardly keeps alight without a blast, raw lignite carries 30 to 60% water. Low oxygen in the room slows burning. Bellows need lumber, and so a metal saw, which comes after copper: the first forced draught is the ceramic blowpipe, which is clay knapping and a firing at 850 C.

Wind across an open hearth feeds it only a little, since it cools the surface about as much as it feeds it: only a furnace built to catch the wind (Sri Lanka) gains much. Wind therefore does not add to forced air.

## Formula

A hearth loses its heat mostly by radiation, which goes as T^4, and the heat released is proportional to the air burned. So T^4, not T, takes the air:

    T^4 = Tnat^4 + (Tmax^4 - Tnat^4) * min(1, air)
    T   = (T^4 * s)^(1/4)            (kelvin throughout, floored at 20 C)

`air` is the air beyond the natural draught, as a share of full bellows: `air = min(1, max(bellows, pipes, wind))`. The sources do not add to each other; only blowers add among themselves.

| source | air |
|---|---|
| bellows | `min(1, airTicks / 125)`, with TFC's own `airTicks`; 125 is where TFC's `4 * airTicks` reaches the 500 C cap of `FurnaceHeatMixins$Blast` |
| blowers | 0.22 for each distinct player who blew into the hearth within the last 10 game ticks, at most 4 counted: 0.22, 0.44, 0.66, 0.88. People add: rows of men blowing into one hearth |
| wind | only where `level.canSeeSky(pos.above())`: `v = 32 m/s * |wind vector|` (TFC: length 1 is 32 m/s; TFC cuts wind below y 57, so none deep below the surface), `clamp(v / 200, 0, 0.05)`, the full 0.05 from 10 m/s |

The oxygen factor `s = clamp((x - 0.12) / (0.2095 - 0.12), 0, 1.5)`, with `x` the raw oxygen mole fraction of the gas cell that the fire draws from: the same lookup as `FireSource` (`GasEmission.outletHeight`, `leakHeight` as the second choice). Flame temperature depends on that fraction, not on total pressure, so there is no barometric term. Below 12% oxygen `FireSource` puts the fire out, as before. Fresh air gives `s = 1`: **natural charcoal is exactly 1000 C only in fresh air**, and any stuffiness takes it below.

## Numbers (C)

| fuel (item) | natural | 1 blower | 2 blowers | 3 blowers | 4 blowers | max wind | full bellows |
|---|---|---|---|---|---|---|---|
| charcoal (`minecraft:charcoal`) | 1000 | 1204 | 1347 | 1460 | 1554 | 1055 | 1600 |
| lignite char (`tfcpp:lignite_char`) | 950 | 1180 | 1334 | 1453 | 1552 | 1014 | 1600 |
| coal (`tfc:ore/bituminous_coal`, `minecraft:coal`) | 950 | 1228 | 1405 | 1538 | 1648 | 1030 | 1700 |
| coke (`tfcpp:coke`) | 850 | 1217 | 1422 | 1572 | 1693 | 965 | 1750 |
| lignite (`tfc:ore/lignite`) | 750 | 1046 | 1219 | 1347 | 1451 | 840 | 1500 |

One blower reaches nothing over 1250 C (borosilicate) or 1300 C (fire clay); two or three blowers can, or a bellows. Maximum wind on charcoal stays below 1080 C (copper). Oxygen at 16.5% takes charcoal on natural draught to 799 C (the factor is 0.503).

`Tmax` is not in the table: it is always `Draught.fullBlastTemperature(burnTemperature)`, the fuel temperature that `MetallurgyData` and `ChemistryData` write, plus `min(500, T / 2)`, which is exactly what each fuel reached on a full bellows before. So everything tuned on bellows (fire clay 1300, steel 1520, black steel in a crucible) is unchanged.

The table of `Tnat` is a Java table (`DraughtFuel`), not a NeoForge data map: the temperatures TFC has for each fuel are themselves written from Java by the data generators, `Tmax` is derived from them, and the two belong together in code. `GuideDataTest` compares the burn temperatures of the table with the generated data. When the fuel record is missing or stale, the lowest `Tnat` among the fuels of the table that burn at that TFC temperature is taken (1100: lignite char, 950 rather than charcoal 1000). Only if no fuel of the table burns at that temperature (wood in a firepit, a modded fuel) does the fuel burn at its TFC temperature with `Tmax = T + min(500, T / 2)`: as before, plus wind, oxygen and the pipe.

## Where it applies

- `CharcoalForgeBlockEntity.serverTick` and `AbstractFirepitBlockEntity.serverTick` call `HeatCapability.adjustDeviceTemp`. `DraughtMixins` wraps the call (`@WrapOperation`), takes the target from the model, applies TFC's rain through `targetDeviceTemp(target, 0, isRaining)` and moves the temperature as TFC does (1 up and 1 down, or 2 up and 0.5 down with forced air; blowers count as forced air). With no fuel burning, TFC's own call runs, and the hearth cools as before.
- The device only knows `burnTemperature`, not the item. `consumeFuel` (at `Fuel.get`) records the item and the temperature TFC gave it, in a `@Unique` field saved to NBT (`tfcppFuel`, `tfcppFuelTemperature`), and `copyFrom` carries it when a firepit becomes a pot, a grill or a stove. It is used only while `burnTemperature` still equals the recorded temperature. After a catch-up over a long time (`consumeFuelForTicks` burns on into the next pieces without any hook), a different temperature means the record is stale, and the lowest fuel of that temperature applies. A catch-up between two fuels of the same temperature (charcoal to lignite char) is not noticed.
- Fuel burn: TFC takes 1 burn tick a tick, or 2 with any bellows air or rain. A `@WrapOperation` at the `PUTFIELD` of `burnTicks` in both `serverTick` methods takes `1 + air` instead, with the fraction carried to the next tick, so a full bellows still burns twice as fast and one blower 1.22 times. Rain keeps TFC's doubling (2, not compounded). `FireSource` uses the same `1 + air` for the gas of a forge or a firepit. The client-side prediction of `burnTicks` in a firepit's `clientTick` is TFC's own and is not changed: it is only for display.
- TPS: wind and oxygen are looked up once in 20 game ticks per device and cached (the game time sets only how often a cache is refreshed, not a process). Every tick costs one `pow` pair and a few multiplications for a lit device; the gas lookup scans only a few blocks once a second.

## Blowing

Holding use with an empty blowpipe (`tfc:ceramic_blowpipe`, `tfc:blowpipe`; not the `*_with_glass` ones) while looking at a hearth gives it 0.22 air for that player for the next 10 ticks, refreshed on every tick of use (`LivingEntityUseItemEvent.Tick`, server side, a pick at block interaction range). It costs 0.02 food exhaustion a tick, like sprinting, and a small flame puff appears every 7 ticks. Different players are counted separately, up to four.

What counts as a hearth: `Blowing.hearthAt` takes the looked-at block and walks down through anything that is heated from below, which is a crucible or any block entity with TFC's `IHeatConsumer` (our zinc retort, coke oven, glass retort, sand bath, cupel hearth, converter, pressure vessel), up to six blocks, until a charcoal forge or a firepit is reached. So a retort on a sand bath on a firepit is blown on through both.

A right click on a block comes before the use of the item, and the forge, the firepit and the crucible open their screens on it. A mixin on those three blocks answers `SKIP_DEFAULT_BLOCK_INTERACTION` for an empty blowpipe in hand (for a crucible only if a hearth is below it by the same walk), and the click falls through to the item, whose use duration of 40 ticks restarts while the key is held. Our own vessels already pass a held item that is not theirs on (`useWithoutItem` returns `PASS` for a non-empty hand, and `canUse` refuses a pipe), so they need no mixin. A person with a pipe in hand therefore cannot open the screen of the forge: they put the pipe away, or sneak (sneaking skips block use anyway).

Known limits, true to the code:
- A pot, a grill or a stove (a firepit with something on it) overrides `useItemOn`, so a right click on it with a pipe opens its screen. They are hearths all the same: if the use of the pipe starts in the air, or on the forge or a plain firepit, and the person then turns to look at one, it is blown on.
- The off hand: the use event is read from whichever hand holds the pipe, but a right click is offered to the main hand first, so a pipe in the off hand with an empty main hand opens the screen of the forge first. It works if the use starts in the air.
- Glassworking is untouched: the pipe with glass is another item, and `BorosilicateGlassMixin` only changes a temperature check.

## Boundaries and accepted simplifications

- The blast furnace, the firebox, the bloomery and the pit kiln are not touched. The blast furnace has blast physics of its own, tuned in `FurnaceHeatMixins` and `IronSmeltingMixins`, and still burns at the temperature TFC has for its fuel.
- Wind is read at the hearth's position, with the sky seen from the block above it. A crucible on the forge does not block it.
