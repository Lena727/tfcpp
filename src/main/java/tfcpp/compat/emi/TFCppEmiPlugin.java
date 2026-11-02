package tfcpp.compat.emi;

import java.util.List;
import java.util.function.Supplier;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

import tfcpp.core.TFCpp;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.metallurgy.RetortRecipe;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.pipes.Pipes;

/** Only loaded by EMI itself, so nothing here is touched when EMI is absent. */
@EmiEntrypoint
public final class TFCppEmiPlugin implements EmiPlugin
{
    private static final EmiRecipeCategory RETORT = new EmiRecipeCategory(TFCpp.id("retort"), EmiStack.of(Metallurgy.RETORT_ITEM.get()));

    @Override
    public void register(EmiRegistry registry)
    {
        registry.addCategory(RETORT);
        registry.addWorkstation(RETORT, EmiStack.of(Metallurgy.RETORT_ITEM.get()));
        registry.addWorkstation(RETORT, EmiStack.of(Metallurgy.RETORT_CONDENSER_ITEM.get()));

        for (RecipeHolder<RetortRecipe> holder : registry.getRecipeManager().getAllRecipesFor(Metallurgy.RETORT_RECIPE.get()))
        {
            registry.addRecipe(new RetortEmiRecipe(holder.id(), holder.value()));
        }

        info(registry, "davy_lamp", "tfcpp.info.davy_lamp", Instruments.DAVY_LAMP_ITEM.get());
        info(registry, "sentinel_cage", "tfcpp.info.sentinel_cage", Instruments.ANIMAL_CAGE.get(), Instruments.SENTINEL_CAGE.get());
        info(registry, "settling_baffle", "tfcpp.info.settling_baffle", Instruments.SETTLING_BAFFLE_ITEM.get());
        info(registry, "mine_fan", "tfcpp.info.mine_fan", Instruments.MINE_FAN_ITEM.get());
        info(registry, "converter", "tfcpp.info.converter", Metallurgy.CONVERTER_ITEM.get());
        info(registry, "condensation_tower", "tfcpp.info.condensation_tower", Metallurgy.CONDENSATION_TOWER_ITEM.get());
        info(registry, "iron_slag", "tfcpp.info.iron_slag", Metallurgy.IRON_SLAG.get());
        info(registry, "phosphoric_iron_slag", "tfcpp.info.phosphoric_iron_slag", Metallurgy.PHOSPHORIC_IRON_SLAG.get());
        info(registry, "thomas_slag", "tfcpp.info.thomas_slag", Metallurgy.THOMAS_SLAG.get());
        info(registry, "basic_brick", "tfcpp.info.basic_brick", Metallurgy.BASIC_BRICK.get());
        info(registry, "pipes", "tfcpp.info.pipes", Pipes.PIPE_ITEMS.values().stream().map(Supplier::get).toArray(Item[]::new));
        info(registry, "pressure_vessel", "tfcpp.info.pressure_vessel", Pipes.PRESSURE_VESSEL.get().asItem());
        info(registry, "piston_pump", "tfcpp.info.piston_pump", Pipes.PISTON_PUMP.get().asItem());
        info(registry, "safety_valve", "tfcpp.info.safety_valve", Pipes.SAFETY_VALVE.get().asItem());
        info(registry, "manometer", "tfcpp.info.manometer", Pipes.MANOMETER.get().asItem());
        info(registry, "shaft_load", "tfcpp.info.shaft_load", TFCBlocks.STEEL_PUMP.get().asItem());
        info(registry, "coal", "tfcpp.info.coal", BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/bituminous_coal")), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/lignite")));
    }

    private static void info(EmiRegistry registry, String name, String key, Item... items)
    {
        final List<EmiIngredient> stacks = java.util.Arrays.stream(items).<EmiIngredient>map(EmiStack::of).toList();
        registry.addRecipe(new EmiInfoRecipe(stacks, List.of(Component.translatable(key)), TFCpp.id("/info/" + name)));
    }

    private static final class RetortEmiRecipe extends BasicEmiRecipe
    {
        private final RetortRecipe recipe;

        RetortEmiRecipe(ResourceLocation id, RetortRecipe recipe)
        {
            super(RETORT, id, 150, 60);
            this.recipe = recipe;
            inputs.add(EmiIngredient.of(recipe.input()));
            outputs.add(EmiStack.of(recipe.result().getFluid(), recipe.result().getAmount()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets)
        {
            widgets.addSlot(inputs.getFirst(), 3, 3);
            widgets.addSlot(outputs.getFirst(), 99, 3).recipeContext(this);
            widgets.addText(Component.translatable("tfcpp.jei.retort.temperature", Math.round(recipe.temperature())), 30, 8, 0x404040, false);
            if (recipe.carbon() > 0)
            {
                widgets.addText(Component.translatable("tfcpp.jei.retort.carbon", String.format("%.2f", recipe.carbon())), 4, 28, 0x404040, false);
            }
            widgets.addText(Component.translatable("tfcpp.jei.retort.condenser"), 4, 44, 0x404040, false);
        }
    }
}
