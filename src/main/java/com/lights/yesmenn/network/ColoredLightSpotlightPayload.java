package com.lights.yesmenn.network;

import com.lights.yesmenn.LightsYesMenn;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ColoredLightSpotlightPayload(
        BlockPos pos,
        int angle,
        int brightness,
        int yaw,
        int pitch,
        int size,
        int softness,
        int focus) implements CustomPacketPayload {
    public static final Type<ColoredLightSpotlightPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "colored_light_spotlight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColoredLightSpotlightPayload> STREAM_CODEC =
            StreamCodec.ofMember(ColoredLightSpotlightPayload::encode, ColoredLightSpotlightPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(angle);
        buffer.writeVarInt(brightness);
        buffer.writeVarInt(yaw);
        buffer.writeVarInt(pitch);
        buffer.writeVarInt(size);
        buffer.writeVarInt(softness);
        buffer.writeVarInt(focus);
    }

    private static ColoredLightSpotlightPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ColoredLightSpotlightPayload(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
