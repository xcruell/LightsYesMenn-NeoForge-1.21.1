package com.lights.yesmenn.item;

import com.lights.yesmenn.block.ColoredLightSpotlightBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import java.util.List;

public class LightConfiguratorItem extends Item {
    private static final String HAS_CONFIG = "HasLightConfig";
    private static final String COLOR = "LightColor";
    private static final String RADIUS = "LightRadius";
    private static final String BRIGHTNESS = "LightBrightness";
    private static final String REDSTONE_INVERTED = "LightRedstoneInverted";
    private static final String HAS_SPOTLIGHT = "HasSpotlightConfig";
    private static final String SPOTLIGHT_ANGLE = "SpotlightAngle";
    private static final String SPOTLIGHT_BRIGHTNESS = "SpotlightBrightness";
    private static final String SPOTLIGHT_YAW = "SpotlightYaw";
    private static final String SPOTLIGHT_PITCH = "SpotlightPitch";
    private static final String SPOTLIGHT_SIZE = "SpotlightSize";
    private static final String SPOTLIGHT_SOFTNESS = "SpotlightSoftness";
    private static final String SPOTLIGHT_FOCUS = "SpotlightFocus";

    public LightConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return useOnLight(
                context.getLevel(),
                context.getClickedPos(),
                context.getItemInHand(),
                context.getPlayer());
    }

    public static InteractionResult useOnLight(Level level, BlockPos pos, ItemStack stack, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ColoredLightBlockEntity light)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            if (!hasConfig(stack) || player == null || !player.isShiftKeyDown()) {
                writeConfig(stack, light);
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "message.lightsyesmenn.light_configurator.copied",
                            light.getRadius(),
                            String.format("#%06X", light.getColor()),
                            light.getBrightness()), true);
                }
            } else {
                CompoundTag tag = customData(stack).copyTag();
                int radius = tag.getInt(RADIUS);
                int color = tag.getInt(COLOR);
                light.setConfig(radius, color, tag.getBoolean(REDSTONE_INVERTED));
                if (tag.contains(BRIGHTNESS)) {
                    light.setBrightness(tag.getInt(BRIGHTNESS));
                }
                if (tag.getBoolean(HAS_SPOTLIGHT)
                        && light.getBlockState().getBlock() instanceof ColoredLightSpotlightBlock) {
                    light.setSpotlightConfig(
                            tag.getInt(SPOTLIGHT_ANGLE),
                            tag.contains(SPOTLIGHT_BRIGHTNESS) ? tag.getInt(SPOTLIGHT_BRIGHTNESS) : light.getBrightness(),
                            tag.getInt(SPOTLIGHT_YAW),
                            tag.getInt(SPOTLIGHT_PITCH),
                            tag.getInt(SPOTLIGHT_SIZE),
                            tag.getInt(SPOTLIGHT_SOFTNESS),
                            tag.getInt(SPOTLIGHT_FOCUS));
                }
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "message.lightsyesmenn.light_configurator.applied"), true);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (!hasConfig(stack)) {
            tooltipComponents.add(Component.translatable("tooltip.lightsyesmenn.light_configurator.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        CompoundTag tag = customData(stack).copyTag();
        tooltipComponents.add(Component.translatable(
                        "tooltip.lightsyesmenn.light_configurator.config",
                        tag.getInt(RADIUS),
                        String.format("#%06X", tag.getInt(COLOR) & 0xFFFFFF),
                        tag.contains(BRIGHTNESS) ? tag.getInt(BRIGHTNESS) : ColoredLightBlockEntity.DEFAULT_BRIGHTNESS)
                .withStyle(ChatFormatting.GRAY));
        if (tag.getBoolean(HAS_SPOTLIGHT)) {
            tooltipComponents.add(Component.translatable("tooltip.lightsyesmenn.light_configurator.spotlight")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(Component.translatable("tooltip.lightsyesmenn.light_configurator.copy")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.lightsyesmenn.light_configurator.apply")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void writeConfig(ItemStack stack, ColoredLightBlockEntity light) {
        CompoundTag tag = customData(stack).copyTag();
        tag.putBoolean(HAS_CONFIG, true);
        tag.putInt(RADIUS, light.getRadius());
        tag.putInt(COLOR, light.getColor() & 0xFFFFFF);
        tag.putInt(BRIGHTNESS, light.getBrightness());
        tag.putBoolean(REDSTONE_INVERTED, light.isRedstoneInverted());
        boolean spotlight = light.getBlockState().getBlock() instanceof ColoredLightSpotlightBlock;
        tag.putBoolean(HAS_SPOTLIGHT, spotlight);
        if (spotlight) {
            tag.putInt(SPOTLIGHT_ANGLE, light.getSpotlightAngle());
            tag.putInt(SPOTLIGHT_BRIGHTNESS, light.getBrightness());
            tag.putInt(SPOTLIGHT_YAW, light.getSpotlightYaw());
            tag.putInt(SPOTLIGHT_PITCH, light.getSpotlightPitch());
            tag.putInt(SPOTLIGHT_SIZE, light.getSpotlightSize());
            tag.putInt(SPOTLIGHT_SOFTNESS, light.getSpotlightSoftness());
            tag.putInt(SPOTLIGHT_FOCUS, light.getSpotlightFocus());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static boolean hasConfig(ItemStack stack) {
        return customData(stack).copyTag().getBoolean(HAS_CONFIG);
    }

    private static CustomData customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
    }
}
