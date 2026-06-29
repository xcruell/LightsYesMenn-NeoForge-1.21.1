package com.lights.yesmenn.compat;

import net.neoforged.fml.ModList;

public final class OptionalModCompat {
    private OptionalModCompat() {
    }

    public static boolean coloredLightAvailable() {
        return ModList.get().isLoaded("veil");
    }

    public static boolean yesMennAvailable() {
        return ModList.get().isLoaded("yesmenn");
    }

    public static boolean vilcAvailable() {
        return ModList.get().isLoaded("veil_iris_lights");
    }

    public static boolean vilcShaderPassActive() {
        if (!vilcAvailable()) {
            return false;
        }
        try {
            Class<?> vilc = Class.forName("com.yesmenn.veilirislights.VeilIrisLights");
            Object active = vilc.getMethod("isShaderPackInUse").invoke(null);
            return active instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}