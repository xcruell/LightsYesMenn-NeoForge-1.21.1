package com.lights.yesmenn.block;

import com.mojang.serialization.MapCodec;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ColoredLightSpotlightBlock extends ColoredLightBlock {
    public static final MapCodec<ColoredLightSpotlightBlock> CODEC = simpleCodec(ColoredLightSpotlightBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty BEAM_FACING = DirectionProperty.create("beam_facing");

    public ColoredLightSpotlightBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(BEAM_FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        Direction mountFace = context.getClickedFace();
        return defaultBlockState()
                .setValue(POWERED, level.hasNeighborSignal(pos))
                .setValue(FACING, mountFace)
                .setValue(BEAM_FACING, placementBeamDirection(context, mountFace, player));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> box(4, 0, 4, 12, 10, 12);
            case DOWN -> box(4, 6, 4, 12, 16, 12);
            case NORTH -> box(4, 4, 6, 12, 12, 16);
            case SOUTH -> box(4, 4, 0, 12, 12, 10);
            case WEST -> box(6, 4, 4, 16, 12, 12);
            case EAST -> box(0, 4, 4, 10, 12, 12);
        };
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ColoredLightBlockEntity light) {
            light.setSpotlightConfig(
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_ANGLE,
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_BRIGHTNESS,
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_YAW,
                    defaultDisplayPitch(state),
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_SIZE,
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_SOFTNESS,
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_FOCUS);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, BEAM_FACING);
    }

    private static Direction placementBeamDirection(BlockPlaceContext context, Direction mountFace, Player player) {
        Direction blockedDirection = mountFace.getOpposite();
        for (Direction direction : context.getNearestLookingDirections()) {
            Direction candidate = player != null && player.isShiftKeyDown() ? direction.getOpposite() : direction;
            if (candidate != blockedDirection) {
                return candidate;
            }
        }
        return mountFace;
    }

    public static int defaultDisplayPitch(BlockState state) {
        Direction mountFace = state.getValue(FACING);
        Direction beamFacing = state.getValue(BEAM_FACING);
        Direction localBeam = worldToMountedLocal(mountFace, beamFacing);
        return localBeam.getAxis() == Direction.Axis.Y ? 0 : 90;
    }

    private static Direction worldToMountedLocal(Direction mountFace, Direction direction) {
        int x = direction.getStepX();
        int y = direction.getStepY();
        int z = direction.getStepZ();
        return switch (mountFace) {
            case UP -> directionFromDelta(x, y, z);
            case DOWN -> directionFromDelta(x, -y, -z);
            case NORTH -> directionFromDelta(x, -z, y);
            case SOUTH -> directionFromDelta(x, z, -y);
            case WEST -> directionFromDelta(y, -x, z);
            case EAST -> directionFromDelta(-y, x, z);
        };
    }

    private static Direction directionFromDelta(int x, int y, int z) {
        Direction direction = Direction.fromDelta(x, y, z);
        return direction != null ? direction : Direction.NORTH;
    }
}
