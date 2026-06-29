package com.lights.yesmenn.block;

import com.mojang.serialization.MapCodec;
import com.lights.yesmenn.compat.veil.ColoredLightCompatRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class InvisibleColoredPointLightBlock extends ColoredLightBlock {
    public static final MapCodec<InvisibleColoredPointLightBlock> CODEC =
            simpleCodec(InvisibleColoredPointLightBlock::new);
    private static final VoxelShape SHAPE = box(5, 5, 5, 11, 11, 11);

    public InvisibleColoredPointLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(POWERED, level.hasNeighborSignal(pos));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return canSeeShape(context) ? SHAPE : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    private static boolean canSeeShape(CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player) {
            return player.getMainHandItem().is(ColoredLightCompatRegistry.INVISIBLE_COLORED_POINTLIGHT_ITEM.get())
                    || player.getOffhandItem().is(ColoredLightCompatRegistry.INVISIBLE_COLORED_POINTLIGHT_ITEM.get());
        }
        return false;
    }
}