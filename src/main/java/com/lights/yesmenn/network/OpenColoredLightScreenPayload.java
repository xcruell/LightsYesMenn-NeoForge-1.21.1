package com.lights.yesmenn.network;

import com.lights.yesmenn.LightsYesMenn;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenColoredLightScreenPayload(
        BlockPos pos,
        int radius,
        int color,
        int brightness,
        boolean redstoneInverted,
        boolean spotlight,
        int spotlightAngle,
        int spotlightBrightness,
        int spotlightYaw,
        int spotlightPitch,
        int spotlightSize,
        int spotlightSoftness,
        int spotlightFocus) implements CustomPacketPayload {
    public static final Type<OpenColoredLightScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "open_colored_light_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenColoredLightScreenPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenColoredLightScreenPayload::encode, OpenColoredLightScreenPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(radius);
        buffer.writeVarInt(color);
        buffer.writeVarInt(brightness);
        buffer.writeBoolean(redstoneInverted);
        buffer.writeBoolean(spotlight);
        buffer.writeVarInt(spotlightAngle);
        buffer.writeVarInt(spotlightBrightness);
        buffer.writeVarInt(spotlightYaw);
        buffer.writeVarInt(spotlightPitch);
        buffer.writeVarInt(spotlightSize);
        buffer.writeVarInt(spotlightSoftness);
        buffer.writeVarInt(spotlightFocus);
    }

    private static OpenColoredLightScreenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenColoredLightScreenPayload(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
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
