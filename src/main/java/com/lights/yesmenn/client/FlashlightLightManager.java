package com.lights.yesmenn.client;

import com.lights.yesmenn.item.ColorfulFlashlightItem;
import com.lights.yesmenn.item.FlashlightSettings;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.lights.yesmenn.client.ColoredLightVeilSupport.activeRenderConfig;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.adjustedColor;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.applySpotCone;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.brightnessMultiplier;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.clearLightExtras;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.clearSpotShape;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.exposure;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.flickerFactor;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.inscattering;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.orientationFromForward;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.setLightExtras;
import static com.lights.yesmenn.client.ColoredLightVeilSupport.setSpotShape;

/**
 * Turns every switched-on Colorful Flashlight held by a visible player into a Veil spot light that follows the
 * player's view. Updated per frame so the beam stays locked to the camera.
 */
public final class FlashlightLightManager {
    /** Pushes the beam origin of other players (and the third-person view) out of the head. */
    private static final double THIRD_PERSON_FORWARD_OFFSET = 0.35;
    private static final Map<UUID, Beam> BEAMS = new HashMap<>();
    private static int frame;

    private FlashlightLightManager() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        frame++;
        for (AbstractClientPlayer player : level.players()) {
            ItemStack flashlight = activeFlashlight(player);
            if (flashlight == null) {
                continue;
            }
            Beam beam = BEAMS.computeIfAbsent(player.getUUID(), ignored -> new Beam());
            beam.seenFrame = frame;
            boolean firstPersonCamera = player == minecraft.getCameraEntity()
                    && minecraft.options.getCameraType().isFirstPerson();
            try {
                beam.update(player, flashlight, partialTick, firstPersonCamera);
            } catch (RuntimeException ignored) {
                // Veil can still be initializing on the first frames; the next frame retries.
            }
        }

        if (BEAMS.isEmpty()) {
            return;
        }
        Iterator<Beam> iterator = BEAMS.values().iterator();
        while (iterator.hasNext()) {
            Beam beam = iterator.next();
            if (beam.seenFrame != frame) {
                beam.free();
                iterator.remove();
            }
        }
    }

    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static ItemStack activeFlashlight(AbstractClientPlayer player) {
        if (player.isSpectator() || player.isRemoved()) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ColorfulFlashlightItem && FlashlightSettings.isEnabled(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static void clear() {
        BEAMS.values().forEach(Beam::free);
        BEAMS.clear();
    }

    /**
     * A flashlight is two Veil spot lights: a focused core and a wider, softer, dimmer spill around it, like the
     * reflector of a real torch. Both follow the view and never light the first-person hand or held item.
     */
    private static final class Beam {
        /** Spill cone angle relative to the core, and its share of the core brightness at 100 % spill. */
        private static final float SPILL_ANGLE_SCALE = 2.2F;
        private static final int SPILL_MAX_ANGLE = 75;
        private static final float SPILL_MAX_BRIGHTNESS = 0.45F;
        private static final float SPILL_RANGE_SCALE = 0.8F;
        private LightRenderHandle<SpotLightData> handle;
        private LightRenderHandle<SpotLightData> spillHandle;
        private FlashlightSettings settings;
        private ColoredLightRenderConfig renderConfig;
        private double exposure = Double.NaN;
        private double colorStrength = Double.NaN;
        private double colorSaturation = Double.NaN;
        private double neutralLift = Double.NaN;
        private Vec3 position;
        private Vec3 forward;
        private int seenFrame;
        private long seed;

        private void update(AbstractClientPlayer player, ItemStack stack, float partialTick, boolean firstPersonCamera) {
            Vec3 nextForward = player.getViewVector(partialTick);
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 nextPosition = firstPersonCamera ? eye : eye.add(nextForward.scale(THIRD_PERSON_FORWARD_OFFSET));
            FlashlightSettings nextSettings = FlashlightSettings.read(stack);
            ColoredLightRenderConfig nextConfig = activeRenderConfig();
            seed = player.getUUID().getLeastSignificantBits();

            boolean wantsSpill = nextSettings.spill() > 0;
            boolean hasSpill = spillHandle != null && spillHandle.isValid();
            if (handle == null || !handle.isValid() || wantsSpill != hasSpill) {
                free();
                SpotLightData data = new SpotLightData().setOcclusionEnabled(true);
                applyCore(data, nextSettings, nextConfig);
                applyTransform(data, nextPosition, nextForward);
                handle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
                if (wantsSpill) {
                    SpotLightData spill = new SpotLightData().setOcclusionEnabled(true);
                    applySpill(spill, nextSettings, nextConfig);
                    applyTransform(spill, nextPosition, nextForward);
                    spillHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(spill);
                }
                remember(nextSettings, nextConfig, nextPosition, nextForward);
                applyFlicker();
                return;
            }

            boolean moved = position.distanceToSqr(nextPosition) > 1.0E-8 || forward.distanceToSqr(nextForward) > 1.0E-8;
            boolean retuned = !nextSettings.equals(settings) || configChanged(nextConfig);
            boolean flickering = nextSettings.flicker() > 0;
            if (!moved && !retuned && !flickering) {
                return;
            }
            SpotLightData data = handle.getLightData();
            SpotLightData spill = spillHandle != null ? spillHandle.getLightData() : null;
            if (retuned) {
                applyCore(data, nextSettings, nextConfig);
                if (spill != null) {
                    applySpill(spill, nextSettings, nextConfig);
                }
            }
            if (moved) {
                applyTransform(data, nextPosition, nextForward);
                if (spill != null) {
                    applyTransform(spill, nextPosition, nextForward);
                }
            }
            remember(nextSettings, nextConfig, nextPosition, nextForward);
            applyFlicker();
            handle.markDirty();
            if (spillHandle != null) {
                spillHandle.markDirty();
            }
        }

        private static float coreBrightness(FlashlightSettings settings, ColoredLightRenderConfig config) {
            return brightnessMultiplier(settings.brightness()) * exposure(config);
        }

        private static float spillBrightness(FlashlightSettings settings, ColoredLightRenderConfig config) {
            return coreBrightness(settings, config) * SPILL_MAX_BRIGHTNESS * settings.spill() / 100.0F;
        }

        private static void applyCore(SpotLightData data, FlashlightSettings settings, ColoredLightRenderConfig config) {
            float[] color = adjustedColor(settings.color(), config);
            applySpotCone(data, settings.angle(), settings.softness());
            data.setColor(color[0], color[1], color[2])
                    .setDistance(settings.range())
                    .setBrightness(coreBrightness(settings, config))
                    .setInscatteringStrength(inscattering(settings.glow()));
            setSpotShape(data, settings.focus(), settings.size());
            setLightExtras(data, true, settings.fringe());
        }

        private static void applySpill(SpotLightData data, FlashlightSettings settings, ColoredLightRenderConfig config) {
            float[] color = adjustedColor(settings.color(), config);
            int spillAngle = Math.min(SPILL_MAX_ANGLE, Math.round(settings.angle() * SPILL_ANGLE_SCALE));
            // Very soft edge and neutral focus: the spill fades out gradually instead of forming a second ring.
            applySpotCone(data, spillAngle, 50);
            data.setColor(color[0], color[1], color[2])
                    .setDistance(Math.max(FlashlightSettings.MIN_RANGE, settings.range() * SPILL_RANGE_SCALE))
                    .setBrightness(spillBrightness(settings, config))
                    .setInscatteringStrength(inscattering(settings.glow()) * 0.35F);
            setSpotShape(data, 100, settings.size());
            setLightExtras(data, true, 0);
        }

        private void applyFlicker() {
            float factor = flickerFactor(settings.flicker(), seed, Util.getMillis() / 1000.0);
            handle.getLightData().setBrightness(coreBrightness(settings, renderConfig) * factor);
            if (spillHandle != null) {
                spillHandle.getLightData().setBrightness(spillBrightness(settings, renderConfig) * factor);
            }
        }

        private static void applyTransform(SpotLightData data, Vec3 position, Vec3 forward) {
            data.getPositionMutable().set(position.x, position.y, position.z);
            data.getOrientationMutable().set(orientationFromForward(forward));
        }

        private boolean configChanged(ColoredLightRenderConfig config) {
            return config != renderConfig
                    || config.exposure != exposure
                    || config.colorStrength != colorStrength
                    || config.colorSaturation != colorSaturation
                    || config.neutralLift != neutralLift;
        }

        private void remember(FlashlightSettings nextSettings, ColoredLightRenderConfig config, Vec3 nextPosition,
                              Vec3 nextForward) {
            settings = nextSettings;
            renderConfig = config;
            exposure = config.exposure;
            colorStrength = config.colorStrength;
            colorSaturation = config.colorSaturation;
            neutralLift = config.neutralLift;
            position = nextPosition;
            forward = nextForward;
        }

        private void free() {
            freeHandle(handle);
            freeHandle(spillHandle);
            handle = null;
            spillHandle = null;
            settings = null;
            renderConfig = null;
            position = null;
            forward = null;
        }

        private static void freeHandle(LightRenderHandle<SpotLightData> lightHandle) {
            if (lightHandle != null && lightHandle.isValid()) {
                clearSpotShape(lightHandle.getLightData());
                clearLightExtras(lightHandle.getLightData());
                lightHandle.free();
            }
        }
    }
}
