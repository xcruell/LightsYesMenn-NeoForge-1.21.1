package com.lights.yesmenn.client;

import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.ColoredLightPanelBlock;
import com.lights.yesmenn.block.ColoredLightSpotlightBlock;
import com.lights.yesmenn.block.HangingLampBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.lights.yesmenn.compat.sable.SableSoundPositionCompat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.Util;
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

import static com.lights.yesmenn.client.ColoredLightVeilSupport.activeRenderConfig;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.adjustedColor;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.applySpotCone;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.brightnessMultiplier;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.clearSpotShape;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.exposure;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.flickerFactor;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.inscattering;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.orientationFromForward;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.setSpotShape;

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
                updateMoving();
            } finally {
                renderPartialTick = null;
            }
            updateFlicker();
        }
    }

    // Flicker and warm-up change brightness every frame; only lights using them are touched.
    private static void updateFlicker() {
        long now = Util.getMillis();
        double seconds = now / 1000.0;
        for (ManagedLight light : LIGHTS.values()) {
            if (light.flicker > 0 || light.warmupStartMillis != ManagedLight.NOT_WARMING) {
                light.applyFlicker(seconds, now);
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

    // Static lights are refreshed on the client tick; only lights on moving Sable sublevels need per-frame updates.
    private static void updateMoving() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Map.Entry<ColoredLightBlockEntity, ManagedLight> entry : LIGHTS.entrySet()) {
            ColoredLightBlockEntity blockEntity = entry.getKey();
            if (entry.getValue().sableTransformActive && !blockEntity.isRemoved() && blockEntity.getLevel() == level) {
                entry.getValue().update(blockEntity);
            }
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
        private static final long NOT_WARMING = Long.MIN_VALUE;
        /** Brightness share right after switching on; an HQL lamp starts as a dim glow. */
        private static final float WARMUP_START = 0.05F;
        private LightRenderHandle<PointLightData> pointHandle;
        private LightRenderHandle<AreaLightData> areaHandle;
        private LightRenderHandle<SpotLightData> spotHandle;
        private int color = -1;
        private int radius = -1;
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
        private int flicker = -1;
        private int glow = -1;
        private float baseBrightness;
        private long seed;
        /** Warm-up of hanging lamps after switching on (not reset by free(): it spans the off -> on change). */
        private boolean switchedOff;
        private long warmupStartMillis = NOT_WARMING;
        private long warmupMillis;
        /** Lamp color as sent to Veil, and the warm-up tint it blends in from (null = no tint). */
        private float[] baseColor;
        private float[] warmupTintColor;
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
            // A hanging lamp always shines straight down, whatever yaw/pitch got copied onto it.
            boolean hangingLamp = blockEntity.getBlockState().getBlock() instanceof HangingLampBlock;
            int nextSpotlightYaw = hangingLamp ? 0 : blockEntity.getSpotlightYaw();
            int nextSpotlightPitch = hangingLamp ? 0 : blockEntity.getSpotlightPitch();
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
            if (!nextEnabled) {
                // Disabled lights are removed from Veil entirely instead of being kept at zero brightness.
                if (pointHandle != null || areaHandle != null || spotHandle != null) {
                    free();
                }
                switchedOff = true;
                warmupStartMillis = NOT_WARMING;
                return;
            }
            // Lamps already on when they load light up at once; only switching on warms up.
            warmupMillis = hangingLamp ? blockEntity.getWarmup() * 100L : 0L;
            int tint = hangingLamp ? HangingLampBlock.warmupTint(blockEntity.getWarmupTint()) : -1;
            warmupTintColor = tint >= 0 ? adjustedColor(tint, activeRenderConfig()) : null;
            if (!isValid(nextMode)) {
                free();
                switch (nextMode) {
                    case PANEL -> createPanelLight(blockEntity, nextPosition, nextOrientation);
                    case SPOTLIGHT -> createSpotLight(blockEntity, nextPosition, nextOrientation);
                    case POINT -> createPointLight(blockEntity, nextPosition);
                }
                color = blockEntity.getColor();
                radius = blockEntity.getRadius();
                spotlightBrightness = blockEntity.getBrightness();
                position = nextPosition;
                forward = nextForward;
                facing = nextFacing;
                beamFacing = nextBeamFacing;
                mode = nextMode;
                spotlightAngle = blockEntity.getSpotlightAngle();
                spotlightBrightness = blockEntity.getBrightness();
                spotlightYaw = nextSpotlightYaw;
                spotlightPitch = nextSpotlightPitch;
                spotlightSize = blockEntity.getSpotlightSize();
                spotlightSoftness = blockEntity.getSpotlightSoftness();
                spotlightFocus = blockEntity.getSpotlightFocus();
                flicker = blockEntity.getFlicker();
                glow = blockEntity.getGlow();
                seed = blockEntity.getBlockPos().asLong();
                baseBrightness = brightnessMultiplier(blockEntity.getBrightness()) * exposure(activeRenderConfig());
                baseColor = adjustedColor(blockEntity.getColor(), activeRenderConfig());
                if (switchedOff && warmupMillis > 0) {
                    warmupStartMillis = Util.getMillis();
                    applyFlicker(warmupStartMillis / 1000.0, warmupStartMillis);
                }
                switchedOff = false;
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
            int nextFlicker = blockEntity.getFlicker();
            int nextGlow = blockEntity.getGlow();
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
                    && !positionChanged
                    && !forwardChanged
                    && !facingChanged
                    && !beamFacingChanged
                    && !renderConfigChanged
                    && !brightnessChanged
                    && !spotlightSettingsChanged
                    && nextFlicker == flicker
                    && nextGlow == glow) {
                return;
            }

            float[] nextColorValues = adjustedColor(nextColor, renderConfig);
            boolean orientationChanged = forwardChanged || facingChanged || beamFacingChanged || spotlightSettingsChanged;
            float lightBrightness = brightnessMultiplier(nextLightBrightness) * exposure(renderConfig);
            if (nextMode == Mode.PANEL) {
                AreaLightData data = areaHandle.getLightData();
                if (positionChanged) {
                    data.getPosition().set(nextPosition.x, nextPosition.y, nextPosition.z);
                }
                if (orientationChanged) {
                    data.getOrientation().set(nextOrientation);
                }
                data.setColor(nextColorValues[0], nextColorValues[1], nextColorValues[2])
                        .setDistance(nextRadius)
                        .setBrightness(lightBrightness)
                        .setInscatteringStrength(inscattering(nextGlow));
                areaHandle.markDirty();
            } else if (nextMode == Mode.SPOTLIGHT) {
                SpotLightData data = spotHandle.getLightData();
                if (positionChanged) {
                    data.getPositionMutable().set(nextPosition.x, nextPosition.y, nextPosition.z);
                }
                if (orientationChanged) {
                    data.getOrientationMutable().set(nextOrientation);
                }
                applySpotCone(data, nextSpotlightAngle, nextSpotlightSoftness);
                data.setColor(nextColorValues[0], nextColorValues[1], nextColorValues[2])
                        .setDistance(nextRadius)
                        .setBrightness(lightBrightness)
                        .setInscatteringStrength(inscattering(nextGlow));
                setSpotShape(data, nextSpotlightFocus, nextSpotlightSize);
                spotHandle.markDirty();
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
                        .setBrightness(lightBrightness)
                        .setInscatteringStrength(inscattering(nextGlow));
                pointHandle.markDirty();
            }
            color = nextColor;
            radius = nextRadius;
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
            flicker = nextFlicker;
            glow = nextGlow;
            baseBrightness = lightBrightness;
            baseColor = nextColorValues;
            storeRenderConfig(renderConfig);
        }

        private void applyFlicker(double seconds, long nowMillis) {
            float warm = warmupProgress(nowMillis);
            float brightness = baseBrightness * flickerFactor(flicker, seed, seconds)
                    * (WARMUP_START + (1.0F - WARMUP_START) * warm);
            if (pointHandle != null && pointHandle.isValid()) {
                pointHandle.getLightData().setBrightness(brightness);
                pointHandle.markDirty();
            } else if (areaHandle != null && areaHandle.isValid()) {
                areaHandle.getLightData().setBrightness(brightness);
                areaHandle.markDirty();
            } else if (spotHandle != null && spotHandle.isValid()) {
                SpotLightData data = spotHandle.getLightData();
                data.setBrightness(brightness);
                // Warm-up color: starts in the tint and blends into the lamp color (restored exactly at the end).
                if (warmupTintColor != null && baseColor != null) {
                    data.setColor(
                            warmupTintColor[0] + (baseColor[0] - warmupTintColor[0]) * warm,
                            warmupTintColor[1] + (baseColor[1] - warmupTintColor[1]) * warm,
                            warmupTintColor[2] + (baseColor[2] - warmupTintColor[2]) * warm);
                }
                spotHandle.markDirty();
            }
        }

        /** Eased warm-up progress 0..1 (1 = not warming); ends the warm-up when it is done. */
        private float warmupProgress(long nowMillis) {
            if (warmupStartMillis == NOT_WARMING) {
                return 1.0F;
            }
            double progress = warmupMillis > 0 ? (nowMillis - warmupStartMillis) / (double) warmupMillis : 1.0;
            if (progress >= 1.0) {
                warmupStartMillis = NOT_WARMING;
                return 1.0F;
            }
            double clamped = Math.max(0.0, progress);
            return (float) (clamped * clamped * (3.0 - 2.0 * clamped));
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
            return switch (nextMode) {
                case PANEL -> areaHandle != null && areaHandle.isValid();
                case SPOTLIGHT -> spotHandle != null && spotHandle.isValid();
                case POINT -> pointHandle != null && pointHandle.isValid();
            };
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
                    .setInscatteringStrength(inscattering(blockEntity.getGlow()))
                    .setOcclusionEnabled(false);
            pointHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
            storeRenderConfig(renderConfig);
        }

        private void createPanelLight(ColoredLightBlockEntity blockEntity, Vec3 position, Quaternionf orientation) {
            ColoredLightRenderConfig renderConfig = activeRenderConfig();
            float[] colorValues = adjustedColor(blockEntity.getColor(), renderConfig);
            AreaLightData data = new AreaLightData()
                    .setSize(PANEL_HALF_SIZE, PANEL_HALF_SIZE)
                    .setAngle(PANEL_ANGLE)
                    .setDistance(blockEntity.getRadius())
                    .setColor(colorValues[0], colorValues[1], colorValues[2])
                    .setBrightness(brightnessMultiplier(blockEntity.getBrightness()) * exposure(renderConfig))
                    .setInscatteringStrength(inscattering(blockEntity.getGlow()))
                    .setOcclusionEnabled(false);
            data.getPosition().set(position.x, position.y, position.z);
            data.getOrientation().set(orientation);
            areaHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
            storeRenderConfig(renderConfig);
        }

        private void createSpotLight(ColoredLightBlockEntity blockEntity, Vec3 position, Quaternionf orientation) {
            ColoredLightRenderConfig renderConfig = activeRenderConfig();
            float[] colorValues = adjustedColor(blockEntity.getColor(), renderConfig);
            SpotLightData data = new SpotLightData()
                    .setDistance(blockEntity.getRadius())
                    .setColor(colorValues[0], colorValues[1], colorValues[2])
                    .setBrightness(brightnessMultiplier(blockEntity.getBrightness()) * exposure(renderConfig))
                    .setInscatteringStrength(inscattering(blockEntity.getGlow()))
                    .setOcclusionEnabled(false);
            applySpotCone(data, blockEntity.getSpotlightAngle(), blockEntity.getSpotlightSoftness());
            data.getPositionMutable().set(position.x, position.y, position.z);
            data.getOrientationMutable().set(orientation);
            setSpotShape(data, blockEntity.getSpotlightFocus(), blockEntity.getSpotlightSize());
            spotHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
            storeRenderConfig(renderConfig);
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
                areaHandle.free();
            }
            if (spotHandle != null && spotHandle.isValid()) {
                clearSpotShape(spotHandle.getLightData());
                spotHandle.free();
            }
            pointHandle = null;
            areaHandle = null;
            spotHandle = null;
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
            flicker = -1;
            glow = -1;
            baseBrightness = 0.0F;
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
