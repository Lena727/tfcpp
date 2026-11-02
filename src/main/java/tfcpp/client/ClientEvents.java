package tfcpp.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import tfcpp.chemistry.Chemistry;
import tfcpp.core.TFCpp;
import tfcpp.metallurgy.Metallurgy;

@EventBusSubscriber(modid = TFCpp.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents
{
    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/lava_still");
    private static final ResourceLocation FLOWING = ResourceLocation.withDefaultNamespace("block/lava_flow");
    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation WATER_FLOWING = ResourceLocation.withDefaultNamespace("block/water_flow");

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event)
    {
        event.registerFluidType(fluid(STILL, FLOWING, 0xFFB5A27A), Metallurgy.ARSENICAL_COPPER_TYPE.get());
        event.registerFluidType(fluid(STILL, FLOWING, 0xFF6E3B2A), Metallurgy.IRON_OXIDE_TYPE.get());
        event.registerFluidType(fluid(STILL, FLOWING, 0xFF7A4A24), Metallurgy.PHOSPHORIC_IRON_OXIDE_TYPE.get());
        event.registerFluidType(fluid(STILL, FLOWING, 0xFFE0A070), Metallurgy.PHOSPHORIC_PIG_IRON_TYPE.get());
        event.registerFluidType(fluid(STILL, FLOWING, 0xFFE8D0B0), Metallurgy.COLD_SHORT_IRON_TYPE.get());
        event.registerFluidType(fluid(WATER_STILL, WATER_FLOWING, 0xFF8F939C), Metallurgy.LEAD_TYPE.get());
        event.registerFluidType(fluid(WATER_STILL, WATER_FLOWING, 0xFF7C828E), Metallurgy.WORK_LEAD_TYPE.get()); // A little darker than lead: the silver in it is a dull grey
        event.registerFluidType(fluid(WATER_STILL, WATER_FLOWING, 0xFFC9CDD2), Metallurgy.MERCURY_TYPE.get());
        Chemistry.LIQUIDS.forEach((liquid, chemical) -> event.registerFluidType(fluid(WATER_STILL, WATER_FLOWING, liquid.tint()), chemical.type.get()));
    }

    private static IClientFluidTypeExtensions fluid(ResourceLocation still, ResourceLocation flowing, int tint)
    {
        return new IClientFluidTypeExtensions()
        {
            @Override
            public ResourceLocation getStillTexture()
            {
                return still;
            }

            @Override
            public ResourceLocation getFlowingTexture()
            {
                return flowing;
            }

            @Override
            public int getTintColor()
            {
                return tint;
            }
        };
    }

    private ClientEvents() {}
}
