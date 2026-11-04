package com.lights.yesmenn.client;

import com.lights.yesmenn.client.screen.ColoredLightScreen;
import com.lights.yesmenn.item.ColorfulFlashlightItem;
import com.lights.yesmenn.item.FlashlightSettings;
import com.lights.yesmenn.network.OpenColoredLightScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class ColoredLightClientHooks {
    private ColoredLightClientHooks() {
    }

    public static void openScreen(OpenColoredLightScreenPayload payload) {
        Minecraft.getInstance().setScreen(new ColoredLightScreen(payload));
    }

    public static void openFlashlightScreen(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (stack.getItem() instanceof ColorfulFlashlightItem) {
            minecraft.setScreen(new ColoredLightScreen(hand, FlashlightSettings.read(stack)));
        }
    }

    public static void refreshBlockTint(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.levelRenderer == null) {
            return;
        }
        minecraft.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }
}
