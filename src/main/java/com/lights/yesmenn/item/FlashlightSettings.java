package com.lights.yesmenn.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Beam settings of a Colorful Flashlight, stored in the stack's custom data. Ranges match the spotlight GUI.
 * Read paths run every frame for every player holding a flashlight, so they use getUnsafe instead of copying the tag.
 */
@SuppressWarnings("deprecation")
public record FlashlightSettings(
        int range,
        int color,
        int brightness,
        int angle,
        int size,
        int softness,
        int focus,
        int spill,
        int glow,
        int fringe,
        int flicker) {
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 128;
    public static final FlashlightSettings DEFAULT = new FlashlightSettings(24, 0xFFFFFF, 300, 22, 10, 10, 100, 40, 30, 20, 0);

    private static final String ENABLED = "FlashlightOn";
    private static final String RANGE = "FlashlightRange";
    private static final String COLOR = "FlashlightColor";
    private static final String BRIGHTNESS = "FlashlightBrightness";
    private static final String ANGLE = "FlashlightAngle";
    private static final String SIZE = "FlashlightSize";
    private static final String SOFTNESS = "FlashlightSoftness";
    private static final String FOCUS = "FlashlightFocus";
    private static final String SPILL = "FlashlightSpill";
    private static final String GLOW = "FlashlightGlow";
    private static final String FRINGE = "FlashlightFringe";
    private static final String FLICKER = "FlashlightFlicker";

    public FlashlightSettings {
        range = clamp(range, MIN_RANGE, MAX_RANGE);
        color &= 0xFFFFFF;
        brightness = clamp(brightness, 25, 800);
        angle = clamp(angle, 5, 75);
        size = clamp(size, 2, 60);
        softness = clamp(softness, 1, 50);
        focus = clamp(focus, 25, 500);
        spill = clamp(spill, 0, 100);
        glow = clamp(glow, 0, 100);
        fringe = clamp(fringe, 0, 100);
        flicker = clamp(flicker, 0, 100);
    }

    public static FlashlightSettings read(ItemStack stack) {
        CustomData data = customData(stack);
        if (!data.contains(COLOR)) {
            return DEFAULT;
        }
        CompoundTag tag = data.getUnsafe();
        return new FlashlightSettings(
                tag.getInt(RANGE),
                tag.getInt(COLOR),
                tag.getInt(BRIGHTNESS),
                tag.getInt(ANGLE),
                tag.getInt(SIZE),
                tag.getInt(SOFTNESS),
                tag.getInt(FOCUS),
                // Flashlights configured before these settings existed fall back to the defaults.
                tag.contains(SPILL) ? tag.getInt(SPILL) : DEFAULT.spill(),
                tag.contains(GLOW) ? tag.getInt(GLOW) : DEFAULT.glow(),
                tag.contains(FRINGE) ? tag.getInt(FRINGE) : DEFAULT.fringe(),
                tag.contains(FLICKER) ? tag.getInt(FLICKER) : DEFAULT.flicker());
    }

    public void write(ItemStack stack) {
        if (customData(stack).contains(COLOR) && equals(read(stack))) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(RANGE, range);
            tag.putInt(COLOR, color);
            tag.putInt(BRIGHTNESS, brightness);
            tag.putInt(ANGLE, angle);
            tag.putInt(SIZE, size);
            tag.putInt(SOFTNESS, softness);
            tag.putInt(FOCUS, focus);
            tag.putInt(SPILL, spill);
            tag.putInt(GLOW, glow);
            tag.putInt(FRINGE, fringe);
            tag.putInt(FLICKER, flicker);
        });
    }

    public static boolean isEnabled(ItemStack stack) {
        return customData(stack).getUnsafe().getBoolean(ENABLED);
    }

    public static void setEnabled(ItemStack stack, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ENABLED, enabled));
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(range);
        buffer.writeVarInt(color);
        buffer.writeVarInt(brightness);
        buffer.writeVarInt(angle);
        buffer.writeVarInt(size);
        buffer.writeVarInt(softness);
        buffer.writeVarInt(focus);
        buffer.writeVarInt(spill);
        buffer.writeVarInt(glow);
        buffer.writeVarInt(fringe);
        buffer.writeVarInt(flicker);
    }

    public static FlashlightSettings decode(RegistryFriendlyByteBuf buffer) {
        return new FlashlightSettings(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    private static CustomData customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
