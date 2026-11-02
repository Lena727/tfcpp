package tfcpp.compat.jei;

import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import tfcpp.core.TFCpp;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.metallurgy.RetortRecipe;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.pipes.Pipes;

/** Only loaded by JEI itself, so nothing here is touched when JEI is absent. */
@JeiPlugin
public final class TFCppJeiPlugin implements IModPlugin
{
    private static final RecipeType<RetortRecipe> RETORT = RecipeType.create(TFCpp.MOD_ID, "retort", RetortRecipe.class);

    @Override
    public ResourceLocation getPluginUid()
    {
        return TFCpp.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration)
    {
        registration.addRecipeCategories(new RetortCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration)
    {
        final List<RetortRecipe> recipes = Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(Metallurgy.RETORT_RECIPE.get()).stream().map(RecipeHolder::value).toList();
        registration.addRecipes(RETORT, recipes);

        info(registration, new ItemStack(Instruments.DAVY_LAMP_ITEM.get()), "tfcpp.info.davy_lamp");
        info(registration, new ItemStack(Instruments.ANIMAL_CAGE.get()), "tfcpp.info.sentinel_cage");
        info(registration, new ItemStack(Instruments.SENTINEL_CAGE.get()), "tfcpp.info.sentinel_cage");
        info(registration, new ItemStack(Instruments.SETTLING_BAFFLE_ITEM.get()), "tfcpp.info.settling_baffle");
        info(registration, new ItemStack(Instruments.MINE_FAN_ITEM.get()), "tfcpp.info.mine_fan");
        info(registration, new ItemStack(Metallurgy.CONVERTER_ITEM.get()), "tfcpp.info.converter");
        info(registration, new ItemStack(Metallurgy.CONDENSATION_TOWER_ITEM.get()), "tfcpp.info.condensation_tower");
        info(registration, new ItemStack(Metallurgy.IRON_SLAG.get()), "tfcpp.info.iron_slag");
        info(registration, new ItemStack(Metallurgy.PHOSPHORIC_IRON_SLAG.get()), "tfcpp.info.phosphoric_iron_slag");
        info(registration, new ItemStack(Metallurgy.THOMAS_SLAG.get()), "tfcpp.info.thomas_slag");
        info(registration, new ItemStack(Metallurgy.BASIC_BRICK.get()), "tfcpp.info.basic_brick");
        Pipes.PIPE_ITEMS.values().forEach(pipe -> info(registration, new ItemStack(pipe.get()), "tfcpp.info.pipes"));
        info(registration, new ItemStack(Pipes.PRESSURE_VESSEL.get()), "tfcpp.info.pressure_vessel");
        info(registration, new ItemStack(Pipes.PISTON_PUMP.get()), "tfcpp.info.piston_pump");
        info(registration, new ItemStack(Pipes.SAFETY_VALVE.get()), "tfcpp.info.safety_valve");
        info(registration, new ItemStack(Pipes.MANOMETER.get()), "tfcpp.info.manometer");
        info(registration, new ItemStack(TFCBlocks.STEEL_PUMP.get()), "tfcpp.info.shaft_load");
        info(registration, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/bituminous_coal"))), "tfcpp.info.coal");
        info(registration, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("tfc", "ore/lignite"))), "tfcpp.info.coal");
    }

    private static void info(IRecipeRegistration registration, ItemStack stack, String key)
    {
        registration.addIngredientInfo(stack, VanillaTypes.ITEM_STACK, Component.translatable(key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration)
    {
        registration.addRecipeCatalyst(new ItemStack(Metallurgy.RETORT_ITEM.get()), RETORT);
        registration.addRecipeCatalyst(new ItemStack(Metallurgy.RETORT_CONDENSER_ITEM.get()), RETORT);
    }

    private static final class RetortCategory extends AbstractRecipeCategory<RetortRecipe>
    {
        RetortCategory(IGuiHelper helper)
        {
            super(RETORT, Component.translatable("tfcpp.jei.retort"), helper.createDrawableItemStack(new ItemStack(Metallurgy.RETORT_ITEM.get())), 150, 60);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RetortRecipe recipe, IFocusGroup focuses)
        {
            builder.addSlot(RecipeIngredientRole.INPUT, 4, 4).addIngredients(recipe.input());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 100, 4).addFluidStack(recipe.result().getFluid(), recipe.result().getAmount());
        }

        @Override
        public void draw(RetortRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY)
        {
            final Minecraft minecraft = Minecraft.getInstance();
            graphics.drawString(minecraft.font, Component.translatable("tfcpp.jei.retort.temperature", Math.round(recipe.temperature())), 30, 8, 0x404040, false);
            if (recipe.carbon() > 0)
            {
                graphics.drawString(minecraft.font, Component.translatable("tfcpp.jei.retort.carbon", String.format("%.2f", recipe.carbon())), 4, 28, 0x404040, false);
            }
            graphics.drawString(minecraft.font, Component.translatable("tfcpp.jei.retort.condenser"), 4, 44, 0x404040, false);
        }
    }
}
