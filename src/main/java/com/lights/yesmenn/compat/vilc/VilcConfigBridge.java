package com.lights.yesmenn.compat.vilc;

import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VilcConfigBridge {
    private VilcConfigBridge() {
    }

    public static boolean available() {
        return ModList.get().isLoaded("veil_iris_lights");
    }

    public static Object config() {
        try {
            Class<?> configClass = Class.forName("com.yesmenn.veilirislights.config.LightRenderConfig");
            return configClass.getMethod("get").invoke(null);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    public static void save() {
        try {
            Class.forName("com.yesmenn.veilirislights.config.LightRenderConfig")
                    .getMethod("save")
                    .invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static void resetColorSettings(Object config) {
        invoke(config, "reset");
    }

    public static String qualityLabel(Object config) {
        Object quality = get(config, "quality");
        if (quality == null) {
            return "unknown";
        }
        return quality.toString().toLowerCase();
    }

    public static void nextQualityPreset(Object config) {
        Object quality = get(config, "quality");
        if (quality == null) {
            return;
        }
        try {
            Object next = quality.getClass().getMethod("next").invoke(quality);
            invoke(config, "applyPreset", quality.getClass(), next);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static int getInt(Object config, String field) {
        Object value = get(config, field);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public static void setInt(Object config, String field, int value) {
        set(config, field, value);
        invoke(config, "markCustom");
    }

    public static boolean getBoolean(Object config, String field) {
        Object value = get(config, field);
        return value instanceof Boolean bool && bool;
    }

    public static void setBoolean(Object config, String field, boolean value) {
        set(config, field, value);
        invoke(config, "markCustom");
    }

    public static double getDouble(Object config, String field) {
        Object value = get(config, field);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    public static void setDouble(Object config, String field, double value) {
        set(config, field, value);
    }

    private static Object get(Object target, String field) {
        if (target == null) {
            return null;
        }
        try {
            Field reflectedField = target.getClass().getField(field);
            return reflectedField.get(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static void set(Object target, String field, Object value) {
        if (target == null) {
            return;
        }
        try {
            Field reflectedField = target.getClass().getField(field);
            reflectedField.set(target, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void invoke(Object target, String method) {
        if (target == null) {
            return;
        }
        try {
            target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void invoke(Object target, String method, Class<?> parameterType, Object argument) {
        if (target == null) {
            return;
        }
        try {
            Method reflectedMethod = target.getClass().getMethod(method, parameterType);
            reflectedMethod.invoke(target, argument);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
