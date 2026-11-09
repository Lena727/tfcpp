# TFC++

An addon for [TerraFirmaCraft](https://github.com/TerraFirmaCraft/TerraFirmaCraft) that continues it from medieval metallurgy into coal chemistry, acids, oil, polymers and electricity, under real physics and chemistry.

Built for dedicated servers with many players. Licensed under the [EUPL-1.2](LICENSE.txt).

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.234 or newer |
| TerraFirmaCraft | exactly 4.2.12 |
| Patchouli | 1.21.1-92 or newer; TFC itself requires it, and the TFC++ chapters of the field guide are Patchouli pages |

JEI and EMI are optional: without them the game runs, and with them the devices get information pages.

## What v1.0 contains

Coal chemistry and the first acids: coke ovens and dry distillation, sulfur in fuel, phosphorus in iron and the basic converter, coal dust and secondary explosions, work lead, cupellation and condensation towers, lead chambers and chamber sulfuric acid, borosilicate glass, nitric and hydrochloric acids, the Marsh test and gold-leaf mercury indicators. Underneath are the earlier versions: a chunk-local gas grid with breathing and poisons, methane and the Davy lamp, shaft loads, pipes and pressure vessels, ore reduction and the Bessemer converter, new ores and lead. Everything is explained by a TFC++ category in TFC's in-game field guide, in English and Russian.

## Building

Requires JDK 21 (Gradle downloads it if missing).

```bash
./gradlew build
```

| Task | What it does |
|---|---|
| `test` | Unit tests of the physics, without Minecraft |
| `runGameTestServer` | In-game tests on a headless server with TFC. `-Pload=<n>` also runs the load test (`n` multiplies the number of gas rooms; 1 is the base case), which builds a large base and logs the mod's cost per tick as `TFCPP_LOAD` lines; `/tfcpp perf` shows the same figures on a live server |
| `runData` | Regenerates `src/generated/resources` |
| `runClient` | Starts a development client with JEI. `-PquickPlayWorld=<save folder>` joins that world directly, and `-Psurvey=<chunks>` logs what ores worldgen made in new chunks far from spawn |

`runData` must run in its own Gradle invocation before anything that reads the generated files.

`bash tools/verify.sh` runs `runData`, the unit tests and the game tests and prints a short summary; `--client` adds a smoke run of the development client that joins a world.

## Mixins into TFC

tfcpp supports one exact TFC version and changes TFC freely wherever that gives better physics. All injections are required: if a target disappears in a new TFC version the game fails at startup, and this table is the map for porting them.

| Mixin | Target | Why |
|---|---|---|
| `TFCBlockEntityMixin` | `TFCBlockEntity.onLoad`, `setRemoved`, `onChunkUnloaded` | Tells the gas grid exactly which fires exist, with no scanning of block entities |
| `CharcoalForgeBlockMixin` | `CharcoalForgeBlock.isValid` | Removes the sky requirement: a forge without a chimney works, and fills the room with flue gas |
| `ShaftSourceMixins$WaterWheel`, `$Windmill` | `WaterWheelBlockEntity.clientTick`, `WindmillBlockEntity.clientTick`, `saveAdditional`, `loadAdditional` | Slows the source under load, on both sides, and saves the factor so that it reaches clients |
| `ShaftSourceMixins$Pump` | `PumpBlockEntity.serverTick`, `onRemoved` | Replaces a pump that made water from nothing with `MassPump`, which moves it |
| `ShaftSourceMixins$Bellows` | `BellowsBlockEntity.tickBoth` | A shaft can now stop, and dividing by its speed would disable the bellows for good |
| `ShaftSourceMixins$Aqueduct` | `AqueductBlock.pickupBlock` | A bucket from an aqueduct refilled for free, which made water from nothing |
| `IronSmeltingMixins$Bloomery` | `BloomeryBlockEntity.completeRecipe` | Leaves the slag that holds the iron a bloomery could not reduce, and the CO2 that took the oxygen off the ore |
| `IronSmeltingMixins$BlastFurnace` | `BlastFurnaceBlockEntity.serverTick`, at `HeatingRecipe.assembleFluid` | The same for a blast furnace: calcium silicate slag for each piece of flux, and CO2 from the ore and the limestone |
| `IronBlendMixins$Recipe` | `BlastFurnaceRecipe.matchesInput(ItemStack)`, at return | A furnace recipe of one kind of iron oxide turned away a piece of the other kind. Phosphorus goes into the iron of the whole hearth, so either recipe takes either ore |
| `IronBlendMixins$BlastFurnace` | `BlastFurnaceBlockEntity.insertOrReplaceFluid`, at head; `serverTick`, at `FluidTank.fill` | Two different oxides melting in one tick replaced and threw away one another, and pig iron made into a tank that held the other kind was refused and vanished. Here the two become one bath of phosphoric iron |
| `DrinkableMixin` | `Drinkable.onDrink` | Swallowed mercury joins the same dose in the body as breathed vapour, which a drink's food and potion effects cannot express |
| `FurnaceHeatMixins$PitKiln` | `PitKilnBlockEntity.cookContents` | A pit reaches about 900 C and not 1400 C, metal melting in it stays instead of being deleted, and a recipe with a chance of giving nothing runs once and not every tick |
| `FurnaceHeatMixins$Firebox` | `FireboxBlockEntity.tryApplyRecipe` | Metal melting on a shelf above a firebox stays instead of being deleted |
| `FurnaceHeatMixins$Blast` | `HeatCapability.targetDeviceTemp` | A blast adds at most 500 C and not 600 C, which is what makes melting steel hard |
| `DraughtMixins$Forge`, `$Firepit` | `CharcoalForgeBlockEntity.serverTick` and `AbstractFirepitBlockEntity.serverTick`, at `HeatCapability.adjustDeviceTemp` (`@WrapOperation`) and at the `PUTFIELD` of `burnTicks` (`@WrapOperation`, ordinal 0); `consumeFuel`, at `Fuel.get`; `copyFrom`, `saveAdditional`, `loadAdditional`, at tail | The temperature of a hearth is set by its air and not by its fuel alone: replaces TFC's target with the model of `Draught` (natural draught, blowers, bellows, wind, oxygen of the room), makes the fuel burn at 1 + air instead of TFC's 1 or 2, remembers which fuel was taken, carries that over when a firepit becomes a pot or a grill, and saves it. Implements `Hearth` onto both |
| `DraughtMixins$ForgeBlock`, `$FirepitBlockUse`, `$CrucibleBlockUse` | `CharcoalForgeBlock.useItemOn`, `FirepitBlock.useItemOn`, `CrucibleBlock.useItemOn` (a crucible only with a hearth under it, through any vessel that takes heat from below), at head | A right click with an empty blowpipe opened the screen of the device, so that the pipe could not be used; it now falls through to the use of the item, which blows air into the hearth |
| `HeatingRecipeMixin` | `HeatingRecipe.assembleItem`, `assembleStacked` | The one place where every heating recipe converts an item: releases sulfide fumes at that exact moment |
| `FuelSulfurMixins$Firepit`, `$Firebox`, `$Forge`, `$BlastFurnace` | `consumeFuel` of `AbstractFirepitBlockEntity`, `FireboxBlockEntity`, `CharcoalForgeBlockEntity` and `BlastFurnaceBlockEntity`, each at `Fuel.get` | Sulfur in a piece of coal, lignite or coke goes up the flue as sulfur dioxide at the moment the fire takes the piece to burn it. A blast furnace lets only a tenth of it out, as its lime slag takes the rest |
| `MeltContextMixins` | `serverTick` of crucible, forge, firepit, pit kiln, firebox | Tells `HeatingRecipeMixin` where the conversion is happening, as a vessel melting its contents has no idea where it is |
| `BorosilicateGlassMixin` | `GlassOperation.hasRequiredTemperature`, at return (`@ModifyReturnValue`) | TFC compares every glassworking step with one temperature written into the operation in code, 480 C, so no data can make a glass worked hotter. Borosilicate is worked above 1250 C and, being above the 930 C at which brass melts, only on a ceramic blowpipe |
| `QuernMixin` | `QuernBlockEntity.finishGrinding`, at `ItemStack.shrink` | A quern lets no event out when it finishes a grind. Grinding coal, charcoal or coke raises 2 g of coal dust into the air of the cell the quern stands in, at the moment the input is used up |
| `CropGrowthMixin$Step` | `CropHelpers.growthTickStep`, at head; `@ModifyVariable` on the second store of `growth` and on the store of `healthy` (by local name) | The ground and the air act on a crop: the growth that the step adds (not the expiry) is multiplied by what soil acidity (relative to the soil's own start), metals and sulfur dioxide allow, and aluminium or an acute hour of SO2 makes the step unhealthy, TFC's own way for a crop to die. The field is irrigated from the water that hydrates it, with what that water carries |
| `CropGrowthMixin$Fertilize` | `CropHelpers.useFertilizer`, at `ItemStack.shrink` (`@WrapOperation`) | Wood ash that TFC takes as fertiliser also limes the field, by its neutralising value for each piece taken |
| `CropGrowthMixin$Picked` | `IPickableCrop.getItemInteractionResult`, at `ItemHandlerHelper.giveItemToPlayer` (`@WrapOperation`, both calls) | Fruit picked from a crop that keeps growing takes up the metals of the soil, as the drops of a broken crop do (that one is an event) |
| `FoodMetalsMixins$Provider` | `ItemStackProvider.getStack(ItemStack, Context)`, at return | Every TFC recipe that makes food from one input (heating, quern, barrel, pot) goes through it: the metal of the input goes to the output |
| `FoodMetalsMixins$Shapeless`, `$Shaped` | `AdvancedShapelessRecipe.assemble`, `AdvancedShapedRecipe.assemble`, at return | A crafting recipe of many foods adds up the metal of all its inputs and shares it among its outputs |
| `ForestMixins$Leaves` | `TFCLeavesBlock.randomTick`, at head | A leaf of a stand in poor health falls on a random tick as TFC's own leaves fall |
| `ForestMixins$Sapling` | `TFCSaplingBlock.randomTick`, at head | A sapling in a dying stand does not grow |

## Documentation

- [Stage 0 audit of TFC's API](docs/stage0-tfc-audit.md)
- [Where TFC breaks physics](docs/tfc-physics-audit.md)
- [Version plan](docs/roadmap.md)
- [Design of v1.0](docs/design-v1.0.md)
- [Developer handbook](docs/dev-handbook.md) and [ledger of physical values](docs/physics-ledger.md)
- [Field guide: what of TFC's book was rewritten, and the chapters still to write](docs/guide-audit.md)
- [Version reports](docs/reports), with the [global review of v1.0](docs/reports/v1.0-review.md)

Textures are generated by `tools/generate_textures.py`.
