package com.lights.yesmenn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.ColoredLightSpotlightBlock;
import com.lights.yesmenn.block.InvisibleColoredLightSpotlightBlock;
import com.lights.yesmenn.block.InvisibleColoredPointLightBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.lights.yesmenn.compat.veil.ColoredLightCompatRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ColoredLightSpotlightRenderer implements BlockEntityRenderer<ColoredLightBlockEntity> {
    public static final ModelResourceLocation INVISIBLE_SPOTLIGHT_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/invisible_colored_spotlight"),
            ModelResourceLocation.STANDALONE_VARIANT);
    public static final ModelResourceLocation INVISIBLE_POINTLIGHT_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/invisible_colored_pointlight"),
            ModelResourceLocation.STANDALONE_VARIANT);
    public static final ModelResourceLocation YAW_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/colored_spotlight_yaw"),
            ModelResourceLocation.STANDALONE_VARIANT);
    public static final ModelResourceLocation BODY_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/colored_spotlight_body"),
            ModelResourceLocation.STANDALONE_VARIANT);
    public static final ModelResourceLocation LENS_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/colored_spotlight_lens"),
            ModelResourceLocation.STANDALONE_VARIANT);
    public static final ModelResourceLocation BIG_YAW_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/colored_spotlight_big_yaw"),
            ModelResourceLocation.STANDALONE_VARIANT);
    public static final ModelResourceLocation BIG_BODY_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/colored_spotlight_big_body"),
            ModelResourceLocation.STANDALONE_VARIANT);
    public static final ModelResourceLocation BIG_LENS_MODEL = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath(LightsYesMenn.MODID, "block/colored_spotlight_big_lens"),
            ModelResourceLocation.STANDALONE_VARIANT);
    private static final float YAW_PIVOT_X = 8.0F / 16.0F;
    private static final float YAW_PIVOT_Y = 2.5F / 16.0F;
    private static final float YAW_PIVOT_Z = 8.0F / 16.0F;
    private static final float PITCH_PIVOT_X = 8.0F / 16.0F;
    private static final float PITCH_PIVOT_Y = 8.0F / 16.0F;
    private static final float PITCH_PIVOT_Z = 8.0F / 16.0F;

    public ColoredLightSpotlightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            ColoredLightBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        Minecraft minecraft = Minecraft.getInstance();
        if (state.getBlock() instanceof InvisibleColoredPointLightBlock) {
            renderInvisibleModel(minecraft, blockEntity, state, poseStack, bufferSource, packedOverlay,
                    ColoredLightCompatRegistry.INVISIBLE_COLORED_POINTLIGHT_ITEM.get(), INVISIBLE_POINTLIGHT_MODEL);
            return;
        }
        if (!(state.getBlock() instanceof ColoredLightSpotlightBlock)) {
            return;
        }

        if (state.getBlock() instanceof InvisibleColoredLightSpotlightBlock) {
            renderInvisibleModel(minecraft, blockEntity, state, poseStack, bufferSource, packedOverlay,
                    ColoredLightCompatRegistry.INVISIBLE_COLORED_LIGHT_SPOTLIGHT_ITEM.get(), INVISIBLE_SPOTLIGHT_MODEL);
            return;
        }

        boolean big = state.is(ColoredLightCompatRegistry.COLORED_LIGHT_SPOTLIGHT_BIG.get());
        BakedModel yawModel = minecraft.getModelManager().getModel(big ? BIG_YAW_MODEL : YAW_MODEL);
        BakedModel bodyModel = minecraft.getModelManager().getModel(big ? BIG_BODY_MODEL : BODY_MODEL);
        BakedModel lensModel = minecraft.getModelManager().getModel(big ? BIG_LENS_MODEL : LENS_MODEL);
        BakedModel missingModel = minecraft.getModelManager().getMissingModel();
        if (yawModel == missingModel || bodyModel == missingModel || lensModel == missingModel) {
            return;
        }

        Direction facing = state.getValue(ColoredLightSpotlightBlock.FACING);
        Direction beamFacing = state.getValue(ColoredLightSpotlightBlock.BEAM_FACING);
        VertexConsumer consumer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyMountRotation(poseStack, facing);
        applyInitialBeamYawRotation(poseStack, facing, beamFacing);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        poseStack.translate(YAW_PIVOT_X, YAW_PIVOT_Y, YAW_PIVOT_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getSpotlightYaw()));
        poseStack.translate(-YAW_PIVOT_X, -YAW_PIVOT_Y, -YAW_PIVOT_Z);

        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), consumer, state, yawModel, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);

        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyInitialBeamPitchRotation(poseStack, facing, beamFacing);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        poseStack.translate(PITCH_PIVOT_X, PITCH_PIVOT_Y, PITCH_PIVOT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(defaultDisplayPitch(facing, beamFacing) - blockEntity.getSpotlightPitch()));
        poseStack.translate(-PITCH_PIVOT_X, -PITCH_PIVOT_Y, -PITCH_PIVOT_Z);

        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), consumer, state, bodyModel, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        if (ColoredLightBlock.isLightEnabled(state)) {
            float[] color = colorComponents(blockEntity.getColor());
            minecraft.getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(), consumer, state, lensModel, color[0], color[1], color[2],
                    LightTexture.FULL_BRIGHT, packedOverlay);
        }
        poseStack.popPose();
    }

    private static float[] colorComponents(int color) {
        return new float[] {
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F
        };
    }

    private static void applyMountRotation(PoseStack poseStack, Direction facing) {
        switch (facing) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            case UP -> {
            }
        }
    }

    private static void renderInvisibleModel(
            Minecraft minecraft,
            ColoredLightBlockEntity blockEntity,
            BlockState state,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedOverlay,
            Item visibleItem,
            ModelResourceLocation modelLocation) {
        if (minecraft.player == null
                || (!minecraft.player.getMainHandItem().is(visibleItem)
                && !minecraft.player.getOffhandItem().is(visibleItem))) {
            return;
        }

        BakedModel model = minecraft.getModelManager().getModel(modelLocation);
        if (model == minecraft.getModelManager().getMissingModel()) {
            return;
        }

        float[] color = colorComponents(blockEntity.getColor());
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), bufferSource.getBuffer(Sheets.cutoutBlockSheet()), state, model,
                color[0], color[1], color[2], LightTexture.FULL_BRIGHT, packedOverlay);
    }

    private static void applyInitialBeamYawRotation(PoseStack poseStack, Direction mountFace, Direction beamFacing) {
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
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            return;
        }
        poseStack.mulPose(new Quaternionf().rotationTo(defaultBeam, localBeam));
    }

    private static void applyInitialBeamPitchRotation(PoseStack poseStack, Direction mountFace, Direction beamFacing) {
        Vector3f localBeam = worldToMountedLocal(mountFace, beamFacing);
        if (localBeam.lengthSquared() <= 0.0F) {
            return;
        }

        localBeam.normalize();
        if (Math.abs(localBeam.y()) > 0.999F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(localBeam.y() > 0.0F ? 90.0F : -90.0F));
        }
    }

    private static int defaultDisplayPitch(Direction mountFace, Direction beamFacing) {
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
}