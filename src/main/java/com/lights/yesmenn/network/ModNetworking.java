package com.lights.yesmenn.network;

import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ColoredLightActionPayload.TYPE, ColoredLightActionPayload.STREAM_CODEC, ModNetworking::handleColoredLightAction);
        registrar.playToServer(ColoredLightSpotlightPayload.TYPE, ColoredLightSpotlightPayload.STREAM_CODEC, ModNetworking::handleColoredLightSpotlight);
        registrar.playToClient(OpenColoredLightScreenPayload.TYPE, OpenColoredLightScreenPayload.STREAM_CODEC, ModNetworking::handleOpenColoredLightScreen);
    }

    private static void handleColoredLightAction(ColoredLightActionPayload payload,
                                                  net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || player.distanceToSqr(payload.pos().getCenter()) > 64.0D
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
                || player.distanceToSqr(payload.pos().getCenter()) > 64.0D
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