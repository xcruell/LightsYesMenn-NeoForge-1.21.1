package com.lights.yesmenn.registry;

import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.item.LightConfiguratorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LightsYesMenn.MODID);

    public static final DeferredItem<LightConfiguratorItem> LIGHT_CONFIGURATOR =
            ITEMS.register("light_configurator", () -> new LightConfiguratorItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}