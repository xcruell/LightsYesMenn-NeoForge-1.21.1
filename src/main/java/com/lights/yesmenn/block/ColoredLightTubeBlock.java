package com.lights.yesmenn.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ColoredLightTubeBlock extends ColoredLightBlock {
    public static final MapCodec<ColoredLightTubeBlock> CODEC = simpleCodec(ColoredLightTubeBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    private static final VoxelShape[] SHAPES = buildShapes();

    public ColoredLightTubeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(NORTH, false)
                .setValue(SOUTH, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        int key = facing.ordinal() << 2;
        if (state.getValue(NORTH)) {
            key |= 1;
        }
        if (state.getValue(SOUTH)) {
            key |= 2;
        }
        return SHAPES[key];
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing) {
            state = state.setValue(NORTH, connectsTo(neighborState, facing));
        } else if (direction == facing.getOpposite()) {
            state = state.setValue(SOUTH, connectsTo(neighborState, facing));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getClickedFace().getOpposite();
        return defaultBlockState()
                .setValue(POWERED, level.hasNeighborSignal(pos))
                .setValue(FACING, facing)
                .setValue(NORTH, connectsTo(level.getBlockState(pos.relative(facing)), facing))
                .setValue(SOUTH, connectsTo(level.getBlockState(pos.relative(facing.getOpposite())), facing));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, NORTH, SOUTH);
    }

    private boolean connectsTo(BlockState neighborState, Direction facing) {
        return neighborState.is(this)
                && neighborState.getValue(FACING).getAxis() == facing.getAxis();
    }

    private static VoxelShape tubeShape(Direction.Axis axis) {
        return switch (axis) {
            case X -> box(0, 7, 7, 16, 9, 9);
            case Y -> box(7, 0, 7, 9, 16, 9);
            case Z -> box(7, 7, 0, 9, 9, 16);
        };
    }

    private static VoxelShape capShape(Direction direction) {
        return switch (direction) {
            case DOWN -> box(6, 0, 6, 10, 2, 10);
            case UP -> box(6, 14, 6, 10, 16, 10);
            case NORTH -> box(6, 6, 0, 10, 10, 2);
            case SOUTH -> box(6, 6, 14, 10, 10, 16);
            case WEST -> box(0, 6, 6, 2, 10, 10);
            case EAST -> box(14, 6, 6, 16, 10, 10);
        };
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[Direction.values().length * 4];
        for (Direction facing : Direction.values()) {
            for (int connected = 0; connected < 4; connected++) {
                boolean northConnected = (connected & 1) != 0;
                boolean southConnected = (connected & 2) != 0;
                VoxelShape shape = tubeShape(facing.getAxis());
                if (!northConnected) {
                    shape = Shapes.or(shape, capShape(facing));
                }
                if (!southConnected) {
                    shape = Shapes.or(shape, capShape(facing.getOpposite()));
                }
                shapes[(facing.ordinal() << 2) | connected] = shape.optimize();
            }
        }
        return shapes;
    }
}
