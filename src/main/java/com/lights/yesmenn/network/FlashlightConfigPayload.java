package com.lights.yesmenn.network;

import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.item.FlashlightSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record FlashlightConfigPayload(InteractionHand hand, FlashlightSettings settings) implements CustomPacketPayload {
    public static final Type<FlashlightConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "flashlight_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlashlightConfigPayload> STREAM_CODEC =
            StreamCodec.ofMember(FlashlightConfigPayload::encode, FlashlightConfigPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
        settings.encode(buffer);
    }

    private static FlashlightConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new FlashlightConfigPayload(buffer.readEnum(InteractionHand.class), FlashlightSettings.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
