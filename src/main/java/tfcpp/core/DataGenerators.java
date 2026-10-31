package tfcpp.core;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
        event.getGenerator().addProvider(event.includeClient(), new MetallurgyAssets.Models(output, event.getExistingFileHelper()));
        event.getGenerator().addProvider(event.includeClient(), new MetallurgyAssets.BlockStates(output, event.getExistingFileHelper()));

        event.getGenerator().addProvider(event.includeClient(), new English(output));
        event.getGenerator().addProvider(event.includeClient(), new Russian(output));
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
            add("tfcpp.info.coal", "Coal seams hold methane, more of it the deeper they lie, and some rock next to them holds gas under pressure. Mining lets it out. A flame lights air holding 5% to 15% of it, and the blast is followed by afterdamp full of carbon monoxide.");
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
            add("tfcpp.info.coal", "Угольные пласты содержат метан, и чем глубже, тем больше, а порода рядом с ними может держать газ под давлением. При добыче он выходит наружу. Пламя поджигает воздух с 5–15% метана, а после взрыва остаётся смесь с большим количеством угарного газа.");
        }
    }

    private DataGenerators() {}
}
