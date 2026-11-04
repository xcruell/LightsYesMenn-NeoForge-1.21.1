package com.lights.yesmenn.client;

import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.HangingLampBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Filament of the hanging lamp. Block model textures can only loop their animation, so the filament is drawn here:
 * off texture while the lamp is off, the warm-up strip once after switching on (spread over the lamp's warm-up
 * time, so the filament glows up in step with the light), then the steady "on" texture.
 * While lit it is a fullbright, lightly tinted core plus an additive, emissive glow layer in the lamp color.
 */
final class HangingLampWireRenderer {
    private static final ResourceLocation WIRE_OFF = texture("bulb_wire_off");
    private static final ResourceLocation WIRE_ON = texture("bulb_wire_on");
    /** Vertical strip of 16x16 frames, played once (no .mcmeta, so the atlas keeps all frames). */
    private static final ResourceLocation WIRE_WARMUP = texture("bulb_wire_anim");
    /** Warm-up time is stored in tenths of a second; 20 ticks per second. */
    private static final float TICKS_PER_WARMUP_UNIT = 2.0F;
    /** How much of the lamp color the filament core takes (0 = white, 1 = full tint). */
    private static final float CORE_TINT = 0.35F;
    /** Glow layers sit this far in front of and behind the filament plane (1/16 = one model pixel). */
    private static final float GLOW_OFFSET = 0.05F / 16.0F;
    /** Glow layer: size relative to the filament and additive strength. */
    private static final float GLOW_SCALE = 1.18F;
    private static final float GLOW_INTENSITY = 0.85F;
    private static final float WARMUP_GLOW_START = 0.05F;

    // Two crossed planes, as in the Blockbench model: x = 8, z 7..9, y -0.5..2, turned +-45 deg around (8, 0.75, 8).
    private static final float PLANE_X = 8.0F / 16.0F;
    private static final float MIN_Z = 7.0F / 16.0F;
    private static final float MAX_Z = 9.0F / 16.0F;
    private static final float MIN_Y = -0.5F / 16.0F;
    private static final float MAX_Y = 2.0F / 16.0F;
    private static final float PIVOT_Y = 0.75F / 16.0F;
    // Face UV [0, 0, 8, 10] in 16ths of the texture.
    private static final float MAX_U = 8.0F / 16.0F;
    private static final float MAX_V = 10.0F / 16.0F;

    private static final Map<ColoredLightBlockEntity, LitState> STATES = new WeakHashMap<>();

    private HangingLampWireRenderer() {
    }

    static void render(ColoredLightBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        boolean lit = ColoredLightBlock.isLightEnabled(blockEntity.getBlockState());
        long gameTime = blockEntity.getLevel().getGameTime();
        LitState litState = STATES.computeIfAbsent(blockEntity, ignored -> new LitState(lit));
        if (lit && !litState.lit) {
            litState.litSince = gameTime;
        }
        litState.lit = lit;

        float warmupTicks = blockEntity.getWarmup() * TICKS_PER_WARMUP_UNIT;
        Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite sprite;
        float frameV0 = 0.0F;
        float frameHeight = 1.0F;
        if (!lit) {
            sprite = atlas.apply(WIRE_OFF);
        } else {
            sprite = atlas.apply(WIRE_ON);
            if (litState.litSince != Long.MIN_VALUE && warmupTicks > 0.0F) {
                TextureAtlasSprite warmup = atlas.apply(WIRE_WARMUP);
                int frames = Math.max(1, warmup.contents().height() / Math.max(1, warmup.contents().width()));
                float progress = (gameTime - litState.litSince + partialTick) / warmupTicks;
                int frame = (int) (progress * frames);
                if (frame < frames) {
                    sprite = warmup;
                    frameHeight = 1.0F / frames;
                    frameV0 = frame * frameHeight;
                }
            }
        }

        int light = lit ? LightTexture.FULL_BRIGHT : packedLight;
        int color = lit ? blockEntity.getColor() : 0xFFFFFF;
        // Glow strength follows the light's warm-up brightness; the warm-up color blends into the lamp color.
        float glowStrength = 1.0F;
        if (lit && litState.litSince != Long.MIN_VALUE && warmupTicks > 0.0F) {
            float progress = Math.min(1.0F, Math.max(0.0F,
                    (gameTime - litState.litSince + partialTick) / warmupTicks));
            float eased = progress * progress * (3.0F - 2.0F * progress);
            glowStrength = WARMUP_GLOW_START + (1.0F - WARMUP_GLOW_START) * eased;
            int tint = HangingLampBlock.warmupTint(blockEntity.getWarmupTint());
            if (tint >= 0) {
                color = blend(tint, color, eased);
            }
        }
        float u0 = sprite.getU(0.0F);
        float u1 = sprite.getU(MAX_U);
        float v0 = sprite.getV(frameV0);
        float v1 = sprite.getV(frameV0 + MAX_V * frameHeight);

        // Sandwich: lightly tinted filament core in the middle, an additive glow layer just in front of and behind
        // it. Coplanar layers z-fought; the small offset keeps them apart.
        int core = lit ? blend(0xFFFFFF, color, CORE_TINT) : color;
        drawPlanes(bufferSource.getBuffer(Sheets.cutoutBlockSheet()), poseStack, 0.0F, 1.0F, core, 1.0F,
                u0, u1, v0, v1, light, packedOverlay);
        if (lit) {
            // Glow: additive and emissive ("eyes" render type, which shaderpacks treat as glowing), slightly larger
            // than the filament so its edges bleed out into the bulb like the colored light's core.
            VertexConsumer glow = bufferSource.getBuffer(RenderType.eyes(InventoryMenu.BLOCK_ATLAS));
            float intensity = GLOW_INTENSITY * glowStrength;
            drawPlanes(glow, poseStack, GLOW_OFFSET, GLOW_SCALE, color, intensity, u0, u1, v0, v1, light, packedOverlay);
            drawPlanes(glow, poseStack, -GLOW_OFFSET, GLOW_SCALE, color, intensity, u0, u1, v0, v1, light, packedOverlay);
        }
    }

    private static void drawPlanes(VertexConsumer consumer, PoseStack poseStack, float offset, float scale, int color,
                                   float intensity, float u0, float u1, float v0, float v1, int light, int overlay) {
        float red = (color >> 16 & 0xFF) / 255.0F * intensity;
        float green = (color >> 8 & 0xFF) / 255.0F * intensity;
        float blue = (color & 0xFF) / 255.0F * intensity;
        for (float angle : new float[]{-45.0F, 45.0F}) {
            poseStack.pushPose();
            poseStack.translate(PLANE_X, PIVOT_Y, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            poseStack.scale(1.0F, scale, scale);
            poseStack.translate(-PLANE_X + offset, -PIVOT_Y, -0.5F);
            PoseStack.Pose pose = poseStack.last();
            // East side (u runs from +z to -z) and west side (u runs from -z to +z).
            quad(consumer, pose, MAX_Z, MIN_Z, u0, u1, v0, v1, red, green, blue, light, overlay);
            quad(consumer, pose, MIN_Z, MAX_Z, u0, u1, v0, v1, red, green, blue, light, overlay);
            poseStack.popPose();
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float zStart, float zEnd,
                             float u0, float u1, float v0, float v1,
                             float red, float green, float blue, int light, int overlay) {
        // The normal points up so the entity shader's directional shading does not darken the filament.
        vertex(consumer, pose, MAX_Y, zStart, u0, v0, red, green, blue, light, overlay);
        vertex(consumer, pose, MIN_Y, zStart, u0, v1, red, green, blue, light, overlay);
        vertex(consumer, pose, MIN_Y, zEnd, u1, v1, red, green, blue, light, overlay);
        vertex(consumer, pose, MAX_Y, zEnd, u1, v0, red, green, blue, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float y, float z, float u, float v,
                               float red, float green, float blue, int light, int overlay) {
        consumer.addVertex(pose, PLANE_X, y, z)
                .setColor(red, green, blue, 1.0F)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static int blend(int from, int to, float amount) {
        int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return red << 16 | green << 8 | blue;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/hanging_lamp/" + name);
    }

    private static final class LitState {
        private boolean lit;
        /** Game time of the last switch-on; MIN_VALUE when the lamp was already on when it came into view. */
        private long litSince = Long.MIN_VALUE;

        private LitState(boolean lit) {
            this.lit = lit;
        }
    }
}
