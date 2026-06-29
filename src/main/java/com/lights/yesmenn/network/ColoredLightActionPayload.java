package com.lights.yesmenn.network;

import com.lights.yesmenn.LightsYesMenn;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ColoredLightActionPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int SET_RADIUS = 0;
    public static final int SET_COLOR = 1;
    public static final int SET_CONFIG = 2;
    public static final int SET_SPOTLIGHT_CONFIG = 3;
    public static final int SET_BRIGHTNESS = 4;
    public static final Type<ColoredLightActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "colored_light_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColoredLightActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(ColoredLightActionPayload::encode, ColoredLightActionPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(action);
        buffer.writeVarInt(value);
    }

    private static ColoredLightActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ColoredLightActionPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static int packConfig(int radius, int color, boolean redstoneInverted) {
        return ((radius - 1) & 0x1F) << 24
                | (redstoneInverted ? 1 << 29 : 0)
                | color & 0xFFFFFF;
    }

    public static int unpackRadius(int config) {
        return (config >>> 24 & 0x1F) + 1;
    }

    public static int unpackColor(int config) {
        return config & 0xFFFFFF;
    }

    public static boolean unpackRedstoneInverted(int config) {
        return (config & 1 << 29) != 0;
    }

    public static int packSpotlightConfig(int angle, int brightness, int yaw, int pitch) {
        int packedAngle = (Math.max(5, Math.min(90, angle)) - 5) & 0x7F;
        int packedBrightness = (Math.max(25, Math.min(800, brightness)) - 25) / 25 & 0x1F;
        int packedYaw = (Math.max(-90, Math.min(90, yaw)) + 90) & 0xFF;
        int packedPitch = (Math.max(-90, Math.min(90, pitch)) + 90) & 0xFF;
        return packedAngle
                | packedBrightness << 7
                | packedYaw << 12
                | packedPitch << 20;
    }

    public static int unpackSpotlightAngle(int config) {
        return (config & 0x7F) + 5;
    }

    public static int unpackSpotlightBrightness(int config) {
        return ((config >>> 7) & 0x1F) * 25 + 25;
    }

    public static int unpackSpotlightYaw(int config) {
        return ((config >>> 12) & 0xFF) - 90;
    }

    public static int unpackSpotlightPitch(int config) {
        return ((config >>> 20) & 0xFF) - 90;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
