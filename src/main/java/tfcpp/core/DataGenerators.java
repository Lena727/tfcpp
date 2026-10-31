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
        }
    }

    private DataGenerators() {}
}
