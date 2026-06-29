package com.lights.yesmenn;

import com.lights.yesmenn.client.ColoredLightRenderConfig;
import com.lights.yesmenn.client.screen.ColoredLightsConfigScreen;
import com.lights.yesmenn.compat.veil.ColoredLightClientCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = LightsYesMenn.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = LightsYesMenn.MODID, value = Dist.CLIENT)
public class LightsYesMennClient {
    public LightsYesMennClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> new ColoredLightsConfigScreen(parent));
        ColoredLightRenderConfig.load();
        ColoredLightClientCompat.registerEventListeners();
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ColoredLightClientCompat.registerRenderers(event);
    }

    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        ColoredLightClientCompat.registerAdditionalModels(event);
    }

    @SubscribeEvent
    static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        ColoredLightClientCompat.registerBlockColors(event);
    }

    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        ColoredLightClientCompat.registerItemColors(event);
    }
}