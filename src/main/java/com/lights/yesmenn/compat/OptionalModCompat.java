package com.lights.yesmenn.compat;

import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class OptionalModCompat {
    private OptionalModCompat() {
    }

    public static boolean coloredLightAvailable() {
        return Loaded.VEIL;
    }

    public static boolean yesMennAvailable() {
        return Loaded.YESMENN;
    }

    public static boolean vilcAvailable() {
        return Loaded.VILC;
    }

    public static boolean vilcShaderPassActive() {
        MethodHandle handle = VilcShaderPass.IS_SHADER_PACK_IN_USE;
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invokeExact();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // Resolved lazily on first use, after FML has finished building the mod list.
    private static final class Loaded {
        private static final boolean VEIL = ModList.get().isLoaded("veil");
        private static final boolean YESMENN = ModList.get().isLoaded("yesmenn");
        private static final boolean VILC = ModList.get().isLoaded("veil_iris_lights");
    }

    private static final class VilcShaderPass {
        private static final MethodHandle IS_SHADER_PACK_IN_USE = resolve();

        private static MethodHandle resolve() {
            if (!Loaded.VILC) {
                return null;
            }
            try {
                Class<?> vilc = Class.forName("com.yesmenn.veilirislights.VeilIrisLights");
                return MethodHandles.publicLookup()
                        .findStatic(vilc, "isShaderPackInUse", MethodType.methodType(boolean.class));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
