package com.lights.yesmenn.registry;

import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.compat.veil.ColoredLightCompatRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeModeTabs {
    public static final ResourceKey<CreativeModeTab> YES_MENN_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "yes_menn"));
    public static final ResourceKey<CreativeModeTab> LEGACY_YES_MENN_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("yesmenn", "yes_menn"));

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LightsYesMenn.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> YES_MENN =
            CREATIVE_MODE_TABS.register("yes_menn", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lightsyesmenn"))
                    .icon(() -> new ItemStack(ColoredLightCompatRegistry.COLORED_LIGHT_ITEM.get()))
                    .displayItems((parameters, output) -> acceptLights(output))
                    .build());

    private ModCreativeModeTabs() {
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> key = event.getTabKey();
        if (key.equals(LEGACY_YES_MENN_KEY)) {
            acceptLights(event);
        }
    }

    private static void acceptLights(CreativeModeTab.Output output) {
        output.accept(ColoredLightCompatRegistry.COLORED_LIGHT_ITEM.get());
        output.accept(ColoredLightCompatRegistry.COLORED_LIGHT_TUBE_ITEM.get());
        output.accept(ColoredLightCompatRegistry.COLORED_LIGHT_PANEL_ITEM.get());
        output.accept(ColoredLightCompatRegistry.COLORED_LIGHT_SPOTLIGHT_ITEM.get());
        output.accept(ColoredLightCompatRegistry.INVISIBLE_COLORED_LIGHT_SPOTLIGHT_ITEM.get());
        output.accept(ModItems.LIGHT_CONFIGURATOR.get());
    }
}