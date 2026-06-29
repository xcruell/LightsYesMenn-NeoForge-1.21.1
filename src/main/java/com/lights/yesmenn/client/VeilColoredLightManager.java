package com.lights.yesmenn.client;

import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.ColoredLightPanelBlock;
import com.lights.yesmenn.block.ColoredLightSpotlightBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.lights.yesmenn.compat.OptionalModCompat;
import com.lights.yesmenn.compat.sable.SableSoundPositionCompat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public final class VeilColoredLightManager {
    private static final Map<ColoredLightBlockEntity, ManagedLight> LIGHTS = new IdentityHashMap<>();
    private static Float renderPartialTick;

    private VeilColoredLightManager() {
    }

    public static void register(ColoredLightBlockEntity blockEntity) {
        ManagedLight light = LIGHTS.computeIfAbsent(blockEntity, ignored -> new ManagedLight());
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && blockEntity.getLevel() == level && !blockEntity.isRemoved()) {
            try {
                light.update(blockEntity);
            } catch (RuntimeException ignored) {
                // Veil can still be initializing while chunks create block entities; the next tick retries.
            }
        }
    }

    public static void unregister(ColoredLightBlockEntity blockEntity) {
        ManagedLight light = LIGHTS.remove(blockEntity);
        if (light != null) {
            light.free();
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        updateAll();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            renderPartialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            try {
                updateAll();
            } finally {
                renderPartialTick = null;
            }
        }
    }

    private static void updateAll() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }

        Iterator<Map.Entry<ColoredLightBlockEntity, ManagedLight>> iterator = LIGHTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ColoredLightBlockEntity, ManagedLight> entry = iterator.next();
            ColoredLightBlockEntity blockEntity = entry.getKey();
            if (blockEntity.isRemoved() || blockEntity.getLevel() != level) {
                entry.getValue().free();
                iterator.remove();
                continue;
            }
            entry.getValue().update(blockEntity);
        }
    }

    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        LIGHTS.values().forEach(ManagedLight::free);
        LIGHTS.clear();
    }

    private static final class ManagedLight {
        private static final float ACTIVE_BRIGHTNESS = 2.0F;
        private static final float PANEL_HALF_SIZE = 0.34F;
        private static final float PANEL_ANGLE = (float) Math.toRadians(60.0);
        private static final double PANEL_PLANE_OFFSET = -0.235;
        private static final double SPOTLIGHT_LENS_OFFSET = 0.55;
        private LightRenderHandle<PointLightData> pointHandle;
        private LightRenderHandle<AreaLightData> areaHandle;
        private int color = -1;
        private int radius = -1;
        private boolean enabled;
        private Vec3 position;
        private Direction facing;
        private Direction beamFacing;
        private Mode mode;
        private int spotlightAngle = -1;
        private int spotlightBrightness = -1;
        private int spotlightYaw = -181;
        private int spotlightPitch = -181;
        private int spotlightSize = -1;
        private int spotlightSoftness = -1;
        private int spotlightFocus = -1;
        private double exposure = Double.NaN;
        private double colorStrength = Double.NaN;
        private double colorSaturation = Double.NaN;
        private double neutralLift = Double.NaN;
        private boolean yesMennRenderConfigActive;
        private Vec3 forward;
        private boolean sableTransformActive;
        private int sableStaticRefreshDelay;

        private void update(ColoredLightBlockEntity blockEntity) {
            boolean panel = blockEntity.getBlockState().getBlock() instanceof ColoredLightPanelBlock;
            boolean spotlight = blockEntity.getBlockState().getBlock() instanceof ColoredLightSpotlightBlock;
            Mode nextMode = spotlight ? Mode.SPOTLIGHT : panel ? Mode.PANEL : Mode.POINT;
            boolean nextEnabled = ColoredLightBlock.isLightEnabled(blockEntity.getBlockState());
            SableSoundPositionCompat.ResolvedTransform sableTransform = resolveSableTransform(blockEntity);
            Vec3 blockCenter = sableTransform.center();
            int nextBrightness = blockEntity.getBrightness();
            int nextSpotlightAngle = blockEntity.getSpotlightAngle();
            int nextSpotlightYaw = blockEntity.getSpotlightYaw();
            int nextSpotlightPitch = blockEntity.getSpotlightPitch();
            int nextSpotlightSize = blockEntity.getSpotlightSize();
            int nextSpotlightSoftness = blockEntity.getSpotlightSoftness();
            int nextSpotlightFocus = blockEntity.getSpotlightFocus();
            Direction nextFacing = switch (nextMode) {
                case PANEL -> blockEntity.getBlockState().getValue(ColoredLightPanelBlock.FACING);
                case SPOTLIGHT -> blockEntity.getBlockState().getValue(ColoredLightSpotlightBlock.FACING);
                case POINT -> null;
            };
            Direction nextBeamFacing = nextMode == Mode.SPOTLIGHT
                    ? blockEntity.getBlockState().getValue(ColoredLightSpotlightBlock.BEAM_FACING)
                    : null;
            AreaTransform nextAreaTransform = nextMode == Mode.PANEL
                    ? panelTransform(sableTransform, nextFacing)
                    : nextMode == Mode.SPOTLIGHT
                    ? spotlightTransform(sableTransform, nextFacing, nextBeamFacing, nextSpotlightYaw, nextSpotlightPitch)
                    : null;
            Vec3 nextPosition = nextAreaTransform != null ? nextAreaTransform.position() : blockCenter;
            Vec3 nextForward = nextAreaTransform != null ? nextAreaTransform.forward() : null;
            Quaternionf nextOrientation = nextAreaTransform != null ? nextAreaTransform.orientation() : null;
            if (!isValid(nextMode)) {
                free();
                if (!nextEnabled) {
                    return;
                }
                if (nextMode == Mode.PANEL || nextMode == Mode.SPOTLIGHT) {
                    createAreaLight(blockEntity, nextPosition, nextOrientation, nextMode);
                } else {
                    createPointLight(blockEntity, nextPosition);
                }
                color = blockEntity.getColor();
                radius = blockEntity.getRadius();
                spotlightBrightness = blockEntity.getBrightness();
                enabled = true;
                position = nextPosition;
                forward = nextForward;
                facing = nextFacing;
                beamFacing = nextBeamFacing;
                mode = nextMode;
                spotlightAngle = blockEntity.getSpotlightAngle();
                spotlightBrightness = blockEntity.getBrightness();
                spotlightYaw = blockEntity.getSpotlightYaw();
                spotlightPitch = blockEntity.getSpotlightPitch();
                spotlightSize = blockEntity.getSpotlightSize();
                spotlightSoftness = blockEntity.getSpotlightSoftness();
                spotlightFocus = blockEntity.getSpotlightFocus();
                LightsYesMenn.LOGGER.trace(
                        "Registered Veil colored {} light at {} with color #{}, radius {}",
                        nextMode.name().toLowerCase(),
                        nextPosition,
                        String.format("%06X", color),
                        radius);
                return;
            }

            int nextColor = blockEntity.getColor();
            int nextRadius = blockEntity.getRadius();
            int nextLightBrightness = nextBrightness;
            ColoredLightRenderConfig renderConfig = activeRenderConfig();
            boolean renderConfigChanged = renderConfigChanged(renderConfig);
            boolean positionChanged = position == null || position.distanceToSqr(nextPosition) > 0.000001;
            boolean forwardChanged = nextForward != null && (forward == null || forward.distanceToSqr(nextForward) > 0.000001);
            boolean facingChanged = (nextMode == Mode.PANEL || nextMode == Mode.SPOTLIGHT) && nextFacing != facing;
            boolean beamFacingChanged = nextMode == Mode.SPOTLIGHT && nextBeamFacing != beamFacing;
            boolean spotlightSettingsChanged = nextMode == Mode.SPOTLIGHT
                    && (nextSpotlightAngle != spotlightAngle
                    || nextLightBrightness != spotlightBrightness
                    || nextSpotlightYaw != spotlightYaw
                    || nextSpotlightPitch != spotlightPitch
                    || nextSpotlightSize != spotlightSize
                    || nextSpotlightSoftness != spotlightSoftness
                    || nextSpotlightFocus != spotlightFocus);
            boolean brightnessChanged = nextLightBrightness != spotlightBrightness;
            if (nextColor == color
                    && nextRadius == radius
                    && nextEnabled == enabled
                    && !positionChanged
                    && !forwardChanged
                    && !facingChanged
                    && !beamFacingChanged
                    && !renderConfigChanged
                    && !brightnessChanged
                    && !spotlightSettingsChanged) {
                return;
            }

            float[] nextColorValues = adjustedColor(nextColor, renderConfig);
            if (nextMode == Mode.PANEL || nextMode == Mode.SPOTLIGHT) {
                AreaLightData data = areaHandle.getLightData();
                if (positionChanged) {
                    data.getPosition().set(nextPosition.x, nextPosition.y, nextPosition.z);
                }
                if (forwardChanged || facingChanged || beamFacingChanged) {
                    data.getOrientation().set(nextOrientation);
                }
                float halfSize = nextMode == Mode.SPOTLIGHT ? nextSpotlightSize / 100.0F : PANEL_HALF_SIZE;
                data.setSize(
                        halfSize,
                        halfSize);
                data.setAngle(nextMode == Mode.SPOTLIGHT ? (float) Math.toRadians(nextSpotlightAngle) : PANEL_ANGLE);
                data
                        .setColor(
                                nextColorValues[0],
                                nextColorValues[1],
                                nextColorValues[2])
                        .setDistance(nextRadius)
                        .setBrightness(nextEnabled
                                ? brightnessMultiplier(nextLightBrightness)
                                * exposure(renderConfig)
                                : 0.0F);
                if (spotlightSettingsChanged && !facingChanged && !beamFacingChanged && !forwardChanged) {
                    data.getOrientation().set(nextOrientation);
                }
                if (nextMode == Mode.SPOTLIGHT) {
                    setAreaShape(data, nextSpotlightSoftness, nextSpotlightFocus);
                }
                areaHandle.markDirty();
            } else {
                PointLightData data = pointHandle.getLightData();
                if (positionChanged) {
                    data.setPosition(nextPosition.x, nextPosition.y, nextPosition.z);
                }
                data
                        .setColor(
                                nextColorValues[0],
                                nextColorValues[1],
                                nextColorValues[2])
                        .setRadius(nextRadius)
                        .setBrightness(nextEnabled ? brightnessMultiplier(nextLightBrightness) * exposure(renderConfig) : 0.0F);
                pointHandle.markDirty();
            }
            color = nextColor;
            radius = nextRadius;
            enabled = nextEnabled;
            position = nextPosition;
            forward = nextForward;
            facing = nextFacing;
            beamFacing = nextBeamFacing;
            mode = nextMode;
            spotlightAngle = nextSpotlightAngle;
            spotlightBrightness = nextLightBrightness;
            spotlightYaw = nextSpotlightYaw;
            spotlightPitch = nextSpotlightPitch;
            spotlightSize = nextSpotlightSize;
            spotlightSoftness = nextSpotlightSoftness;
            spotlightFocus = nextSpotlightFocus;
            storeRenderConfig(renderConfig);
        }

        private SableSoundPositionCompat.ResolvedTransform resolveSableTransform(ColoredLightBlockEntity blockEntity) {
            if (!sableTransformActive && sableStaticRefreshDelay-- > 0) {
                return SableSoundPositionCompat.identity(blockEntity.getBlockPos());
            }

            SableSoundPositionCompat.ResolvedTransform transform = SableSoundPositionCompat.resolveTransform(
                    blockEntity.getLevel(),
                    blockEntity.getBlockPos(),
                    renderPartialTick);
            sableTransformActive = transform.transformed();
            sableStaticRefreshDelay = sableTransformActive ? 0 : 10;
            return transform;
        }

        private boolean isValid(Mode nextMode) {
            return nextMode == Mode.PANEL || nextMode == Mode.SPOTLIGHT
                    ? areaHandle != null && areaHandle.isValid()
                    : pointHandle != null && pointHandle.isValid();
        }

        private void createPointLight(ColoredLightBlockEntity blockEntity, Vec3 position) {
            ColoredLightRenderConfig renderConfig = activeRenderConfig();
            float[] colorValues = adjustedColor(blockEntity.getColor(), renderConfig);
            PointLightData data = new PointLightData()
                    .setPosition(position.x, position.y, position.z)
                    .setColor(
                            colorValues[0],
                            colorValues[1],
                            colorValues[2])
                    .setRadius(blockEntity.getRadius())
                    .setBrightness(brightnessMultiplier(blockEntity.getBrightness()) * exposure(renderConfig))
                    .setOcclusionEnabled(false);
            pointHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
            storeRenderConfig(renderConfig);
        }

        private void createAreaLight(ColoredLightBlockEntity blockEntity, Vec3 position, Quaternionf orientation, Mode nextMode) {
            ColoredLightRenderConfig renderConfig = activeRenderConfig();
            float[] colorValues = adjustedColor(blockEntity.getColor(), renderConfig);
            AreaLightData data = new AreaLightData()
                    .setSize(
                            nextMode == Mode.SPOTLIGHT ? blockEntity.getSpotlightSize() / 100.0F : PANEL_HALF_SIZE,
                            nextMode == Mode.SPOTLIGHT ? blockEntity.getSpotlightSize() / 100.0F : PANEL_HALF_SIZE)
                    .setAngle(nextMode == Mode.SPOTLIGHT
                            ? (float) Math.toRadians(blockEntity.getSpotlightAngle())
                            : PANEL_ANGLE)
                    .setDistance(blockEntity.getRadius())
                    .setColor(
                            colorValues[0],
                            colorValues[1],
                            colorValues[2])
                    .setBrightness(brightnessMultiplier(blockEntity.getBrightness()) * exposure(renderConfig))
                    .setOcclusionEnabled(false);
            data.getPosition().set(position.x, position.y, position.z);
            data.getOrientation().set(orientation);
            if (nextMode == Mode.SPOTLIGHT) {
                setAreaShape(data, blockEntity.getSpotlightSoftness(), blockEntity.getSpotlightFocus());
            }
            areaHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
            storeRenderConfig(renderConfig);
        }

        private static void setAreaShape(AreaLightData data, int softness, int focus) {
            try {
                Class<?> registry = Class.forName("com.yesmenn.veilirislights.compat.light.AreaLightShapeRegistry");
                registry.getMethod("set", AreaLightData.class, float.class, float.class)
                        .invoke(null, data, softness / 100.0F, focus / 100.0F);
            } catch (ReflectiveOperationException ignored) {
                // VILC is optional; plain Veil ignores the extra beam-shape controls.
            }
        }

        private static void clearAreaShape(AreaLightData data) {
            try {
                Class<?> registry = Class.forName("com.yesmenn.veilirislights.compat.light.AreaLightShapeRegistry");
                registry.getMethod("clear", AreaLightData.class).invoke(null, data);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        private static float brightnessMultiplier(int brightness) {
            return Math.max(25, Math.min(800, brightness)) / 100.0F;
        }

        private static AreaTransform panelTransform(SableSoundPositionCompat.ResolvedTransform sableTransform, Direction facing) {
            Vec3 forward = transformedForward(sableTransform, directionVector(facing));
            return new AreaTransform(
                    sableTransform.center().add(forward.scale(PANEL_PLANE_OFFSET)),
                    forward,
                    orientationFromForward(forward));
        }

        private static AreaTransform spotlightTransform(
                SableSoundPositionCompat.ResolvedTransform sableTransform,
                Direction facing,
                Direction beamFacing,
                int yawDegrees,
                int pitchDegrees) {
            Vector3f localForward = spotlightOrientation(facing, beamFacing, yawDegrees, pitchDegrees)
                    .transform(new Vector3f(0.0F, 0.0F, -1.0F));
            Vec3 forward = transformedForward(sableTransform, new Vec3(localForward.x(), localForward.y(), localForward.z()));
            return new AreaTransform(
                    sableTransform.center().add(forward.scale(SPOTLIGHT_LENS_OFFSET)),
                    forward,
                    orientationFromForward(forward));
        }

        private static Vec3 transformedForward(SableSoundPositionCompat.ResolvedTransform sableTransform, Vec3 localForward) {
            Vec3 transformed = sableTransform.transformOffset(localForward);
            return transformed.lengthSqr() > 1.0E-8 ? transformed.normalize() : localForward.normalize();
        }

        private static Vec3 directionVector(Direction direction) {
            return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }

        private static Quaternionf orientationFromForward(Vec3 forward) {
            return new Quaternionf().rotationTo(
                    new Vector3f((float) forward.x, (float) forward.y, (float) forward.z),
                    new Vector3f(0.0F, 0.0F, 1.0F));
        }

        private static Quaternionf spotlightOrientation(
                Direction facing,
                Direction beamFacing,
                int yawDegrees,
                int pitchDegrees) {
            Quaternionf orientation = mountOrientation(facing);
            if (beamFacing != null) {
                applyInitialBeamYawRotation(orientation, facing, beamFacing);
            }
            if (yawDegrees != 0) {
                orientation.rotateY((float) Math.toRadians(yawDegrees));
            }
            if (beamFacing != null) {
                applyInitialBeamPitchRotation(orientation, facing, beamFacing);
            }
            int pitchRotation = defaultDisplayPitch(facing, beamFacing) - pitchDegrees;
            if (pitchRotation != 0) {
                orientation.rotateX((float) Math.toRadians(pitchRotation));
            }
            return orientation;
        }

        private static void applyInitialBeamYawRotation(
                Quaternionf orientation,
                Direction mountFace,
                Direction beamFacing) {
            Vector3f localBeam = worldToMountedLocal(mountFace, beamFacing);
            if (localBeam.lengthSquared() <= 0.0F) {
                return;
            }

            localBeam.normalize();
            if (Math.abs(localBeam.y()) > 0.999F) {
                return;
            }

            Vector3f defaultBeam = new Vector3f(0.0F, 0.0F, -1.0F);
            float alignment = defaultBeam.dot(localBeam);
            if (alignment > 0.999F) {
                return;
            }
            if (alignment < -0.999F) {
                orientation.rotateY((float) Math.toRadians(180.0));
                return;
            }
            orientation.mul(new Quaternionf().rotationTo(defaultBeam, localBeam));
        }

        private static void applyInitialBeamPitchRotation(
                Quaternionf orientation,
                Direction mountFace,
                Direction beamFacing) {
            Vector3f localBeam = worldToMountedLocal(mountFace, beamFacing);
            if (localBeam.lengthSquared() <= 0.0F) {
                return;
            }

            localBeam.normalize();
            if (Math.abs(localBeam.y()) > 0.999F) {
                orientation.rotateX((float) Math.toRadians(localBeam.y() > 0.0F ? 90.0 : -90.0));
            }
        }

        private static int defaultDisplayPitch(Direction mountFace, Direction beamFacing) {
            if (beamFacing == null) {
                return ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_PITCH;
            }

            Vector3f localBeam = worldToMountedLocal(mountFace, beamFacing);
            if (localBeam.lengthSquared() <= 0.0F) {
                return ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_PITCH;
            }

            localBeam.normalize();
            return Math.abs(localBeam.y()) > 0.999F ? 0 : 90;
        }

        private static Vector3f worldToMountedLocal(Direction mountFace, Direction direction) {
            float x = direction.getStepX();
            float y = direction.getStepY();
            float z = direction.getStepZ();
            return switch (mountFace) {
                case UP -> new Vector3f(x, y, z);
                case DOWN -> new Vector3f(x, -y, -z);
                case NORTH -> new Vector3f(x, -z, y);
                case SOUTH -> new Vector3f(x, z, -y);
                case WEST -> new Vector3f(y, -x, z);
                case EAST -> new Vector3f(-y, x, z);
            };
        }

        private static Quaternionf mountOrientation(Direction facing) {
            Quaternionf orientation = new Quaternionf();
            switch (facing) {
                case DOWN -> orientation.rotateX((float) Math.toRadians(180.0));
                case NORTH -> orientation.rotateX((float) Math.toRadians(-90.0));
                case SOUTH -> orientation.rotateX((float) Math.toRadians(90.0));
                case WEST -> orientation.rotateZ((float) Math.toRadians(90.0));
                case EAST -> orientation.rotateZ((float) Math.toRadians(-90.0));
                case UP -> {
                }
            }
            return orientation;
        }

        private static float linearChannel(int channel) {
            float srgb = (channel & 0xFF) / 255.0F;
            return srgb <= 0.04045F
                    ? srgb / 12.92F
                    : (float) Math.pow((srgb + 0.055F) / 1.055F, 2.4);
        }

        private static float[] adjustedColor(int color, ColoredLightRenderConfig config) {
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

        private static ColoredLightRenderConfig activeRenderConfig() {
            if (OptionalModCompat.vilcAvailable()) {
                return null;
            }
            ColoredLightRenderConfig renderConfig = ColoredLightRenderConfig.get();
            renderConfig.clamp();
            return renderConfig;
        }

        private static float exposure(ColoredLightRenderConfig renderConfig) {
            return renderConfig != null ? (float) renderConfig.exposure : 1.0F;
        }

        private boolean renderConfigChanged(ColoredLightRenderConfig renderConfig) {
            if (renderConfig == null) {
                return yesMennRenderConfigActive;
            }
            return !yesMennRenderConfigActive
                    || renderConfig.exposure != exposure
                    || renderConfig.colorStrength != colorStrength
                    || renderConfig.colorSaturation != colorSaturation
                    || renderConfig.neutralLift != neutralLift;
        }

        private void storeRenderConfig(ColoredLightRenderConfig renderConfig) {
            yesMennRenderConfigActive = renderConfig != null;
            exposure = renderConfig != null ? renderConfig.exposure : 1.0;
            colorStrength = renderConfig != null ? renderConfig.colorStrength : 1.0;
            colorSaturation = renderConfig != null ? renderConfig.colorSaturation : 1.0;
            neutralLift = renderConfig != null ? renderConfig.neutralLift : 0.0;
        }

        private void free() {
            if (pointHandle != null && pointHandle.isValid()) {
                pointHandle.free();
            }
            if (areaHandle != null && areaHandle.isValid()) {
                clearAreaShape(areaHandle.getLightData());
                areaHandle.free();
            }
            pointHandle = null;
            areaHandle = null;
            enabled = false;
            position = null;
            facing = null;
            beamFacing = null;
            mode = null;
            spotlightAngle = -1;
            spotlightBrightness = -1;
            spotlightYaw = -181;
            spotlightPitch = -181;
            spotlightSize = -1;
            spotlightSoftness = -1;
            spotlightFocus = -1;
            exposure = Double.NaN;
            colorStrength = Double.NaN;
            colorSaturation = Double.NaN;
            neutralLift = Double.NaN;
            yesMennRenderConfigActive = false;
            forward = null;
            sableTransformActive = false;
            sableStaticRefreshDelay = 0;
        }

        private enum Mode {
            POINT,
            PANEL,
            SPOTLIGHT
        }

        private record AreaTransform(Vec3 position, Vec3 forward, Quaternionf orientation) {
        }
    }
}
