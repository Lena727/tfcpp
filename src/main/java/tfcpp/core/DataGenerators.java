package tfcpp.core;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;
import java.util.Map;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import tfcpp.chemistry.ChemistryAssets;
import tfcpp.chemistry.ChemistryData;
import tfcpp.chemistry.CorrosiveSpill;
import tfcpp.geology.NewOre;
import tfcpp.geology.OreData;
import tfcpp.metallurgy.MetallurgyAssets;
import tfcpp.metallurgy.MetallurgyData;
import tfcpp.pneumatics.gas.Breathing;
import tfcpp.pneumatics.gas.GasIgnition;

@EventBusSubscriber(modid = TFCpp.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGenerators
{
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event)
    {
        final PackOutput output = event.getGenerator().getPackOutput();

        final DatapackBuiltinEntriesProvider datapack = event.getGenerator().addProvider(event.includeServer(), new DatapackBuiltinEntriesProvider(output, event.getLookupProvider(), new RegistrySetBuilder()
            .add(Registries.DAMAGE_TYPE, context -> {
                context.register(Breathing.ASPHYXIATION, new DamageType("tfcpp.asphyxiation", 0f));
                context.register(Breathing.CARBON_MONOXIDE, new DamageType("tfcpp.carbon_monoxide", 0f));
                context.register(Breathing.TOXIC_GAS, new DamageType("tfcpp.toxic_gas", 0f));
                context.register(GasIgnition.BLAST, new DamageType("tfcpp.blast", 0.1f));
                context.register(CorrosiveSpill.CORROSIVE, new DamageType("tfcpp.corrosive", 0f));
            }), Set.of(TFCpp.MOD_ID)));

        event.getGenerator().addProvider(event.includeServer(), new DamageTags(output, datapack.getRegistryProvider(), event.getExistingFileHelper()));

        event.getGenerator().addProvider(event.includeServer(), new MetallurgyData(output));
        event.getGenerator().addProvider(event.includeServer(), new ChemistryData(output));
        event.getGenerator().addProvider(event.includeServer(), new OreData(output));
        event.getGenerator().addProvider(event.includeClient(), new MetallurgyAssets.Models(output, event.getExistingFileHelper()));
        event.getGenerator().addProvider(event.includeClient(), new MetallurgyAssets.BlockStates(output, event.getExistingFileHelper()));
        event.getGenerator().addProvider(event.includeClient(), new ChemistryAssets.Models(output, event.getExistingFileHelper()));
        event.getGenerator().addProvider(event.includeClient(), new ChemistryAssets.BlockStates(output, event.getExistingFileHelper()));

        event.getGenerator().addProvider(event.includeClient(), new English(output));
        event.getGenerator().addProvider(event.includeClient(), new Russian(output));
    }

    private static final Map<Rock, String> RUSSIAN_ROCKS = Map.ofEntries(
        Map.entry(Rock.GABBRO, "габбро"), Map.entry(Rock.DIORITE, "диорит"), Map.entry(Rock.SHALE, "сланец"), Map.entry(Rock.CLAYSTONE, "аргиллит"),
        Map.entry(Rock.LIMESTONE, "известняк"), Map.entry(Rock.DOLOMITE, "доломит"), Map.entry(Rock.CHERT, "кремень"), Map.entry(Rock.BASALT, "базальт"),
        Map.entry(Rock.QUARTZITE, "кварцит"), Map.entry(Rock.SLATE, "кровельный сланец"), Map.entry(Rock.PHYLLITE, "филлит"), Map.entry(Rock.SCHIST, "кристаллический сланец"),
        Map.entry(Rock.GNEISS, "гнейс"), Map.entry(Rock.MARBLE, "мрамор"));

    private static String capitalized(String word)
    {
        return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }

    /** Gas acts on the lungs and blood, so armour does not reduce it, and it does not knock anybody back. */
    private static final class DamageTags extends TagsProvider<DamageType>
    {
        DamageTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper)
        {
            super(output, Registries.DAMAGE_TYPE, lookup, TFCpp.MOD_ID, helper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider)
        {
            for (TagKey<DamageType> tag : List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_SHIELD, DamageTypeTags.NO_KNOCKBACK))
            {
                tag(tag).add(Breathing.ASPHYXIATION, Breathing.CARBON_MONOXIDE, Breathing.TOXIC_GAS);
            }
            tag(DamageTypeTags.BYPASSES_ARMOR).add(GasIgnition.BLAST); // A pressure wave passes straight through armour and into the lungs
            tag(DamageTypeTags.IS_EXPLOSION).add(GasIgnition.BLAST);
        }
    }

    private static final class English extends LanguageProvider
    {
        English(PackOutput output)
        {
            super(output, TFCpp.MOD_ID, "en_us");
        }

        @Override
        protected void addTranslations()
        {
            add("death.attack.tfcpp.asphyxiation", "%1$s ran out of air to breathe");
            add("death.attack.tfcpp.carbon_monoxide", "%1$s fell asleep and never woke up");
            add("death.attack.tfcpp.toxic_gas", "%1$s choked on toxic fumes");
            add("death.attack.tfcpp.corrosive", "%1$s was scalded by a splash of acid");
            add("tfcpp.gas.smell.sulfur_dioxide", "A sharp, choking smell of burning sulfur catches your throat");
            add("tfcpp.gas.smell.hydrogen_sulfide", "The air stinks of rotten eggs");
            add("tfcpp.gas.smell.ammonia", "A pungent smell of ammonia stings your eyes");
            add("tfcpp.gas.smell.chlorine", "A sharp bleach-like smell burns your nose");
            add("tfcpp.gas.smell.nitrogen_oxides", "A sharp, acrid smell of nitrous fumes stings your eyes");
            add("tfcpp.gas.smell.hydrogen_chloride", "A sharp, choking sourness of hydrochloric fumes burns your throat");

            add("itemGroup.tfcpp", "TFC++");
            add("fluid_type.tfcpp.metal.arsenical_copper", "Molten Arsenical Copper");
            add("fluid_type.tfcpp.mercury", "Mercury");
            add("item.tfcpp.metal.ingot.arsenical_copper", "Arsenical Copper Ingot");
            add("item.tfcpp.calcine.small_zinc", "Small Zinc Calcine");
            add("item.tfcpp.calcine.poor_zinc", "Poor Zinc Calcine");
            add("item.tfcpp.calcine.normal_zinc", "Zinc Calcine");
            add("item.tfcpp.calcine.rich_zinc", "Rich Zinc Calcine");
            add("item.tfcpp.glass_bulb", "Glass Bulb");
            add("item.tfcpp.mercury_bulb", "Mercury Bulb");
            add("fluid_type.tfcpp.metal.iron_oxide", "Molten Iron Oxide");
            add("item.tfcpp.powder.borax", "Borax Flux");
            add("fluid_type.tfcpp.metal.lead", "Molten Lead");
            add("item.tfcpp.metal.ingot.lead", "Lead Ingot");
            add("fluid_type.tfcpp.metal.work_lead", "Molten Work Lead");
            add("item.tfcpp.metal.ingot.work_lead", "Work Lead Ingot");
            add("item.tfcpp.metal.sheet.lead", "Lead Sheet");
            add("item.tfcpp.powder.bone_ash", "Bone Ash");
            add("item.tfcpp.powder.lead_oxide", "Litharge");
            add("item.tfcpp.powder.apatite", "Ground Apatite");
            for (NewOre ore : NewOre.values())
            {
                for (String kind : ore.kinds())
                {
                    final String grade = ore.graded() ? kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1, kind.indexOf('_')) + " " : "";
                    add("item.tfcpp.ore." + kind, grade + ore.english());
                    for (Rock rock : ore.hosts())
                    {
                        final String key = "block.tfcpp.ore." + kind + "." + NewOre.rockId(rock);
                        add(key, grade + capitalized(NewOre.rockId(rock)) + " " + ore.english());
                        add(key + ".prospected", ore.english());
                    }
                }
                if (ore.graded())
                {
                    add("block.tfcpp.ore.small_" + ore.id(), "Small " + ore.english());
                }
            }
            add("item.tfcpp.slag.iron", "Iron Slag");
            add("item.tfcpp.slag.furnace", "Blast Furnace Slag");
            add("item.tfcpp.slag.phosphoric_iron", "Phosphoric Iron Slag");
            add("item.tfcpp.slag.thomas", "Thomas Slag");
            add("item.tfcpp.powder.thomas_meal", "Thomas Meal");
            add("item.tfcpp.powder.dolomite", "Ground Dolomite");
            add("item.tfcpp.powder.burnt_dolomite", "Burnt Dolomite");
            add("item.tfcpp.basic_brick", "Basic Brick");
            add("item.tfcpp.metal.ingot.phosphoric_pig_iron", "Phosphoric Pig Iron Ingot");
            add("item.tfcpp.metal.ingot.cold_short_iron", "Cold Short Iron Ingot");
            add("fluid_type.tfcpp.metal.phosphoric_iron_oxide", "Molten Phosphoric Iron Oxide");
            add("fluid_type.tfcpp.metal.phosphoric_pig_iron", "Molten Phosphoric Pig Iron");
            add("fluid_type.tfcpp.metal.cold_short_iron", "Molten Cold Short Iron");
            add("block.tfcpp.converter", "Bessemer Converter");
            add("tfcpp.converter.status", "%1$s C, %2$s mB of %3$s, %4$s, lining %5$s (%6$s), lime %7$s mol");
            add("tfcpp.converter.status_empty", "Empty, lining %1$s (%2$s), lime %3$s mol");
            add("tfcpp.converter.reline_basic", "A basic lining can only be repaired with basic bricks: fire clay is silica, and would flux away in the slag");
            add("tfcpp.converter.basic_progress", "Basic lining: %1$s of %2$s bricks laid. Until the last it is still the acid one");
            add("tfcpp.converter.basic_done", "The lining is now basic");
            add("tfcpp.converter.lime_acid", "Lime would only flux the silica lining away. Lay a basic lining first");
            add("tfcpp.converter.lining.acid", "acid");
            add("tfcpp.converter.lining.basic", "basic");
            add("tfcpp.converter.flame.afterblow", "thick white smoke over the mouth");
            add("tfcpp.converter.set", "the iron has set in the vessel");
            add("tfcpp.converter.reline_full", "It has to be emptied before it can be relined");
            add("tfcpp.converter.flame.sparks", "sparks, hardly any flame");
            add("tfcpp.converter.flame.boiling", "a long white flame");
            add("tfcpp.converter.flame.shortening", "the flame is shortening");
            add("tfcpp.converter.flame.dropped", "the flame has dropped");
            add("tfcpp.converter.lining.good", "sound");
            add("tfcpp.converter.lining.worn", "worn");
            add("tfcpp.converter.lining.thin", "thin, and about to go");
            add("tfcpp.info.converter", "Set below a blast furnace or pour in molten iron, then blow air through it from a pipe at a pressure above about 1.7 atm. Silicon burns first in a shower of sparks, then carbon in a long flame, and when the flame drops the iron itself starts to burn. Stop while the flame is shortening for steel. No fuel is used: the reactions alone keep the iron liquid, but only with a strong enough blast, and iron that sets in the vessel stops the blow. Water in the pipe will blow it apart. Reline it with fire bricks when it is empty. A new vessel is lined with silica, and phosphorus stays in the iron: a tap of it is brittle cold short iron. Eight basic bricks laid in an empty vessel make the lining basic. Throw lime powder into a basic vessel, and after the carbon is gone the phosphorus burns out as thick white smoke into Thomas slag, if there is lime enough for the silicon and the phosphorus both. The silica of a basic vessel with no lime eats its lining.");
            add("tfcpp.info.iron_slag", "Holds iron that the furnace could not get out. It melts back into a blast furnace or bloomery charge.");
            add("tfcpp.info.phosphoric_iron_slag", "Slag from smelting brown ore, as rich in phosphorus as the tap cinder of a real bloomery. It melts back as phosphoric oxide, so it does not clean anything.");
            add("tfcpp.info.thomas_slag", "Basic slag from a converter on a dolomite lining, full of calcium phosphate. Grind it in a quern for a phosphorus fertiliser.");
            add("tfcpp.info.basic_brick", "Burnt dolomite pressed with coal tar, and not fired. Eight of them in an empty converter make its lining basic, and repair a basic lining as fire bricks repair an acid one.");
            add("block.tfcpp.retort", "Retort");
            add("block.tfcpp.retort_condenser", "Retort Condenser");
            add("tfcpp.retort.status", "%1$s C, charge: %2$s, carbon: %3$s, %4$s mB of %5$s");
            add("tfcpp.retort.status.empty_tank", "nothing condensed yet");
            add("tfcpp.retort.no_condenser", "No condenser beside the retort: the vapour will escape into the room");

            add("death.attack.tfcpp.blast", "%1$s was caught in a gas explosion");
            add("block.tfcpp.davy_lamp", "Davy Lamp");
            add("block.tfcpp.davy_lamp.filled", "Davy Lamp");
            add("item.tfcpp.wire_gauze", "Copper Wire Gauze");
            add("item.tfcpp.animal_cage", "Animal Cage");
            add("item.tfcpp.sentinel_cage", "Caged Sentinel Animal");
            add("block.tfcpp.settling_baffle", "Settling Baffle");
            add("item.tfcpp.powder.arsenic_trioxide", "White Arsenic");
            add("item.tfcpp.powder.antimony_trioxide", "Antimony Trioxide");
            add("item.tfcpp.powder.zinc_oxide", "Zinc Oxide");
            add("tfcpp.davy_lamp.steady", "The flame burns steady and yellow");
            add("tfcpp.davy_lamp.dim", "The flame burns low and dull: the air is short of oxygen");
            add("tfcpp.davy_lamp.pale_cap", "A faint pale cap shows above the flame");
            add("tfcpp.davy_lamp.blue_cap", "A blue cap stands above the flame");
            add("tfcpp.davy_lamp.tall_cap", "A tall blue cap reaches up the gauze: get out soon");
            add("tfcpp.davy_lamp.filled", "Flame fills the whole gauze: the air is explosive, get out now");
            add("tfcpp.davy_lamp.out", "The flame has died: this air cannot keep a flame going, and soon not a person either");
            add("tfcpp.sentinel.uneasy", "The animal in the cage is uneasy and breathing fast");
            add("tfcpp.sentinel.collapsed", "The animal in the cage has collapsed: leave, the air is poisoned");
            add("tfcpp.sentinel.dead", "The animal in the cage is dead");
            add("tfcpp.sentinel.starved", "The animal in the cage has starved");
            add("tfcpp.sentinel.state.lively", "Lively");
            add("tfcpp.sentinel.state.uneasy", "Uneasy");
            add("tfcpp.sentinel.state.collapsed", "Collapsed");
            add("tfcpp.settling_baffle.nothing", "Too little dust on the plates to scrape off");
            add("tfcpp.stone_dust.sample", "Sample from the floor here: %s%% of the dust cannot burn (at least %s%% is needed)");
            add("tfcpp.jei.retort", "Retort");
            add("tfcpp.jei.retort.temperature", "%s °C");
            add("tfcpp.jei.retort.carbon", "Carbon: %s mol");
            add("tfcpp.jei.retort.condenser", "Needs a condenser beside it");
            add("tfcpp.info.davy_lamp", "A lamp whose gauze keeps its flame from lighting the air around it. Carried in hand with oil in it, it shows firedamp as a blue cap above the flame, and goes out when oxygen runs low. It does not show carbon monoxide, and a gauze with gas burning in it for minutes gets hot enough to light the gas outside.");
            add("tfcpp.info.sentinel_cage", "Right-click a rat or a quail with an empty cage. The animal takes in carbon monoxide twenty times as fast as a person, so it collapses long before you feel anything. Feed it grain from the other hand at least every three days.");
            add("tfcpp.info.settling_baffle", "Place these in a closed chamber that furnace fumes pass through. Arsenic, antimony and zinc oxide dust settles on the plates, and is scraped off by right-clicking with an empty hand. Scraping stirs up some of the dust, so do not breathe it.");
            add("tfcpp.info.condensation_tower", "Put it in a run of pipe that carries furnace fumes. While the gas is below 250 C, arsenic, antimony, zinc and lead oxide settle on its walls as dust, and mercury runs down as a liquid when the gas is below 100 C. Sulfur dioxide goes on through. Scrape the dust off with an empty hand while crouching, which stirs some of it into the air, and tap the mercury into a container. Several towers in a row catch proportionally more.");
            add("block.tfcpp.mine_fan", "Mine Fan");
            add("block.tfcpp.pipe.wood", "Wooden Pipe");
            add("block.tfcpp.pipe.ceramic", "Fired Clay Pipe");
            add("block.tfcpp.pipe.copper", "Copper Pipe");
            add("block.tfcpp.pipe.lead", "Lead Pipe");
            add("block.tfcpp.pipe.wrought_iron", "Wrought Iron Pipe");
            add("block.tfcpp.pipe.steel", "Steel Pipe");
            add("block.tfcpp.pipe_valve", "Gate Valve");
            add("block.tfcpp.pipe_vent", "Pipe Vent");
            add("block.tfcpp.pipe_tap", "Pipe Tap");
            add("block.tfcpp.safety_valve", "Safety Valve");
            add("block.tfcpp.mercury_manometer", "Mercury Manometer");
            add("block.tfcpp.pressure_vessel", "Pressure Vessel");
            add("block.tfcpp.piston_pump", "Piston Pump");
            add("tfcpp.pipe.opened", "Opened");
            add("tfcpp.pipe.closed", "Closed");
            add("tfcpp.manometer.reading", "%s mm of mercury. The air outside is 760");
            add("tfcpp.info.pipes", "A run of pipe is one volume at one pressure, and bursts at its weakest wall or joint. Hemp and tallow joints hold 10 atm at most. Corrosion thins the walls: copper suffers from ammonia and mercury, iron from brine and chlorine, and wood chars when hot. Breaking a pipe lets out everything in the run, so close a valve first.");
            add("tfcpp.info.pressure_vessel", "Half a cubic metre that joins any pipe touching it. A fire below heats everything in its network. Gas gains pressure in proportion to its absolute temperature, water adds its vapour pressure, and a vessel full of liquid with no room to expand gives way almost at once. Fit a safety valve.");
            add("tfcpp.info.piston_pump", "Driven by a shaft from the side. It takes in from the back, from a pipe, a barrel, standing water or the air, and delivers into a pipe at the front. Every turn sweeps the same volume, and the shaft pays for the difference in pressure.");
            add("tfcpp.info.safety_valve", "Lifts at 8 atm above the air outside and seats again at 7, with a loud hiss. It cannot be held shut.");
            add("tfcpp.info.manometer", "Right-click to read the pressure. Above 3 atm over the air outside the mercury is blown out of the tube, into the room.");
            add("tfcpp.info.mine_fan", "Driven by a shaft at its back, it blows out of its front and draws air in through its other sides, along the open spaces leading to it. Air moves quickly along a narrow drift and hardly at all across a large chamber. The fan loads the shaft more the faster it turns.");
            add("tfcpp.info.shaft_load", "Every machine on a shaft takes torque from its source. The more it has to drive, the slower it turns, and past what the source can start against it stops. A water wheel gives its best power at half of its free speed. Pumps take more torque the higher they lift. Water itself no longer multiplies, and a pump moves it from its intake to its outlet.");
            add("tfcpp.info.coal", "Coal seams hold methane, more of it the deeper they lie, and some rock next to them holds gas under pressure. Mining lets it out. A flame lights air holding 5% to 15% of it, and the blast is followed by afterdamp full of carbon monoxide.");

            add("fluid_type.tfcpp.coal_tar", "Coal Tar");
            add("fluid_type.tfcpp.wood_tar", "Wood Tar");
            add("fluid_type.tfcpp.ammonia_liquor", "Ammoniacal Liquor");
            add("fluid_type.tfcpp.pyroligneous_liquor", "Pyroligneous Liquor");
            add("fluid_type.tfcpp.chamber_acid", "Chamber Acid");
            add("item.tfcpp.coke", "Coke");
            add("item.tfcpp.lignite_char", "Lignite Char");
            add("block.tfcpp.coke_oven", "Coke Oven");
            add("block.tfcpp.gas_burner", "Gas Burner");
            add("block.tfcpp.lead_chamber", "Lead Chamber");
            add("block.tfcpp.sulfur_burner", "Sulfur Burner");
            add("tfcpp.lead_chamber.status", "Gas %1$s C. Acid in the tank: %2$s mB. In the gas: sulfur dioxide %3$s%%, oxygen %4$s%%, nitrogen oxides %5$s%%. Water: %6$s L");
            add("tfcpp.lead_chamber.no_network", "Not joined to a pipe: no gas passes through it");
            add("tfcpp.lead_chamber.acid", "Acid in the tank: %1$s mB");
            add("tfcpp.lead_chamber.no_water", "No water in the network: no acid can form");
            add("tfcpp.lead_chamber.oxides_low", "Nitrogen oxides are below %1$s%%: the reaction is slow until saltpeter brings them in");
            add("tfcpp.lead_chamber.oxygen_low", "Oxygen is below %1$s%%: the reaction is slow");
            add("tfcpp.lead_chamber.empty", "The tank holds no acid");
            add("tfcpp.lead_chamber.refused", "This container does not take chamber acid");
            add("tfcpp.lead_chamber.saltpeter", "Saltpeter goes into the gas as nitrous fumes");
            add("tfcpp.lead_chamber.saltpeter_no_network", "Not joined to a pipe: the nitrous fumes have nowhere to go");
            add("tfcpp.sulfur_burner.status", "Sulfur in the burner: %1$s of %2$s powders");
            add("tfcpp.sulfur_burner.no_network", "Not joined to a pipe: nothing burns without a network");
            add("tfcpp.sulfur_burner.full", "The burner is full of sulfur");
            add("tfcpp.sulfur_burner.no_air", "Oxygen is below %1$s%%: the sulfur smoulders out and does not burn");
            add("tfcpp.coke_oven.status", "%1$s C, charge: %2$s, %3$s%% done, tar: %4$s mB, liquor: %5$s mB");
            add("tfcpp.coke_oven.status.empty", "empty");
            add("tfcpp.coke_oven.too_cold", "Nothing changes below %1$s C");
            add("tfcpp.coke_oven.tanks_full", "A tank is full or holds another liquid: the batch waits until it is emptied");
            add("tfcpp.coke_oven.ready", "Ready to take out: %1$s x %2$s (crouch and use an empty hand)");
            add("tfcpp.coke_oven.no_condenser", "No condenser beside the oven: the tar and liquor will be lost as vapour");
            add("tfcpp.coke_oven.locked", "The charge has begun to change and cannot be added to");
            add("tfcpp.coke_oven.take_first", "Take out the finished batch first");
            add("tfcpp.coke_oven.one_kind", "An oven takes one kind of charge at a time");
            add("tfcpp.coke_oven.full", "The oven is full: 64 pieces at most");
            add("tfcpp.coke_oven.loaded", "%1$s x %2$s in the oven");
            add("tfcpp.coke_oven.nothing", "Nothing to take out");
            add("tfcpp.gas_burner.shut", "Open the gas first");
            add("block.tfcpp.cupel_hearth", "Cupellation Hearth");
            add("tfcpp.cupel_hearth.status", "%1$s C, lead: %2$s mB, silver: %3$s mB, the ash can still take %4$s mB of lead");
            add("tfcpp.cupel_hearth.needs_ash", "The floor is full of litharge: lay bone ash on it, or the blast stops");
            add("tfcpp.cupel_hearth.too_cold", "Litharge stays liquid only above %1$s C, and the blast does nothing below it");
            add("tfcpp.cupel_hearth.poor_air", "The air over the hearth has too little oxygen for the blast");
            add("tfcpp.cupel_hearth.plain_lead", "Plain lead holds no silver to take out: only work lead can be cupelled");
            add("tfcpp.cupel_hearth.wrong_fluid", "Only work lead can be poured in");
            add("tfcpp.cupel_hearth.full", "The hearth is full of lead");
            add("tfcpp.cupel_hearth.lead_left", "The lead must all be oxidised before the silver can be tapped");
            add("tfcpp.cupel_hearth.no_silver", "There is no silver to tap yet");
            add("tfcpp.cupel_hearth.blick", "Blick: the last litharge has cleared, and the silver flashes");
            add("block.tfcpp.condensation_tower", "Condensation Tower");
            add("tfcpp.condensation_tower.status", "Gas %1$s C. Dust on the walls, in powders: arsenic %2$s, antimony %3$s, zinc %4$s, lead %5$s. Mercury: %6$s mB");
            add("tfcpp.condensation_tower.nothing", "Too little dust on the walls to scrape off");
            add("tfcpp.condensation_tower.no_network", "Not joined to a pipe: no gas passes through it");
            add("tfcpp.condensation_tower.too_hot", "The gas at %1$s C is too hot: dust settles only below %2$s C and mercury only below %3$s C");
            add("tfcpp.condensation_tower.tank_full", "The mercury tank is full, and more stays in the gas");
            add("fluid_type.tfcpp.sulfuric_acid", "Oil of Vitriol");
            add("fluid_type.tfcpp.nitric_acid", "Aqua Fortis");
            add("fluid_type.tfcpp.hydrochloric_acid", "Spirit of Salt");
            add("fluid_type.tfcpp.wood_spirit", "Wood Spirit");
            add("item.tfcpp.powder.potassium_bisulfate", "Potassium Bisulfate");
            add("item.tfcpp.powder.sodium_bisulfate", "Sodium Bisulfate");
            add("item.tfcpp.powder.superphosphate", "Superphosphate");
            add("item.tfcpp.powder.ammonium_sulfate", "Ammonium Sulfate");
            add("item.tfcpp.borosilicate_glass_batch", "Borosilicate Glass Batch");
            add("item.tfcpp.glass_carboy", "Glass Carboy");
            add("item.tfcpp.glass_carboy.filled", "Carboy of %s");
            add("block.tfcpp.glass_retort", "Glass Retort");
            add("block.tfcpp.sand_bath", "Sand Bath");
            add("tfcpp.sand_bath.status", "Sand %1$s of %2$s deep, %3$s C, passing up %4$s C to the vessel on it");
            add("tfcpp.glass_retort.cracked", "The flask cracks at %1$s C and its contents spill");
            add("tfcpp.glass_retort.status", "Glass retort: %1$s C (it cracks above %2$s C), %3$s");
            add("tfcpp.glass_retort.stage.idle", "idle");
            add("tfcpp.glass_retort.stage.running", "running, %1$s%% done");
            add("tfcpp.glass_retort.contents", "Flask: %1$s. Second liquid: %2$s. Powder: %3$s");
            add("tfcpp.glass_retort.receiver", "Receiver: %1$s. Left in the flask: %2$s. Receiver water: %3$s");
            add("tfcpp.glass_retort.tank", "%1$s L of %2$s");
            add("tfcpp.glass_retort.empty", "nothing");
            add("tfcpp.glass_retort.ready", "Ready to take out: %1$s x %2$s (crouch and use an empty hand)");
            add("tfcpp.glass_retort.nothing", "Nothing to take out");
            add("tfcpp.glass_retort.no_condenser", "No condenser beside the retort: what boils off will escape into the room");
            add("tfcpp.glass_retort.no_recipe", "This charge makes nothing: check the liquids and the powder");
            add("tfcpp.glass_retort.dry", "The receiver does not hold %1$s L of water to take up the hydrogen chloride: it will go into the air");
            add("tfcpp.glass_retort.need_items", "A batch needs %1$s powders in the slot");
            add("tfcpp.glass_retort.need_liquid", "A batch needs %1$s L of the liquid in the flask");
            add("tfcpp.glass_retort.need_reagent", "A batch needs %1$s L of the second liquid");
            add("tfcpp.glass_retort.too_cold", "Nothing comes over below %1$s C");
            add("tfcpp.glass_retort.too_hot", "Nothing comes over properly above %1$s C: the charge is held");
            add("tfcpp.glass_retort.output_full", "The receiver is full or holds another liquid: the batch waits until it is emptied");
            add("tfcpp.glass_retort.refused", "The flask takes no such liquid, or both its tanks hold others");
            add("tfcpp.glass_retort.nothing_to_tap", "Nothing to run into the container");
            add("tfcpp.glass_retort.one_kind", "The slot takes one kind of powder at a time");
            add("tfcpp.glass_retort.full", "The slot is full");
            add("tfcpp.glass_retort.loaded", "%1$s x %2$s in the slot");
            add("tfcpp.info.glass_retort", "Set it over a sand bath, with a retort condenser beside it. Right-click with a carboy to pour in the liquids (a recipe takes one or two) and water for the receiver, or powder into the slot. Heat it only to the working range of the charge: nothing comes over below it, and glass cracks above 550 C, spilling everything. Without a condenser the acids escape as fumes, and without water in the receiver hydrogen chloride goes into the air. Crouch with an empty carboy to draw off the flask's own liquids.");
            add("tfcpp.info.sand_bath", "A tray of sand between a fire and glass. It passes up only 20 C plus a share of the fire's excess over that, one part in the depth plus one, so deeper sand gives a cooler vessel. Empty hand adds a layer, crouching removes one.");
            add("tfcpp.info.glass_carboy", "Holds 25 L of acids, the chemical liquids of the mod, vinegar and fresh water, and nothing else. Right-click a tank or a retort to pour or draw.");
            add("tfcpp.info.borosilicate_glass_batch", "Sand, lime and borax. Load it into a blowpipe and work it as any batch: this is the glass that stands heat and acid. Makes the glass carboy and the glass retort.");
            add("tfcpp.info.coke_oven", "A closed chamber of fire brick, heated from below, that distils a charge of up to 64 pieces of coal or wood without air. Its gas goes up the pipe above it, or into the room if there is no pipe, its tar and liquor condense in the tanks of a condenser beside it, and the coke is taken out when the batch is done. Without a condenser the tar and liquor are lost as vapour, and the gas is poisonous, so lead it into a pipe.");
            add("tfcpp.info.gas_burner", "A nozzle with a hand wheel that burns the gas of the pipe it is joined to, under whatever stands on it. Light it with flint and steel or a fire starter: a lit burner is an open flame to any gas in the room, so do not open it near methane or coal gas.");
            add("tfcpp.info.cupel_hearth", "A fire-brick hearth floored with bone ash, over a fire. Pour in work lead and blow air over it: the lead oxidises to litharge, which is raked off, and the silver is left to be tapped at the end. Keep the hearth hot enough for the litharge to stay liquid, and do not breathe the lead fume that the blast raises.");
            add("tfcpp.info.lead_chamber", "A chamber of lead sheet in a pipe run, where sulfur dioxide, air, steam and the oxides of nitrogen make chamber acid on its walls. Lead holds the acid while it is cool, so the tank can be drawn off into a carboy by hand. The oxides of nitrogen are poisonous, and they leak out of any joint that is not sealed.");
            add("tfcpp.info.sulfur_burner", "A trough of fire clay in a pipe, where powdered sulfur burns in the air of the pipe to sulfur dioxide, which the lead chamber takes up. It lights from the hot flue and needs oxygen: with too little, the sulfur only smoulders. Sulfur dioxide chokes, so the trough must stand in a pipe that carries the gas away.");
            add("tfcpp.info.marsh_apparatus", "Charge it with a millibucket of sulfuric or chamber acid from a carboy in the other hand, which gives eight tests. Right-click in the air to test the air at your eye, or with a sample in the other hand: white arsenic, antimony oxide, tetrahedrite ore or arsenical copper. A mirror says that arsenic or antimony is there, and not which one; the arsine it makes is poisonous, so test in the open.");
            add("tfcpp.info.gold_leaf_indicator", "A leaf of gold on a thin plate, which mercury vapour whitens for good: from bright gold, through pale, to white. Place it where mercury may reach it, and read its stage with an empty hand; it shows no number. A leaf that has whitened comes up as nothing when it is broken, and a clean one comes up as a new leaf.");
            add("item.tfcpp.marsh_apparatus", "Marsh Apparatus");
            add("tfcpp.marsh.tests", "Tests left: %1$s");
            add("tfcpp.marsh.none", "No mirror: neither arsenic nor antimony is in the sample");
            add("tfcpp.marsh.faint", "A faint mirror: it could be arsenic or antimony, and only a trace of it");
            add("tfcpp.marsh.strong", "A dense mirror: it could be arsenic or antimony, and the air of this place is poisonous");
            add("tfcpp.marsh.no_acid", "The carboy in the other hand holds no sulfuric or chamber acid: the apparatus cannot be charged");
            add("tfcpp.marsh.charged", "Charged with one millibucket of acid: %1$s tests");
            add("block.tfcpp.gold_leaf_indicator", "Gold Leaf Indicator");
            add("tfcpp.gold_leaf.stage.0", "The leaf is bright gold: no mercury has reached it");
            add("tfcpp.gold_leaf.stage.1", "Traces: the leaf has whitened at its edges, from mercury in the air");
            add("tfcpp.gold_leaf.stage.2", "Exposed: the leaf is pale, and the air here has held mercury for a long time");
            add("tfcpp.gold_leaf.stage.3", "Heavily poisoned: the leaf is white, and the mercury in it will not come out");
        }
    }

    private static final class Russian extends LanguageProvider
    {
        Russian(PackOutput output)
        {
            super(output, TFCpp.MOD_ID, "ru_ru");
        }

        @Override
        protected void addTranslations()
        {
            add("death.attack.tfcpp.asphyxiation", "%1$s задохнулся: в воздухе не осталось кислорода");
            add("death.attack.tfcpp.carbon_monoxide", "%1$s уснул и больше не проснулся");
            add("death.attack.tfcpp.toxic_gas", "%1$s отравился ядовитыми испарениями");
            add("death.attack.tfcpp.corrosive", "%1$s обжёгся брызгами кислоты");
            add("tfcpp.gas.smell.sulfur_dioxide", "Резкий удушливый запах горелой серы перехватывает горло");
            add("tfcpp.gas.smell.hydrogen_sulfide", "В воздухе стоит запах тухлых яиц");
            add("tfcpp.gas.smell.ammonia", "Едкий запах нашатыря щиплет глаза");
            add("tfcpp.gas.smell.chlorine", "Резкий запах хлорки обжигает нос");
            add("tfcpp.gas.smell.nitrogen_oxides", "Едкий запах окислов азота режет глаза");
            add("tfcpp.gas.smell.hydrogen_chloride", "Едкая кислота соляных паров обжигает горло");

            add("itemGroup.tfcpp", "TFC++");
            add("fluid_type.tfcpp.metal.arsenical_copper", "Расплав мышьяковистой меди");
            add("fluid_type.tfcpp.mercury", "Ртуть");
            add("item.tfcpp.metal.ingot.arsenical_copper", "Слиток черновой мышьяковистой меди");
            add("item.tfcpp.calcine.small_zinc", "Малый цинковый огарок");
            add("item.tfcpp.calcine.poor_zinc", "Бедный цинковый огарок");
            add("item.tfcpp.calcine.normal_zinc", "Цинковый огарок");
            add("item.tfcpp.calcine.rich_zinc", "Богатый цинковый огарок");
            add("item.tfcpp.glass_bulb", "Стеклянная колба");
            add("item.tfcpp.mercury_bulb", "Ртутная колба");
            add("fluid_type.tfcpp.metal.iron_oxide", "Расплав оксида железа");
            add("item.tfcpp.powder.borax", "Флюс из буры");
            add("fluid_type.tfcpp.metal.lead", "Расплав свинца");
            add("item.tfcpp.metal.ingot.lead", "Слиток свинца");
            add("fluid_type.tfcpp.metal.work_lead", "Расплав веркблея");
            add("item.tfcpp.metal.ingot.work_lead", "Слиток веркблея");
            add("item.tfcpp.metal.sheet.lead", "Свинцовый лист");
            add("item.tfcpp.powder.bone_ash", "Костяная зола");
            add("item.tfcpp.powder.lead_oxide", "Глёт");
            add("item.tfcpp.powder.apatite", "Молотый апатит");
            for (NewOre ore : NewOre.values())
            {
                for (String kind : ore.kinds())
                {
                    final String grade = !ore.graded() ? "" : kind.startsWith("poor") ? "Бедный " : kind.startsWith("normal") ? "Обычный " : "Богатый ";
                    final String name = grade.isEmpty() ? capitalized(ore.russian()) : grade + ore.russian();
                    add("item.tfcpp.ore." + kind, name);
                    for (Rock rock : ore.hosts())
                    {
                        final String key = "block.tfcpp.ore." + kind + "." + NewOre.rockId(rock);
                        add(key, name + " в породе: " + RUSSIAN_ROCKS.get(rock));
                        add(key + ".prospected", capitalized(ore.russian()));
                    }
                }
                if (ore.graded())
                {
                    add("block.tfcpp.ore.small_" + ore.id(), "Мелкий " + ore.russian());
                }
            }
            add("item.tfcpp.slag.iron", "Железистый шлак");
            add("item.tfcpp.slag.furnace", "Доменный шлак");
            add("item.tfcpp.slag.phosphoric_iron", "Фосфористый железистый шлак");
            add("item.tfcpp.slag.thomas", "Томасовский шлак");
            add("item.tfcpp.powder.thomas_meal", "Томасшлаковая мука");
            add("item.tfcpp.powder.dolomite", "Молотый доломит");
            add("item.tfcpp.powder.burnt_dolomite", "Обожжённый доломит");
            add("item.tfcpp.basic_brick", "Основной кирпич");
            add("item.tfcpp.metal.ingot.phosphoric_pig_iron", "Слиток фосфористого чугуна");
            add("item.tfcpp.metal.ingot.cold_short_iron", "Слиток хладноломкого железа");
            add("fluid_type.tfcpp.metal.phosphoric_iron_oxide", "Расплав фосфористого оксида железа");
            add("fluid_type.tfcpp.metal.phosphoric_pig_iron", "Расплав фосфористого чугуна");
            add("fluid_type.tfcpp.metal.cold_short_iron", "Расплав хладноломкого железа");
            add("block.tfcpp.converter", "Конвертер Бессемера");
            add("tfcpp.converter.status", "%1$s °C, %2$s мБ: %3$s, %4$s, футеровка %5$s (%6$s), извести %7$s моль");
            add("tfcpp.converter.status_empty", "Пусто, футеровка %1$s (%2$s), извести %3$s моль");
            add("tfcpp.converter.reline_basic", "Основную футеровку чинят только основными кирпичами: шамот это кремнезём, и он ушёл бы в шлак");
            add("tfcpp.converter.basic_progress", "Основная футеровка: уложено %1$s из %2$s кирпичей. Пока не уложен последний, она остаётся кислой");
            add("tfcpp.converter.basic_done", "Футеровка стала основной");
            add("tfcpp.converter.lime_acid", "Известь лишь разъела бы кремнезёмную футеровку. Сначала выложите основную");
            add("tfcpp.converter.lining.acid", "кислая");
            add("tfcpp.converter.lining.basic", "основная");
            add("tfcpp.converter.flame.afterblow", "густой белый дым над горловиной");
            add("tfcpp.converter.set", "железо застыло в сосуде");
            add("tfcpp.converter.reline_full", "Перед заменой футеровки сосуд нужно опорожнить");
            add("tfcpp.converter.flame.sparks", "искры, пламени почти нет");
            add("tfcpp.converter.flame.boiling", "длинное белое пламя");
            add("tfcpp.converter.flame.shortening", "пламя укорачивается");
            add("tfcpp.converter.flame.dropped", "пламя упало");
            add("tfcpp.converter.lining.good", "целая");
            add("tfcpp.converter.lining.worn", "изношена");
            add("tfcpp.converter.lining.thin", "истончилась, скоро прогорит");
            add("tfcpp.info.converter", "Поставьте под доменную печь или налейте расплавленное железо, затем продувайте воздухом из трубы при давлении выше примерно 1,7 атм. Сначала в снопе искр горит кремний, затем в длинном пламени углерод, а когда пламя падает, начинает гореть само железо. Для стали остановите дутьё, пока пламя укорачивается. Топливо не нужно: железо остаётся жидким только от тепла реакций, но лишь при достаточно сильном дутье, а застывшее железо останавливает продувку. Вода в трубе разнесёт конвертер. Когда он пуст, футеровку можно обновить шамотным кирпичом. Новый сосуд выложен кремнезёмом, и фосфор остаётся в железе: выпуск из него это ломкое хладноломкое железо. Восемь основных кирпичей, уложенных в пустой сосуд, делают футеровку основной. Бросьте в основной сосуд порошок извести, и когда углерод уйдёт, фосфор сгорит густым белым дымом в томасовский шлак, если извести хватит и на кремний, и на фосфор. Кремнезём в основном сосуде без извести разъедает футеровку.");
            add("tfcpp.info.iron_slag", "В нём осталось железо, которое печь не смогла извлечь. Его можно снова загрузить в доменную или сыродутную печь.");
            add("tfcpp.info.phosphoric_iron_slag", "Шлак от плавки бурого железняка, такой же фосфористый, как настоящий шлак сыродутной печи. Он плавится обратно в фосфористый оксид, так что ничего не очищает.");
            add("tfcpp.info.thomas_slag", "Основной шлак конвертера с доломитовой футеровкой, полный фосфата кальция. Перемелите его на жёрнове в фосфорное удобрение.");
            add("tfcpp.info.basic_brick", "Обожжённый доломит, спрессованный с каменноугольной смолой и не обожжённый. Восемь штук в пустом конвертере делают его футеровку основной и чинят её так, как шамотный кирпич чинит кислую.");
            add("block.tfcpp.retort", "Реторта");
            add("block.tfcpp.retort_condenser", "Конденсатор реторты");
            add("tfcpp.retort.status", "%1$s °C, загрузка: %2$s, углерод: %3$s, %4$s мБ: %5$s");
            add("tfcpp.retort.status.empty_tank", "пока ничего не сконденсировалось");
            add("tfcpp.retort.no_condenser", "Рядом с ретортой нет конденсатора: пары уйдут в помещение");

            add("death.attack.tfcpp.blast", "%1$s попал под взрыв газа");
            add("block.tfcpp.davy_lamp", "Лампа Дэви");
            add("block.tfcpp.davy_lamp.filled", "Лампа Дэви");
            add("item.tfcpp.wire_gauze", "Медная проволочная сетка");
            add("item.tfcpp.animal_cage", "Клетка для животного");
            add("item.tfcpp.sentinel_cage", "Сторожевое животное в клетке");
            add("block.tfcpp.settling_baffle", "Пылеосадительная перегородка");
            add("item.tfcpp.powder.arsenic_trioxide", "Белый мышьяк");
            add("item.tfcpp.powder.antimony_trioxide", "Триоксид сурьмы");
            add("item.tfcpp.powder.zinc_oxide", "Оксид цинка");
            add("tfcpp.davy_lamp.steady", "Пламя горит ровно и жёлто");
            add("tfcpp.davy_lamp.dim", "Пламя низкое и тусклое: в воздухе мало кислорода");
            add("tfcpp.davy_lamp.pale_cap", "Над пламенем появился едва заметный бледный ореол");
            add("tfcpp.davy_lamp.blue_cap", "Над пламенем стоит голубой ореол");
            add("tfcpp.davy_lamp.tall_cap", "Высокий голубой ореол тянется вверх по сетке: скоро пора уходить");
            add("tfcpp.davy_lamp.filled", "Пламя заполнило всю сетку: воздух взрывоопасен, уходите немедленно");
            add("tfcpp.davy_lamp.out", "Пламя погасло: в этом воздухе огонь не горит, а вскоре не выживет и человек");
            add("tfcpp.sentinel.uneasy", "Животное в клетке беспокоится и часто дышит");
            add("tfcpp.sentinel.collapsed", "Животное в клетке упало: уходите, воздух отравлен");
            add("tfcpp.sentinel.dead", "Животное в клетке погибло");
            add("tfcpp.sentinel.starved", "Животное в клетке умерло от голода");
            add("tfcpp.sentinel.state.lively", "Бодрое");
            add("tfcpp.sentinel.state.uneasy", "Беспокоится");
            add("tfcpp.sentinel.state.collapsed", "Лежит без сил");
            add("tfcpp.settling_baffle.nothing", "На пластинах слишком мало пыли, соскрести нечего");
            add("tfcpp.stone_dust.sample", "Проба с пола здесь: %s%% пыли не горит (нужно не менее %s%%)");
            add("tfcpp.jei.retort", "Реторта");
            add("tfcpp.jei.retort.temperature", "%s °C");
            add("tfcpp.jei.retort.carbon", "Углерод: %s моль");
            add("tfcpp.jei.retort.condenser", "Нужен конденсатор рядом");
            add("tfcpp.info.davy_lamp", "Лампа, сетка которой не даёт пламени поджечь окружающий воздух. В руке и с маслом показывает рудничный газ голубым ореолом над пламенем и гаснет, когда не хватает кислорода. Угарный газ она не показывает, а сетка с горящим внутри газом за несколько минут раскаляется настолько, что поджигает газ снаружи.");
            add("tfcpp.info.sentinel_cage", "Правым кликом пустой клеткой поймайте крысу или перепела. Животное набирает угарный газ в двадцать раз быстрее человека и падает задолго до того, как вы что-то почувствуете. Кормите его зерном из другой руки хотя бы раз в три дня.");
            add("tfcpp.info.settling_baffle", "Ставятся в закрытой камере, через которую идут печные газы. Пыль мышьяка, сурьмы и оксида цинка оседает на пластинах, её соскребают правым кликом пустой рукой. При этом часть пыли снова поднимается в воздух, так что дышать ею нельзя.");
            add("tfcpp.info.condensation_tower", "Ставится в трубу, по которой идут печные газы. Пока газ холоднее 250 °C, оксиды мышьяка, сурьмы, цинка и свинца оседают на стенках пылью, а при газе холоднее 100 °C стекает жидкая ртуть. Диоксид серы проходит дальше. Пыль соскребают пустой рукой с приседанием, при этом часть её поднимается в воздух, ртуть сливают в ёмкость. Несколько башен подряд ловят пропорционально больше.");
            add("block.tfcpp.mine_fan", "Шахтный вентилятор");
            add("block.tfcpp.pipe.wood", "Деревянная труба");
            add("block.tfcpp.pipe.ceramic", "Керамическая труба");
            add("block.tfcpp.pipe.copper", "Медная труба");
            add("block.tfcpp.pipe.lead", "Свинцовая труба");
            add("block.tfcpp.pipe.wrought_iron", "Труба из сварочного железа");
            add("block.tfcpp.pipe.steel", "Стальная труба");
            add("block.tfcpp.pipe_valve", "Задвижка");
            add("block.tfcpp.pipe_vent", "Трубный выпуск");
            add("block.tfcpp.pipe_tap", "Кран");
            add("block.tfcpp.safety_valve", "Предохранительный клапан");
            add("block.tfcpp.mercury_manometer", "Ртутный манометр");
            add("block.tfcpp.pressure_vessel", "Сосуд под давлением");
            add("block.tfcpp.piston_pump", "Поршневой насос");
            add("tfcpp.pipe.opened", "Открыто");
            add("tfcpp.pipe.closed", "Закрыто");
            add("tfcpp.manometer.reading", "%s мм ртутного столба. Снаружи воздух даёт 760");
            add("tfcpp.info.pipes", "Участок труб это один объём с одним давлением, и он рвётся в самом слабом месте. Стыки из пеньки с салом держат не больше 10 атм. Коррозия утончает стенки: медь страдает от аммиака и ртути, железо от рассола и хлора, а дерево обугливается при нагреве. Если сломать трубу, выйдет всё содержимое участка, поэтому сначала закройте задвижку.");
            add("tfcpp.info.pressure_vessel", "Половина кубометра, соединяется с любой трубой рядом. Огонь снизу нагревает всю сеть. Давление газа растёт пропорционально абсолютной температуре, вода добавляет давление своего пара, а сосуд, полный жидкости без места для расширения, рвётся почти сразу. Поставьте предохранительный клапан.");
            add("tfcpp.info.piston_pump", "Вал подводится сбоку. Насос берёт сзади, из трубы, бочки, стоячей воды или воздуха, и подаёт в трубу спереди. Каждый оборот вытесняет один и тот же объём, а вал оплачивает разность давлений.");
            add("tfcpp.info.safety_valve", "Открывается при 8 атм сверх наружного давления и садится обратно при 7, с громким шипением. Удержать его закрытым нельзя.");
            add("tfcpp.info.manometer", "Правый клик показывает давление. При 3 атм сверх наружного ртуть выбрасывает из трубки прямо в помещение.");
            add("tfcpp.info.mine_fan", "Приводится валом с задней стороны, выдувает вперёд, а воздух тянет через остальные стороны по ведущим к нему выработкам. По узкому штреку воздух идёт быстро, а через большую полость почти незаметно. Чем быстрее вентилятор вращается, тем сильнее он нагружает вал.");
            add("tfcpp.info.shaft_load", "Каждая машина на валу отбирает момент у источника. Чем больше нагрузка, тем медленнее вал, а сверх предела источник останавливается. Водяное колесо даёт наибольшую мощность на половине свободной скорости. Насос нагружает вал тем сильнее, чем выше поднимает воду. Вода больше не размножается сама, а насос перемещает её от входа к выходу.");
            add("tfcpp.info.coal", "Угольные пласты содержат метан, и чем глубже, тем больше, а порода рядом с ними может держать газ под давлением. При добыче он выходит наружу. Пламя поджигает воздух с 5–15% метана, а после взрыва остаётся смесь с большим количеством угарного газа.");

            add("fluid_type.tfcpp.coal_tar", "Каменноугольная смола");
            add("fluid_type.tfcpp.wood_tar", "Древесный дёготь");
            add("fluid_type.tfcpp.ammonia_liquor", "Надсмольная аммиачная вода");
            add("fluid_type.tfcpp.pyroligneous_liquor", "Подсмольная вода");
            add("fluid_type.tfcpp.chamber_acid", "Камерная кислота");
            add("item.tfcpp.coke", "Кокс");
            add("item.tfcpp.lignite_char", "Буроугольный полукокс");
            add("block.tfcpp.coke_oven", "Коксовая печь");
            add("block.tfcpp.gas_burner", "Газовая горелка");
            add("block.tfcpp.lead_chamber", "Свинцовая камера");
            add("block.tfcpp.sulfur_burner", "Серная горелка");
            add("tfcpp.lead_chamber.status", "Газ %1$s °C. Кислоты в баке: %2$s мБ. В газе: диоксид серы %3$s%%, кислород %4$s%%, окислы азота %5$s%%. Воды: %6$s л");
            add("tfcpp.lead_chamber.no_network", "Не соединена с трубой: газ через неё не идёт");
            add("tfcpp.lead_chamber.acid", "Кислоты в баке: %1$s мБ");
            add("tfcpp.lead_chamber.no_water", "В сети нет воды: кислота не образуется");
            add("tfcpp.lead_chamber.oxides_low", "Окислов азота меньше %1$s%%: реакция идёт медленно, пока селитра их не даст");
            add("tfcpp.lead_chamber.oxygen_low", "Кислорода меньше %1$s%%: реакция идёт медленно");
            add("tfcpp.lead_chamber.empty", "В баке нет кислоты");
            add("tfcpp.lead_chamber.refused", "Эта ёмкость не принимает камерную кислоту");
            add("tfcpp.lead_chamber.saltpeter", "Селитра уходит в газ окислами азота");
            add("tfcpp.lead_chamber.saltpeter_no_network", "Не соединена с трубой: окислам некуда деться");
            add("tfcpp.sulfur_burner.status", "Серы в горелке: %1$s из %2$s порошков");
            add("tfcpp.sulfur_burner.no_network", "Не соединена с трубой: без сети горелка не горит");
            add("tfcpp.sulfur_burner.full", "Горелка полна серы");
            add("tfcpp.sulfur_burner.no_air", "Кислорода меньше %1$s%%: сера тлеет и гаснет, не горя");
            add("tfcpp.coke_oven.status", "%1$s °C, загрузка: %2$s, готово %3$s%%, смола: %4$s мБ, вода: %5$s мБ");
            add("tfcpp.coke_oven.status.empty", "пусто");
            add("tfcpp.coke_oven.too_cold", "Ниже %1$s °C ничего не происходит");
            add("tfcpp.coke_oven.tanks_full", "Бак полон или в нём другая жидкость: партия ждёт, пока его опорожнят");
            add("tfcpp.coke_oven.ready", "Можно выгружать: %1$s × %2$s (присядьте и кликните пустой рукой)");
            add("tfcpp.coke_oven.no_condenser", "Рядом с печью нет конденсатора: смола и вода уйдут паром");
            add("tfcpp.coke_oven.locked", "Загрузка уже начала меняться, догрузить нельзя");
            add("tfcpp.coke_oven.take_first", "Сначала выгрузите готовую партию");
            add("tfcpp.coke_oven.one_kind", "В печь грузят только один вид сырья за раз");
            add("tfcpp.coke_oven.full", "Печь полна: не больше 64 кусков");
            add("tfcpp.coke_oven.loaded", "В печи: %1$s × %2$s");
            add("tfcpp.coke_oven.nothing", "Выгружать нечего");
            add("tfcpp.gas_burner.shut", "Сначала откройте газ");
            add("block.tfcpp.cupel_hearth", "Купеляционный под");
            add("tfcpp.cupel_hearth.status", "%1$s °C, свинца: %2$s мБ, серебра: %3$s мБ, золы хватит ещё на %4$s мБ свинца");
            add("tfcpp.cupel_hearth.needs_ash", "Под полон глёта: положите костяную золу, иначе дутьё остановится");
            add("tfcpp.cupel_hearth.too_cold", "Глёт остаётся жидким только выше %1$s °C, ниже дутьё ничего не делает");
            add("tfcpp.cupel_hearth.poor_air", "Над подом слишком мало кислорода для дутья");
            add("tfcpp.cupel_hearth.plain_lead", "В простом свинце нет серебра для выделения: купелируют только веркблей");
            add("tfcpp.cupel_hearth.wrong_fluid", "Сюда льют только веркблей");
            add("tfcpp.cupel_hearth.full", "Под полон свинца");
            add("tfcpp.cupel_hearth.lead_left", "Серебро выпускают, когда весь свинец окислится");
            add("tfcpp.cupel_hearth.no_silver", "Серебра для выпуска пока нет");
            add("tfcpp.cupel_hearth.blick", "Блик: последний глёт ушёл, и серебро блеснуло");
            add("block.tfcpp.condensation_tower", "Конденсационная башня");
            add("tfcpp.condensation_tower.status", "Газ %1$s °C. Пыль на стенках, в порошках: мышьяк %2$s, сурьма %3$s, цинк %4$s, свинец %5$s. Ртуть: %6$s мБ");
            add("tfcpp.condensation_tower.nothing", "На стенках слишком мало пыли, соскрести нечего");
            add("tfcpp.condensation_tower.no_network", "Не соединена с трубой: газ через неё не идёт");
            add("tfcpp.condensation_tower.too_hot", "Газ при %1$s °C слишком горяч: пыль оседает только ниже %2$s °C, ртуть только ниже %3$s °C");
            add("tfcpp.condensation_tower.tank_full", "Бак ртути полон, остальная остаётся в газе");
            add("fluid_type.tfcpp.sulfuric_acid", "Купоросное масло");
            add("fluid_type.tfcpp.nitric_acid", "Крепкая водка");
            add("fluid_type.tfcpp.hydrochloric_acid", "Соляная кислота");
            add("fluid_type.tfcpp.wood_spirit", "Древесный спирт");
            add("item.tfcpp.powder.potassium_bisulfate", "Гидросульфат калия");
            add("item.tfcpp.powder.sodium_bisulfate", "Гидросульфат натрия");
            add("item.tfcpp.powder.superphosphate", "Суперфосфат");
            add("item.tfcpp.powder.ammonium_sulfate", "Сульфат аммония");
            add("item.tfcpp.borosilicate_glass_batch", "Шихта боросиликатного стекла");
            add("item.tfcpp.glass_carboy", "Стеклянная бутыль");
            add("item.tfcpp.glass_carboy.filled", "Бутыль: %s");
            add("block.tfcpp.glass_retort", "Стеклянная реторта");
            add("block.tfcpp.sand_bath", "Песчаная баня");
            add("tfcpp.sand_bath.status", "Песка %1$s из %2$s, %3$s °C, к сосуду сверху идёт %4$s °C");
            add("tfcpp.glass_retort.cracked", "Колба лопнула при %1$s °C, содержимое разлилось");
            add("tfcpp.glass_retort.status", "Стеклянная реторта: %1$s °C (трескается выше %2$s °C), %3$s");
            add("tfcpp.glass_retort.stage.idle", "простаивает");
            add("tfcpp.glass_retort.stage.running", "идёт, готово %1$s%%");
            add("tfcpp.glass_retort.contents", "Колба: %1$s. Вторая жидкость: %2$s. Порошок: %3$s");
            add("tfcpp.glass_retort.receiver", "Приёмник: %1$s. Осталось в колбе: %2$s. Вода приёмника: %3$s");
            add("tfcpp.glass_retort.tank", "%1$s л: %2$s");
            add("tfcpp.glass_retort.empty", "ничего");
            add("tfcpp.glass_retort.ready", "Можно выгружать: %1$s × %2$s (присядьте и кликните пустой рукой)");
            add("tfcpp.glass_retort.nothing", "Выгружать нечего");
            add("tfcpp.glass_retort.no_condenser", "Рядом с ретортой нет конденсатора: то, что выкипит, уйдёт в помещение");
            add("tfcpp.glass_retort.no_recipe", "Из этой загрузки ничего не получится: проверьте жидкости и порошок");
            add("tfcpp.glass_retort.dry", "В приёмнике нет %1$s л воды, чтобы поглотить хлороводород: он уйдёт в воздух");
            add("tfcpp.glass_retort.need_items", "Для партии нужно %1$s порошков в ячейке");
            add("tfcpp.glass_retort.need_liquid", "Для партии нужно %1$s л жидкости в колбе");
            add("tfcpp.glass_retort.need_reagent", "Для партии нужно %1$s л второй жидкости");
            add("tfcpp.glass_retort.too_cold", "Ниже %1$s °C ничего не перегоняется");
            add("tfcpp.glass_retort.too_hot", "Выше %1$s °C перегонка идёт неправильно: загрузка стоит");
            add("tfcpp.glass_retort.output_full", "Приёмник полон или в нём другая жидкость: партия ждёт, пока его опорожнят");
            add("tfcpp.glass_retort.refused", "Колба не принимает такую жидкость, или оба её бака заняты другими");
            add("tfcpp.glass_retort.nothing_to_tap", "Сливать в ёмкость нечего");
            add("tfcpp.glass_retort.one_kind", "В ячейку кладут один вид порошка за раз");
            add("tfcpp.glass_retort.full", "Ячейка полна");
            add("tfcpp.glass_retort.loaded", "В ячейке: %1$s × %2$s");
            add("tfcpp.info.glass_retort", "Ставится на песчаную баню, рядом нужен конденсатор реторты. Правый клик бутылью заливает жидкости (рецепт берёт одну или две) и воду для приёмника, а порошок кладётся в ячейку. Грейте только в рабочем диапазоне загрузки: ниже него ничего не перегоняется, а стекло выше 550 °C трескается, и всё содержимое разливается. Без конденсатора кислоты уходят парами, а без воды в приёмнике хлороводород уходит в воздух. Присядьте с пустой бутылью, чтобы слить и собственные жидкости колбы.");
            add("tfcpp.info.sand_bath", "Поддон с песком между огнём и стеклом. Наверх он отдаёт 20 °C плюс долю избытка тепла огня, одну часть на глубину плюс один: чем глубже песок, тем прохладнее сосуд. Пустая рука добавляет слой, с приседанием убирает.");
            add("tfcpp.info.glass_carboy", "Вмещает 25 л кислот, химических жидкостей мода, уксуса и пресной воды, и ничего больше. Правый клик по баку или реторте наливает или набирает.");
            add("tfcpp.info.borosilicate_glass_batch", "Песок, известь и бура. Загрузите в трубку стеклодува и работайте, как с любой шихтой: это стекло, которое держит жар и кислоту. Из неё делают стеклянную бутыль и стеклянную реторту.");
            add("tfcpp.info.coke_oven", "Закрытая камера из шамота, которую греют снизу: в ней без воздуха перегоняют садку до 64 кусков угля или дерева. Газ уходит в трубу над печью, а если трубы нет, в комнату; смола и подсмольная вода оседают в баках конденсатора рядом. Кокс вынимают, когда партия готова. Без конденсатора смола и вода уходят парами, а газ ядовит, поэтому ведите его в трубу.");
            add("tfcpp.info.gas_burner", "Горелка с ручным вентилем, которая сжигает газ подключённой к ней трубы под тем, что стоит сверху. Зажигается кресалом или огнивом: горящая горелка для любого газа в комнате — открытый огонь, поэтому не открывайте её рядом с метаном или коксовым газом.");
            add("tfcpp.info.cupel_hearth", "Очаг из шамотного кирпича на поду из костяной золы, над огнём. Сюда наливают веркблей и продувают воздухом: свинец окисляется в глёт, его сгребают, а в конце остаётся серебро для выпуска. Очаг держат горячим, чтобы глёт оставался жидким, и не дышите свинцовым дымом, который поднимает дутьё.");
            add("tfcpp.info.lead_chamber", "Камера из свинцового листа в трубе, где диоксид серы, воздух, пар и окислы азота образуют на стенках камерную кислоту. Свинец удерживает кислоту, пока она холодная, и бак можно слить в бутыль вручную. Окислы азота ядовиты и выходят через любой негерметичный стык.");
            add("tfcpp.info.sulfur_burner", "Корыто из огнеупорной глины в трубе, где порошковая сера горит в воздухе трубы и даёт диоксид серы, который забирает свинцовая камера. Загорается от горячего дымохода и нуждается в кислороде: при его нехватке сера только тлеет. Диоксид серы удушлив, поэтому корыто должно стоять в трубе, которая уносит газ.");
            add("tfcpp.info.marsh_apparatus", "Заряжается миллибакетом серной или камерной кислоты из бутыли в другой руке: это даёт восемь проб. Правый клик в воздухе проверяет воздух у глаз, а с пробой в другой руке (белый мышьяк, окись сурьмы, руда тетраэдрита или мышьяковистая медь) проверяет её. Зеркало говорит, что есть мышьяк или сурьма, но не какой именно; арсин, который аппарат выделяет, ядовит, поэтому пробуйте на открытом воздухе.");
            add("tfcpp.info.gold_leaf_indicator", "Лист золота на тонкой пластине, который пары ртути белят необратимо: от ярко-золотого через бледный к белому. Поставьте его туда, куда может дойти ртуть, и читайте стадию пустой рукой, без числа. Побелевший лист при поломке не даёт ничего, а чистый выпадает новым листом.");
            add("item.tfcpp.marsh_apparatus", "Аппарат Марша");
            add("tfcpp.marsh.tests", "Проб осталось: %1$s");
            add("tfcpp.marsh.none", "Зеркала нет: ни мышьяка, ни сурьмы в пробе нет");
            add("tfcpp.marsh.faint", "Слабое зеркало: это может быть мышьяк или сурьма, и только следы");
            add("tfcpp.marsh.strong", "Плотное зеркало: это может быть мышьяк или сурьма, и воздух здесь ядовит");
            add("tfcpp.marsh.no_acid", "В бутыли в другой руке нет серной или камерной кислоты: аппарат не зарядить");
            add("tfcpp.marsh.charged", "Заряжено одним миллибакетом кислоты: проб %1$s");
            add("block.tfcpp.gold_leaf_indicator", "Индикатор из золотого листа");
            add("tfcpp.gold_leaf.stage.0", "Лист ярко-золотой: ртуть до него не доходила");
            add("tfcpp.gold_leaf.stage.1", "Следы: лист побелел по краям от ртути в воздухе");
            add("tfcpp.gold_leaf.stage.2", "Подвержен: лист бледный, воздух здесь долго держал ртуть");
            add("tfcpp.gold_leaf.stage.3", "Сильно отравлен: лист белый, ртуть из него уже не выйдет");
        }
    }

    private DataGenerators() {}
}
