package com.lights.yesmenn.block;

import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Lamp hanging from a ceiling. Internally a spot light that always points straight down; the shade lets it
 * cover almost a half sphere, so its cone may be much wider than a regular spotlight's.
 */
public class HangingLampBlock extends ColoredLightSpotlightBlock {
    public static final MapCodec<HangingLampBlock> CODEC = simpleCodec(HangingLampBlock::new);
    /** Cone half-angle limit in degrees (a regular spotlight stops at 75). */
    public static final int MAX_ANGLE = 88;
    public static final int DEFAULT_ANGLE = 80;
    public static final int DEFAULT_SOFTNESS = 30;
    public static final int DEFAULT_SIZE = 12;
    /**
     * Warm-up glow colors (sRGB): 0 = off (the lamp's own color the whole time), then blue, red, yellow, green, white.
     * The light starts in this color and blends into the lamp color while it warms up, like an HQL lamp.
     */
    public static final int[] WARMUP_TINTS = {-1, 0x5A8CFF, 0xFF4A30, 0xFFD050, 0x5AFF78, 0xFFFFFF};
    private static final VoxelShape SHAPE = Shapes.or(
            box(3, 0, 3, 13, 7, 13),
            box(7, 7, 7, 9, 16, 9));

    public HangingLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.DOWN)
                .setValue(BEAM_FACING, Direction.DOWN));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState()
                .setValue(POWERED, level.hasNeighborSignal(pos))
                .setValue(FACING, Direction.DOWN)
                .setValue(BEAM_FACING, Direction.DOWN);
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ColoredLightBlockEntity light) {
            light.setSpotlightConfig(
                    DEFAULT_ANGLE,
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_BRIGHTNESS,
                    0,
                    0,
                    DEFAULT_SIZE,
                    DEFAULT_SOFTNESS,
                    ColoredLightBlockEntity.DEFAULT_SPOTLIGHT_FOCUS);
        }
    }

    /** sRGB tint for the given warm-up tint index, or -1 when it is off. */
    public static int warmupTint(int index) {
        return index > 0 && index < WARMUP_TINTS.length ? WARMUP_TINTS[index] : -1;
    }

    /** Largest cone half-angle the given light block supports. */
    public static int maxSpotlightAngle(BlockState state) {
        return state.getBlock() instanceof HangingLampBlock ? MAX_ANGLE : 75;
    }
}
