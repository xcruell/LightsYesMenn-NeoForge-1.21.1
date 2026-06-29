package com.lights.yesmenn;

import com.lights.yesmenn.compat.OptionalModCompat;
import com.lights.yesmenn.compat.veil.ColoredLightCompatRegistry;
import com.lights.yesmenn.light.ColoredLightSpawnProtection;
import com.lights.yesmenn.network.ModNetworking;
import com.lights.yesmenn.registry.ModCreativeModeTabs;
import com.lights.yesmenn.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(LightsYesMenn.MODID)
public class LightsYesMenn {
    public static final String MODID = "lightsyesmenn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LightsYesMenn(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ModNetworking::register);

        ColoredLightCompatRegistry.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        if (!OptionalModCompat.yesMennAvailable()) {
            ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        }
        modEventBus.addListener(ModCreativeModeTabs::addCreative);

        NeoForge.EVENT_BUS.addListener(ColoredLightSpawnProtection::onSpawnPlacementCheck);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

}