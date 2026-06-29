package com.lights.yesmenn.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lights.yesmenn.LightsYesMenn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ColoredLightRenderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("lightsyesmenn_colored_lights.json");

    public static final double DEFAULT_EXPOSURE = 1.0;
    public static final double DEFAULT_COLOR_STRENGTH = 1.0;
    public static final double DEFAULT_COLOR_SATURATION = 1.0;
    public static final double DEFAULT_NEUTRAL_LIFT = 0.0;
    public static final double MAX_EXPOSURE = 4.0;
    public static final double MAX_COLOR_STRENGTH = 8.0;

    private static ColoredLightRenderConfig instance = new ColoredLightRenderConfig();

    public double exposure = DEFAULT_EXPOSURE;
    public double colorStrength = DEFAULT_COLOR_STRENGTH;
    public double colorSaturation = DEFAULT_COLOR_SATURATION;
    public double neutralLift = DEFAULT_NEUTRAL_LIFT;

    private ColoredLightRenderConfig() {
    }

    public static ColoredLightRenderConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.isRegularFile(PATH)) {
            instance = new ColoredLightRenderConfig();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            ColoredLightRenderConfig loaded = GSON.fromJson(reader, ColoredLightRenderConfig.class);
            instance = loaded != null ? loaded : new ColoredLightRenderConfig();
            instance.clamp();
        } catch (Exception exception) {
            LightsYesMenn.LOGGER.warn("Failed to load {}, using defaults", PATH, exception);
            instance = new ColoredLightRenderConfig();
        }
    }

    public static void save() {
        instance.clamp();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException exception) {
            LightsYesMenn.LOGGER.error("Failed to save {}", PATH, exception);
        }
    }

    public void reset() {
        exposure = DEFAULT_EXPOSURE;
        colorStrength = DEFAULT_COLOR_STRENGTH;
        colorSaturation = DEFAULT_COLOR_SATURATION;
        neutralLift = DEFAULT_NEUTRAL_LIFT;
    }

    public void clamp() {
        exposure = clamp(exposure, 0.01, MAX_EXPOSURE);
        colorStrength = clamp(colorStrength, 0.0, MAX_COLOR_STRENGTH);
        colorSaturation = clamp(colorSaturation, 0.5, 2.0);
        neutralLift = clamp(neutralLift, 0.0, 2.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
