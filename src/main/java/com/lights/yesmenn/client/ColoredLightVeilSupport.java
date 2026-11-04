package com.lights.yesmenn.client;

import com.lights.yesmenn.compat.OptionalModCompat;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Shared conversion from L?YM! light settings to Veil light data, used by placed lamps and the flashlight.
 */
final class ColoredLightVeilSupport {
    private ColoredLightVeilSupport() {
    }

    static float brightnessMultiplier(int brightness) {
        return Math.max(25, Math.min(800, brightness)) / 100.0F;
    }

    /** Light beam slider (0-100) to Veil's in-scattering strength; VILC renders it, plain Veil under Iris does not. */
    static float inscattering(int glow) {
        return Math.max(0, Math.min(100, glow)) / 100.0F;
    }

    /** Length of one flicker event slot; each slot independently may contain a short dip. */
    private static final double FLICKER_SLOT_SECONDS = 0.125;

    /**
     * Brightness multiplier of a flickering bulb: a wavering drift, a fast filament buzz and random short dips.
     * {@code flicker} (0-100) scales depth and dip frequency (at 100 roughly one dip per second, down to ~25 %
     * brightness). The modulation is strong on purpose: VILC compresses bright light softly, so small brightness
     * changes of a strong lamp would hardly be visible. Each light gets its own pattern from {@code seed}.
     */
    static float flickerFactor(int flicker, long seed, double seconds) {
        if (flicker <= 0) {
            return 1.0F;
        }
        double amount = Math.min(100, flicker) / 100.0;
        double t = seconds + (mix(seed) >>> 40) / (double) (1L << 24) * 1000.0;
        double drift = 0.5 * Math.sin(t * 3.1) + 0.3 * Math.sin(t * 8.7 + 1.7) + 0.2 * Math.sin(t * 17.3 + 4.2);
        double buzz = Math.sin(t * 61.0) * Math.sin(t * 43.0 + 0.9);

        // Random dips: every slot rolls a die; a hit dims smoothly in and out within the slot.
        long slot = (long) Math.floor(t / FLICKER_SLOT_SECONDS);
        double slotPosition = t / FLICKER_SLOT_SECONDS - slot;
        double roll = (mix(seed ^ slot * 0x632BE59BD9B4E019L) >>> 11) / (double) (1L << 53);
        double dipChance = 0.13 * amount * amount;
        double dip = 0.0;
        if (roll < dipChance) {
            double depth = 0.45 + 0.4 * (roll / dipChance);
            dip = depth * Math.sin(Math.PI * slotPosition);
        }

        double dimming = amount * (0.22 * (drift * 0.5 + 0.5) + 0.06 * (buzz * 0.5 + 0.5)) + amount * dip;
        return (float) Math.max(0.1, 1.0 - dimming);
    }

    private static long mix(long value) {
        long z = value * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** VILC-only extras: skip the first-person hand and a chromatic fringe (0-100) at the beam edge. */
    static void setLightExtras(LightData data, boolean skipFirstPerson, int chromaticFringe) {
        MethodHandle set = SpotShapeBridge.SET_EXTRAS;
        if (set == null) {
            return;
        }
        try {
            set.invokeExact(data, skipFirstPerson, Math.max(0, Math.min(100, chromaticFringe)) / 100.0F);
        } catch (Throwable ignored) {
        }
    }

    static void clearLightExtras(LightData data) {
        MethodHandle clear = SpotShapeBridge.CLEAR_EXTRAS;
        if (clear == null) {
            return;
        }
        try {
            clear.invokeExact(data);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Veil spot lights: size = cone half-angle, angle = width of the soft edge inside the cone (radians).
     * The softness slider (1-50) is a cone-edge width in cosine space, converted so Veil and VILC draw the same edge.
     */
    static void applySpotCone(SpotLightData data, int angleDegrees, int softness) {
        float cone = (float) Math.toRadians(angleDegrees);
        double cosInner = Math.min(1.0, Math.cos(cone) + softness / 100.0);
        float edgeWidth = (float) Math.max(0.001, cone - Math.acos(cosInner));
        data.setSize(cone);
        data.setAngle(edgeWidth);
    }

    static void setSpotShape(SpotLightData data, int focus, int lensSize) {
        MethodHandle set = SpotShapeBridge.SET;
        if (set == null) {
            // VILC is optional; plain Veil ignores focus and lens size.
            return;
        }
        try {
            set.invokeExact(data, focus / 100.0F, lensSize / 100.0F);
        } catch (Throwable ignored) {
        }
    }

    static void clearSpotShape(SpotLightData data) {
        MethodHandle clear = SpotShapeBridge.CLEAR;
        if (clear == null) {
            return;
        }
        try {
            clear.invokeExact(data);
        } catch (Throwable ignored) {
        }
    }

    /** Veil spot and area lights shine along their local +Z axis. */
    static Quaternionf orientationFromForward(Vec3 forward) {
        return new Quaternionf().rotationTo(
                new Vector3f((float) forward.x, (float) forward.y, (float) forward.z),
                new Vector3f(0.0F, 0.0F, 1.0F));
    }

    private static float linearChannel(int channel) {
        float srgb = (channel & 0xFF) / 255.0F;
        return srgb <= 0.04045F
                ? srgb / 12.92F
                : (float) Math.pow((srgb + 0.055F) / 1.055F, 2.4);
    }

    static float[] adjustedColor(int color, ColoredLightRenderConfig config) {
        float red = linearChannel(color >> 16);
        float green = linearChannel(color >> 8);
        float blue = linearChannel(color);
        float luminance = red * 0.2126F + green * 0.7152F + blue * 0.0722F;
        float saturation = config != null ? (float) config.colorSaturation : 1.0F;
        red = luminance + (red - luminance) * saturation;
        green = luminance + (green - luminance) * saturation;
        blue = luminance + (blue - luminance) * saturation;
        float strength = config != null ? (float) config.colorStrength : 1.0F;
        float neutral = config != null ? (float) config.neutralLift : 0.0F;
        return new float[]{
                Math.max(0.0F, red * strength + neutral),
                Math.max(0.0F, green * strength + neutral),
                Math.max(0.0F, blue * strength + neutral)
        };
    }

    // L?YM!'s color settings tune its own lamps and stack with VILC's global light-pass settings.
    // Defaults are neutral, so lamps look the same until the player changes them.
    static ColoredLightRenderConfig activeRenderConfig() {
        ColoredLightRenderConfig renderConfig = ColoredLightRenderConfig.get();
        renderConfig.clamp();
        return renderConfig;
    }

    static float exposure(ColoredLightRenderConfig renderConfig) {
        return renderConfig != null ? (float) renderConfig.exposure : 1.0F;
    }

    private static final class SpotShapeBridge {
        private static final MethodHandle SET = find("set",
                MethodType.methodType(void.class, SpotLightData.class, float.class, float.class));
        private static final MethodHandle CLEAR = find("clear",
                MethodType.methodType(void.class, SpotLightData.class));
        // Added in VILC 1.6.0; older versions simply ignore the extras.
        private static final MethodHandle SET_EXTRAS = find("setExtras",
                MethodType.methodType(void.class, LightData.class, boolean.class, float.class));
        private static final MethodHandle CLEAR_EXTRAS = find("clearExtras",
                MethodType.methodType(void.class, LightData.class));

        private static MethodHandle find(String name, MethodType type) {
            if (!OptionalModCompat.vilcAvailable()) {
                return null;
            }
            try {
                Class<?> registry = Class.forName("com.yesmenn.veilirislights.compat.light.AreaLightShapeRegistry");
                return MethodHandles.publicLookup().findStatic(registry, name, type);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
