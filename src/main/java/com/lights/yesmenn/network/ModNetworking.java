package com.lights.yesmenn.network;

import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.lights.yesmenn.item.ColorfulFlashlightItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Version 2: open-screen payload and flashlight settings carry flicker / glow / spill / fringe.
        // Version 3: SET_CONFIG packs the radius in 7 bits (up to 128).
        // Version 4: hanging lamp warm-up time (SET_WARMUP, open-screen payload).
        // Version 5: hanging lamp warm-up color (SET_WARMUP_TINT, open-screen payload).
        PayloadRegistrar registrar = event.registrar("5");
        registrar.playToServer(ColoredLightActionPayload.TYPE, ColoredLightActionPayload.STREAM_CODEC, ModNetworking::handleColoredLightAction);
        registrar.playToServer(ColoredLightSpotlightPayload.TYPE, ColoredLightSpotlightPayload.STREAM_CODEC, ModNetworking::handleColoredLightSpotlight);
        registrar.playToServer(FlashlightConfigPayload.TYPE, FlashlightConfigPayload.STREAM_CODEC, ModNetworking::handleFlashlightConfig);
        registrar.playToClient(OpenColoredLightScreenPayload.TYPE, OpenColoredLightScreenPayload.STREAM_CODEC, ModNetworking::handleOpenColoredLightScreen);
    }

    private static void handleColoredLightAction(ColoredLightActionPayload payload,
                                                  net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !canEdit(player, payload.pos())
                || !(player.level().getBlockEntity(payload.pos()) instanceof ColoredLightBlockEntity light)) {
            return;
        }

        if (payload.action() == ColoredLightActionPayload.SET_RADIUS) {
            light.setRadius(payload.value());
        } else if (payload.action() == ColoredLightActionPayload.SET_COLOR) {
            light.setColor(payload.value());
        } else if (payload.action() == ColoredLightActionPayload.SET_CONFIG) {
            light.setConfig(
                    ColoredLightActionPayload.unpackRadius(payload.value()),
                    ColoredLightActionPayload.unpackColor(payload.value()),
                    ColoredLightActionPayload.unpackRedstoneInverted(payload.value()));
        } else if (payload.action() == ColoredLightActionPayload.SET_BRIGHTNESS) {
            light.setBrightness(payload.value());
        } else if (payload.action() == ColoredLightActionPayload.SET_FLICKER) {
            light.setFlicker(payload.value());
        } else if (payload.action() == ColoredLightActionPayload.SET_WARMUP_TINT) {
            light.setWarmupTint(payload.value());
        } else if (payload.action() == ColoredLightActionPayload.SET_WARMUP) {
            light.setWarmup(payload.value());
        } else if (payload.action() == ColoredLightActionPayload.SET_GLOW) {
            light.setGlow(payload.value());
        } else if (payload.action() == ColoredLightActionPayload.SET_SPOTLIGHT_CONFIG) {
            light.setSpotlightConfig(
                    ColoredLightActionPayload.unpackSpotlightAngle(payload.value()),
                    ColoredLightActionPayload.unpackSpotlightBrightness(payload.value()),
                    ColoredLightActionPayload.unpackSpotlightYaw(payload.value()),
                    ColoredLightActionPayload.unpackSpotlightPitch(payload.value()));
        }
    }

    private static void handleColoredLightSpotlight(ColoredLightSpotlightPayload payload,
                                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !canEdit(player, payload.pos())
                || !(player.level().getBlockEntity(payload.pos()) instanceof ColoredLightBlockEntity light)) {
            return;
        }

        light.setSpotlightConfig(
                payload.angle(),
                payload.brightness(),
                payload.yaw(),
                payload.pitch(),
                payload.size(),
                payload.softness(),
                payload.focus());
    }

    private static void handleFlashlightConfig(FlashlightConfigPayload payload,
                                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(payload.hand());
        if (stack.getItem() instanceof ColorfulFlashlightItem) {
            payload.settings().write(stack);
        }
    }

    private static boolean canEdit(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getCenter()) <= 64.0D
                && player.mayBuild()
                && player.level().isLoaded(pos)
                && player.level().mayInteract(player, pos);
    }

    private static void handleOpenColoredLightScreen(OpenColoredLightScreenPayload payload,
                                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

        try {
            Class<?> hooks = Class.forName("com.lights.yesmenn.client.ColoredLightClientHooks");
            hooks.getMethod("openScreen", OpenColoredLightScreenPayload.class).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open Colored Light screen.", exception);
        }
    }
}