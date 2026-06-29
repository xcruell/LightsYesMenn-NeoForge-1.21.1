package com.lights.yesmenn.compat.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class SableSoundPositionCompat {
    private static final boolean AVAILABLE;
    @Nullable
    private static final MethodHandle GET_CONTAINER;
    @Nullable
    private static final MethodHandle GET_PLOT;
    @Nullable
    private static final MethodHandle PLOT_CONTAINS;
    @Nullable
    private static final MethodHandle PLOT_GET_SUB_LEVEL;
    @Nullable
    private static final MethodHandle SUB_LEVEL_LOGICAL_POSE;
    @Nullable
    private static final MethodHandle CLIENT_SUB_LEVEL_RENDER_POSE;
    @Nullable
    private static final MethodHandle POSE_TRANSFORM_POSITION;

    static {
        MethodHandle getContainer = null;
        MethodHandle getPlot = null;
        MethodHandle plotContains = null;
        MethodHandle plotGetSubLevel = null;
        MethodHandle subLevelLogicalPose = null;
        MethodHandle clientSubLevelRenderPose = null;
        MethodHandle poseTransformPosition = null;
        boolean available = false;
        try {
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> plotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            // getMethod() enumerates Sable's overloads and loads ClientLevel on
            // dedicated servers. Method handles resolve only these descriptors.
            getContainer = lookup.findStatic(
                    containerClass,
                    "getContainer",
                    MethodType.methodType(containerClass, Level.class));
            getPlot = lookup.findVirtual(
                    containerClass,
                    "getPlot",
                    MethodType.methodType(plotClass, ChunkPos.class));
            plotContains = lookup.findVirtual(
                    plotClass,
                    "contains",
                    MethodType.methodType(boolean.class, Vec3.class));
            plotGetSubLevel = lookup.findVirtual(
                    plotClass,
                    "getSubLevel",
                    MethodType.methodType(subLevelClass));
            subLevelLogicalPose = lookup.findVirtual(
                    subLevelClass,
                    "logicalPose",
                    MethodType.methodType(poseClass));
            poseTransformPosition = lookup.findVirtual(
                    poseClass,
                    "transformPosition",
                    MethodType.methodType(Vec3.class, Vec3.class));
            try {
                Class<?> clientSubLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel");
                clientSubLevelRenderPose = lookup.findVirtual(
                        clientSubLevelClass,
                        "renderPose",
                        MethodType.methodType(poseClass, float.class));
            } catch (Throwable ignored) {
                // Dedicated servers and older Sable versions can still use logicalPose().
            }
            available = true;
        } catch (Throwable ignored) {
            // Optional compatibility must never prevent a world from loading.
        }

        GET_CONTAINER = getContainer;
        GET_PLOT = getPlot;
        PLOT_CONTAINS = plotContains;
        PLOT_GET_SUB_LEVEL = plotGetSubLevel;
        SUB_LEVEL_LOGICAL_POSE = subLevelLogicalPose;
        CLIENT_SUB_LEVEL_RENDER_POSE = clientSubLevelRenderPose;
        POSE_TRANSFORM_POSITION = poseTransformPosition;
        AVAILABLE = available;
    }

    private SableSoundPositionCompat() {
    }

    public static Vec3 resolve(Level level, BlockPos pos) {
        return resolveTransform(level, pos).center();
    }

    public static ResolvedTransform resolveTransform(Level level, BlockPos pos) {
        return resolveTransform(level, pos, null);
    }

    public static ResolvedTransform resolveTransform(Level level, BlockPos pos, @Nullable Float partialTick) {
        Vec3 localCenter = pos.getCenter();
        if (!AVAILABLE || GET_CONTAINER == null || GET_PLOT == null || PLOT_CONTAINS == null || PLOT_GET_SUB_LEVEL == null || SUB_LEVEL_LOGICAL_POSE == null || POSE_TRANSFORM_POSITION == null) {
            return ResolvedTransform.identity(localCenter);
        }

        try {
            Object container = GET_CONTAINER.invoke(level);
            if (container == null) {
                return ResolvedTransform.identity(localCenter);
            }

            Object plot = GET_PLOT.invoke(container, new ChunkPos(pos));
            if (plot == null || !(boolean) PLOT_CONTAINS.invoke(plot, localCenter)) {
                return ResolvedTransform.identity(localCenter);
            }

            Object subLevel = PLOT_GET_SUB_LEVEL.invoke(plot);
            if (subLevel == null) {
                return ResolvedTransform.identity(localCenter);
            }

            Object pose = renderPose(subLevel, partialTick);
            if (pose == null) {
                return ResolvedTransform.identity(localCenter);
            }

            Object transformed = POSE_TRANSFORM_POSITION.invoke(pose, localCenter);
            return transformed instanceof Vec3 vec3
                    ? new ResolvedTransform(localCenter, vec3, pose)
                    : ResolvedTransform.identity(localCenter);
        } catch (Throwable exception) {
            return ResolvedTransform.identity(localCenter);
        }
    }

    @Nullable
    private static Object renderPose(Object subLevel, @Nullable Float partialTick) throws Throwable {
        if (partialTick != null && CLIENT_SUB_LEVEL_RENDER_POSE != null) {
            try {
                return CLIENT_SUB_LEVEL_RENDER_POSE.invoke(subLevel, partialTick);
            } catch (ClassCastException ignored) {
                // Server-side sublevels expose only logicalPose().
            }
        }
        return SUB_LEVEL_LOGICAL_POSE != null ? SUB_LEVEL_LOGICAL_POSE.invoke(subLevel) : null;
    }

    public static ResolvedTransform identity(BlockPos pos) {
        return ResolvedTransform.identity(pos.getCenter());
    }

    public record ResolvedTransform(Vec3 localCenter, Vec3 center, @Nullable Object pose) {
        private static ResolvedTransform identity(Vec3 center) {
            return new ResolvedTransform(center, center, null);
        }

        public boolean transformed() {
            return pose != null;
        }

        public Vec3 transformOffset(Vec3 offset) {
            if (pose == null || POSE_TRANSFORM_POSITION == null || offset.lengthSqr() < 1.0E-8) {
                return offset;
            }

            try {
                Object transformed = POSE_TRANSFORM_POSITION.invoke(pose, localCenter.add(offset));
                if (transformed instanceof Vec3 vec3) {
                    return vec3.subtract(center);
                }
            } catch (Throwable ignored) {
            }
            return offset;
        }
    }
}
