package com.lights.yesmenn.client;

import com.lights.yesmenn.LightsYesMenn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ColoredLightPresetStore {
    public static final int SLOT_COUNT = 4;
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("yesmenn-colored-light-presets.txt");
    private static final List<Integer> PRESETS = new ArrayList<>(Collections.nCopies(SLOT_COUNT, -1));
    private static boolean loaded;

    private ColoredLightPresetStore() {
    }

    public static List<Integer> getPresets() {
        load();
        return List.copyOf(PRESETS);
    }

    public static void setPreset(int slot, int color) {
        load();
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        PRESETS.set(slot, color & 0xFFFFFF);
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (int slot = 0; slot < Math.min(SLOT_COUNT, lines.size()); slot++) {
                String value = lines.get(slot).trim();
                if (!value.isEmpty()) {
                    PRESETS.set(slot, Integer.parseInt(value, 16) & 0xFFFFFF);
                }
            }
        } catch (IOException | NumberFormatException exception) {
            LightsYesMenn.LOGGER.warn("Could not load Colored Light presets from {}.", FILE, exception);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            List<String> lines = PRESETS.stream()
                    .map(color -> color < 0 ? "" : String.format("%06X", color))
                    .toList();
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LightsYesMenn.LOGGER.warn("Could not save Colored Light presets to {}.", FILE, exception);
        }
    }
}
