package com.lights.yesmenn.compat.veil;

import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.lights.yesmenn.client.ColoredLightSpotlightRenderer;
import com.lights.yesmenn.client.VeilColoredLightManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ColoredLightClientCompat {
    private ColoredLightClientCompat() {
    }

    public static void registerEventListeners() {
        NeoForge.EVENT_BUS.addListener(VeilColoredLightManager::onClientTick);
        NeoForge.EVENT_BUS.addListener(VeilColoredLightManager::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(VeilColoredLightManager::onClientLoggingOut);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ColoredLightCompatRegistry.COLORED_LIGHT_ENTITY.get(),
                ColoredLightSpotlightRenderer::new);
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ColoredLightSpotlightRenderer.YAW_MODEL);
        event.register(ColoredLightSpotlightRenderer.BODY_MODEL);
        event.register(ColoredLightSpotlightRenderer.LENS_MODEL);
        event.register(ColoredLightSpotlightRenderer.BIG_YAW_MODEL);
        event.register(ColoredLightSpotlightRenderer.BIG_BODY_MODEL);
        event.register(ColoredLightSpotlightRenderer.BIG_LENS_MODEL);
        event.register(ColoredLightSpotlightRenderer.INVISIBLE_SPOTLIGHT_MODEL);
        event.register(ColoredLightSpotlightRenderer.INVISIBLE_POINTLIGHT_MODEL);
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null
                    && level.getBlockEntity(pos) instanceof ColoredLightBlockEntity light) {
                return light.getColor();
            }
            return ColoredLightBlockEntity.DEFAULT_COLOR;
        },
                ColoredLightCompatRegistry.COLORED_LIGHT.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_TUBE.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_PANEL.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_SPOTLIGHT.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_SPOTLIGHT_BIG.get(),
                ColoredLightCompatRegistry.INVISIBLE_COLORED_LIGHT_SPOTLIGHT.get(),
                ColoredLightCompatRegistry.INVISIBLE_COLORED_POINTLIGHT.get());
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            CustomData blockEntityData = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
            if (blockEntityData.copyTag().contains("Color")) {
                return blockEntityData.copyTag().getInt("Color") & 0xFFFFFF;
            }
            return ColoredLightBlockEntity.DEFAULT_COLOR;
        },
                ColoredLightCompatRegistry.COLORED_LIGHT_ITEM.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_TUBE_ITEM.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_PANEL_ITEM.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_SPOTLIGHT_ITEM.get(),
                ColoredLightCompatRegistry.COLORED_LIGHT_SPOTLIGHT_BIG_ITEM.get(),
                ColoredLightCompatRegistry.INVISIBLE_COLORED_LIGHT_SPOTLIGHT_ITEM.get(),
                ColoredLightCompatRegistry.INVISIBLE_COLORED_POINTLIGHT_ITEM.get());
    }
}