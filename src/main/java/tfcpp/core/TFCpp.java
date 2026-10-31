package tfcpp.core;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasEvents;

@Mod(TFCpp.MOD_ID)
public final class TFCpp
{
    public static final String MOD_ID = "tfcpp";

    public static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public TFCpp(IEventBus modBus, ModContainer container)
    {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        GasAttachments.TYPES.register(modBus);
        Metallurgy.register(modBus);

        NeoForge.EVENT_BUS.register(GasEvents.class);
    }
}
