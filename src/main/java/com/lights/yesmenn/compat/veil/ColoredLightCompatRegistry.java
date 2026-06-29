package com.lights.yesmenn.compat.veil;

import com.lights.yesmenn.LightsYesMenn;
import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.ColoredLightPanelBlock;
import com.lights.yesmenn.block.ColoredLightSpotlightBlock;
import com.lights.yesmenn.block.ColoredLightTubeBlock;
import com.lights.yesmenn.block.InvisibleColoredLightSpotlightBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.lights.yesmenn.item.ColoredLightTubeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ColoredLightCompatRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LightsYesMenn.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LightsYesMenn.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LightsYesMenn.MODID);

    public static final DeferredBlock<ColoredLightBlock> COLORED_LIGHT =
            BLOCKS.register("colored_light",
                    () -> new ColoredLightBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5F, 6.0F)
                                    .sound(SoundType.GLASS)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> COLORED_LIGHT_ITEM =
            ITEMS.register("colored_light",
                    () -> new BlockItem(COLORED_LIGHT.get(), new Item.Properties()));

    public static final DeferredBlock<ColoredLightTubeBlock> COLORED_LIGHT_TUBE =
            BLOCKS.register("colored_light_tube",
                    () -> new ColoredLightTubeBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5F, 6.0F)
                                    .sound(SoundType.GLASS)
                                    .noOcclusion()));

    public static final DeferredItem<ColoredLightTubeItem> COLORED_LIGHT_TUBE_ITEM =
            ITEMS.register("colored_light_tube",
                    () -> new ColoredLightTubeItem(COLORED_LIGHT_TUBE.get(), new Item.Properties()));

    public static final DeferredBlock<ColoredLightPanelBlock> COLORED_LIGHT_PANEL =
            BLOCKS.register("colored_light_panel",
                    () -> new ColoredLightPanelBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5F, 6.0F)
                                    .sound(SoundType.GLASS)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> COLORED_LIGHT_PANEL_ITEM =
            ITEMS.register("colored_light_panel",
                    () -> new BlockItem(COLORED_LIGHT_PANEL.get(), new Item.Properties()));

    public static final DeferredBlock<ColoredLightSpotlightBlock> COLORED_LIGHT_SPOTLIGHT =
            BLOCKS.register("colored_light_spotlight",
                    () -> new ColoredLightSpotlightBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5F, 6.0F)
                                    .sound(SoundType.GLASS)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> COLORED_LIGHT_SPOTLIGHT_ITEM =
            ITEMS.register("colored_light_spotlight",
                    () -> new BlockItem(COLORED_LIGHT_SPOTLIGHT.get(), new Item.Properties()));

    public static final DeferredBlock<InvisibleColoredLightSpotlightBlock> INVISIBLE_COLORED_LIGHT_SPOTLIGHT =
            BLOCKS.register("invisible_colored_light_spotlight",
                    () -> new InvisibleColoredLightSpotlightBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(1.5F, 6.0F)
                                    .sound(SoundType.GLASS)
                                    .noOcclusion()));

    public static final DeferredItem<BlockItem> INVISIBLE_COLORED_LIGHT_SPOTLIGHT_ITEM =
            ITEMS.register("invisible_colored_light_spotlight",
                    () -> new BlockItem(INVISIBLE_COLORED_LIGHT_SPOTLIGHT.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColoredLightBlockEntity>> COLORED_LIGHT_ENTITY =
            BLOCK_ENTITY_TYPES.register("colored_light",
                    () -> BlockEntityType.Builder.of(
                            ColoredLightBlockEntity::new,
                            COLORED_LIGHT.get(),
                            COLORED_LIGHT_TUBE.get(),
                            COLORED_LIGHT_PANEL.get(),
                            COLORED_LIGHT_SPOTLIGHT.get(),
                            INVISIBLE_COLORED_LIGHT_SPOTLIGHT.get()
                    ).build(null));

    private ColoredLightCompatRegistry() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
