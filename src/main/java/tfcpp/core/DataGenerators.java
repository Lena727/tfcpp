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
            add("tfcpp.gas.smell.sulfur_dioxide", "A sharp, choking smell of burning sulfur catches your throat");
            add("tfcpp.gas.smell.hydrogen_sulfide", "The air stinks of rotten eggs");
            add("tfcpp.gas.smell.ammonia", "A pungent smell of ammonia stings your eyes");
            add("tfcpp.gas.smell.chlorine", "A sharp bleach-like smell burns your nose");

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
            add("tfcpp.jei.retort", "Retort");
            add("tfcpp.jei.retort.temperature", "%s °C");
            add("tfcpp.jei.retort.carbon", "Carbon: %s mol");
            add("tfcpp.jei.retort.condenser", "Needs a condenser beside it");
            add("tfcpp.info.davy_lamp", "A lamp whose gauze keeps its flame from lighting the air around it. Carried in hand with oil in it, it shows firedamp as a blue cap above the flame, and goes out when oxygen runs low. It does not show carbon monoxide, and a gauze with gas burning in it for minutes gets hot enough to light the gas outside.");
            add("tfcpp.info.sentinel_cage", "Right-click a rat or a quail with an empty cage. The animal takes in carbon monoxide twenty times as fast as a person, so it collapses long before you feel anything. Feed it grain from the other hand at least every three days.");
            add("tfcpp.info.settling_baffle", "Place these in a closed chamber that furnace fumes pass through. Arsenic, antimony and zinc oxide dust settles on the plates, and is scraped off by right-clicking with an empty hand. Scraping stirs up some of the dust, so do not breathe it.");
            add("block.tfcpp.mine_fan", "Mine Fan");
            add("block.tfcpp.pipe.wood", "Wooden Pipe");
            add("block.tfcpp.pipe.ceramic", "Fired Clay Pipe");
            add("block.tfcpp.pipe.copper", "Copper Pipe");
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
            add("item.tfcpp.coke", "Coke");
            add("item.tfcpp.lignite_char", "Lignite Char");
            add("block.tfcpp.coke_oven", "Coke Oven");
            add("block.tfcpp.gas_burner", "Gas Burner");
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
            add("tfcpp.gas.smell.sulfur_dioxide", "Резкий удушливый запах горелой серы перехватывает горло");
            add("tfcpp.gas.smell.hydrogen_sulfide", "В воздухе стоит запах тухлых яиц");
            add("tfcpp.gas.smell.ammonia", "Едкий запах нашатыря щиплет глаза");
            add("tfcpp.gas.smell.chlorine", "Резкий запах хлорки обжигает нос");

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
            add("tfcpp.jei.retort", "Реторта");
            add("tfcpp.jei.retort.temperature", "%s °C");
            add("tfcpp.jei.retort.carbon", "Углерод: %s моль");
            add("tfcpp.jei.retort.condenser", "Нужен конденсатор рядом");
            add("tfcpp.info.davy_lamp", "Лампа, сетка которой не даёт пламени поджечь окружающий воздух. В руке и с маслом показывает рудничный газ голубым ореолом над пламенем и гаснет, когда не хватает кислорода. Угарный газ она не показывает, а сетка с горящим внутри газом за несколько минут раскаляется настолько, что поджигает газ снаружи.");
            add("tfcpp.info.sentinel_cage", "Правым кликом пустой клеткой поймайте крысу или перепела. Животное набирает угарный газ в двадцать раз быстрее человека и падает задолго до того, как вы что-то почувствуете. Кормите его зерном из другой руки хотя бы раз в три дня.");
            add("tfcpp.info.settling_baffle", "Ставятся в закрытой камере, через которую идут печные газы. Пыль мышьяка, сурьмы и оксида цинка оседает на пластинах, её соскребают правым кликом пустой рукой. При этом часть пыли снова поднимается в воздух, так что дышать ею нельзя.");
            add("block.tfcpp.mine_fan", "Шахтный вентилятор");
            add("block.tfcpp.pipe.wood", "Деревянная труба");
            add("block.tfcpp.pipe.ceramic", "Керамическая труба");
            add("block.tfcpp.pipe.copper", "Медная труба");
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
            add("item.tfcpp.coke", "Кокс");
            add("item.tfcpp.lignite_char", "Буроугольный полукокс");
            add("block.tfcpp.coke_oven", "Коксовая печь");
            add("block.tfcpp.gas_burner", "Газовая горелка");
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
        }
    }

    private DataGenerators() {}
}
