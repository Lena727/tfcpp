package tfcpp.core;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

import tfcpp.mechanics.WaterBodies;
import tfcpp.geology.OreSurvey;
import tfcpp.geology.Ores;
import tfcpp.metallurgy.Metallurgy;
import tfcpp.pneumatics.gas.GasAttachments;
import tfcpp.pneumatics.gas.GasEvents;
import tfcpp.pneumatics.instruments.Instruments;
import tfcpp.pneumatics.pipes.Pipes;

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
        Ores.load();
        Metallurgy.register(modBus);
        Instruments.register(modBus);
        Pipes.register(modBus);

        NeoForge.EVENT_BUS.register(GasEvents.class);
        NeoForge.EVENT_BUS.addListener(WaterBodies::onCreateFluidSource);
        NeoForge.EVENT_BUS.addListener(OreSurvey::onServerStarted);
    }
}
