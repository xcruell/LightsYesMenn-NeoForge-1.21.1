package com.lights.yesmenn.block.entity;

import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.ColoredLightSpotlightBlock;
import com.lights.yesmenn.compat.OptionalModCompat;
import com.lights.yesmenn.compat.veil.ColoredLightCompatRegistry;
import com.lights.yesmenn.light.FakeColoredLightManager;
import com.lights.yesmenn.network.OpenColoredLightScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

public class ColoredLightBlockEntity extends BlockEntity {
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    public static final int DEFAULT_BRIGHTNESS = 200;
    public static final int DEFAULT_SPOTLIGHT_ANGLE = 29;
    public static final int DEFAULT_SPOTLIGHT_BRIGHTNESS = 400;
    public static final int DEFAULT_SPOTLIGHT_YAW = 0;
    public static final int DEFAULT_SPOTLIGHT_PITCH = 0;
    public static final int DEFAULT_SPOTLIGHT_SIZE = 18;
    public static final int DEFAULT_SPOTLIGHT_SOFTNESS = 8;
    public static final int DEFAULT_SPOTLIGHT_FOCUS = 100;
    private int color = DEFAULT_COLOR;
    private int brightness = DEFAULT_BRIGHTNESS;
    private int spotlightAngle = DEFAULT_SPOTLIGHT_ANGLE;
    private int spotlightBrightness = DEFAULT_SPOTLIGHT_BRIGHTNESS;
    private int spotlightYaw = DEFAULT_SPOTLIGHT_YAW;
    private int spotlightPitch = DEFAULT_SPOTLIGHT_PITCH;
    private int spotlightSize = DEFAULT_SPOTLIGHT_SIZE;
    private int spotlightSoftness = DEFAULT_SPOTLIGHT_SOFTNESS;
    private int spotlightFocus = DEFAULT_SPOTLIGHT_FOCUS;

    public ColoredLightBlockEntity(BlockPos pos, BlockState state) {
        this(ColoredLightCompatRegistry.COLORED_LIGHT_ENTITY.get(), pos, state);
    }

    protected ColoredLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getColor() {
        return color;
    }

    public int getRadius() {
        return getBlockState().getValue(ColoredLightBlock.RADIUS);
    }

    public boolean isRedstoneInverted() {
        return getBlockState().getValue(ColoredLightBlock.REDSTONE_INVERTED);
    }

    public int getBrightness() {
        return brightness;
    }

    public int getSpotlightAngle() {
        return spotlightAngle;
    }

    public int getSpotlightBrightness() {
        return spotlightBrightness;
    }

    public int getSpotlightYaw() {
        return spotlightYaw;
    }

    public int getSpotlightPitch() {
        return spotlightPitch;
    }

    public int getSpotlightSize() {
        return spotlightSize;
    }

    public int getSpotlightSoftness() {
        return spotlightSoftness;
    }

    public int getSpotlightFocus() {
        return spotlightFocus;
    }

    public void setColor(int color) {
        int nextColor = color & 0xFFFFFF;
        if (this.color == nextColor) {
            return;
        }
        this.color = nextColor;
        markUpdated();
    }

    public void setRadius(int radius) {
        if (level == null) {
            return;
        }

        int clamped = Math.max(1, Math.min(32, radius));
        BlockState state = getBlockState();
        if (state.getValue(ColoredLightBlock.RADIUS) == clamped) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(ColoredLightBlock.RADIUS, clamped), Block.UPDATE_CLIENTS);
        setChanged();
    }

    public void setBrightness(int brightness) {
        int nextBrightness = Math.max(25, Math.min(800, brightness));
        if (this.brightness == nextBrightness) {
            return;
        }
        this.brightness = nextBrightness;
        markUpdated();
    }

    public void setConfig(int radius, int color) {
        setConfig(radius, color, isRedstoneInverted());
    }

    public void setConfig(int radius, int color, boolean redstoneInverted) {
        if (level == null) {
            return;
        }

        int clampedRadius = Math.max(1, Math.min(32, radius));
        int nextColor = color & 0xFFFFFF;
        BlockState state = getBlockState();
        boolean radiusChanged = state.getValue(ColoredLightBlock.RADIUS) != clampedRadius;
        boolean colorChanged = this.color != nextColor;
        boolean redstoneChanged = state.getValue(ColoredLightBlock.REDSTONE_INVERTED) != redstoneInverted;
        if (!radiusChanged && !colorChanged && !redstoneChanged) {
            return;
        }

        this.color = nextColor;
        if (radiusChanged || redstoneChanged) {
            level.setBlock(
                    worldPosition,
                    state.setValue(ColoredLightBlock.RADIUS, clampedRadius)
                            .setValue(ColoredLightBlock.REDSTONE_INVERTED, redstoneInverted),
                    Block.UPDATE_CLIENTS);
        }
        markUpdated();
    }

    public OpenColoredLightScreenPayload createOpenScreenPayload() {
        return new OpenColoredLightScreenPayload(
                worldPosition,
                getRadius(),
                color,
                brightness,
                isRedstoneInverted(),
                getBlockState().getBlock() instanceof com.lights.yesmenn.block.ColoredLightSpotlightBlock,
                spotlightAngle,
                spotlightBrightness,
                spotlightYaw,
                spotlightPitch,
                spotlightSize,
                spotlightSoftness,
                spotlightFocus);
    }

    public void setSpotlightConfig(int angle, int brightness, int yaw, int pitch) {
        setSpotlightConfig(angle, brightness, yaw, pitch, spotlightSize, spotlightSoftness, spotlightFocus);
    }

    public void setSpotlightConfig(int angle, int brightness, int yaw, int pitch, int size, int softness, int focus) {
        int nextAngle = Math.max(5, Math.min(75, angle));
        int nextBrightness = Math.max(25, Math.min(800, brightness));
        int nextYaw = Math.max(-180, Math.min(180, yaw));
        int nextPitch = Math.max(-90, Math.min(90, pitch));
        int nextSize = Math.max(2, Math.min(60, size));
        int nextSoftness = Math.max(1, Math.min(50, softness));
        int nextFocus = Math.max(25, Math.min(500, focus));
        if (spotlightAngle == nextAngle
                && spotlightBrightness == nextBrightness
                && spotlightYaw == nextYaw
                && spotlightPitch == nextPitch
                && spotlightSize == nextSize
                && spotlightSoftness == nextSoftness
                && spotlightFocus == nextFocus) {
            return;
        }
        spotlightAngle = nextAngle;
        spotlightBrightness = nextBrightness;
        spotlightYaw = nextYaw;
        spotlightPitch = nextPitch;
        spotlightSize = nextSize;
        spotlightSoftness = nextSoftness;
        spotlightFocus = nextFocus;
        markUpdated();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            FakeColoredLightManager.register(serverLevel, this);
        }
        invokeClientLightHook("register");
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            FakeColoredLightManager.unregister(serverLevel, worldPosition);
        }
        invokeClientLightHook("unregister");
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        color = tag.contains("Color") ? tag.getInt("Color") & 0xFFFFFF : DEFAULT_COLOR;
        spotlightAngle = tag.contains("SpotlightAngle") ? tag.getInt("SpotlightAngle") : DEFAULT_SPOTLIGHT_ANGLE;
        spotlightBrightness = tag.contains("SpotlightBrightness")
                ? tag.getInt("SpotlightBrightness")
                : DEFAULT_SPOTLIGHT_BRIGHTNESS;
        int fallbackBrightness = getBlockState().getBlock() instanceof ColoredLightSpotlightBlock
                ? spotlightBrightness
                : DEFAULT_BRIGHTNESS;
        brightness = Math.max(25, Math.min(800,
                tag.contains("Brightness") ? tag.getInt("Brightness") : fallbackBrightness));
        spotlightYaw = tag.contains("SpotlightYaw") ? tag.getInt("SpotlightYaw") : DEFAULT_SPOTLIGHT_YAW;
        spotlightPitch = tag.contains("SpotlightPitch") ? tag.getInt("SpotlightPitch") : DEFAULT_SPOTLIGHT_PITCH;
        spotlightSize = tag.contains("SpotlightSize") ? tag.getInt("SpotlightSize") : DEFAULT_SPOTLIGHT_SIZE;
        spotlightSoftness = tag.contains("SpotlightSoftness")
                ? tag.getInt("SpotlightSoftness")
                : DEFAULT_SPOTLIGHT_SOFTNESS;
        spotlightFocus = tag.contains("SpotlightFocus") ? tag.getInt("SpotlightFocus") : DEFAULT_SPOTLIGHT_FOCUS;
        invokeClientRenderHook();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Color", color);
        tag.putInt("Brightness", brightness);
        tag.putInt("SpotlightAngle", spotlightAngle);
        tag.putInt("SpotlightBrightness", spotlightBrightness);
        tag.putInt("SpotlightYaw", spotlightYaw);
        tag.putInt("SpotlightPitch", spotlightPitch);
        tag.putInt("SpotlightSize", spotlightSize);
        tag.putInt("SpotlightSoftness", spotlightSoftness);
        tag.putInt("SpotlightFocus", spotlightFocus);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            FakeColoredLightManager.markDirty(serverLevel);
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void invokeClientLightHook(String method) {
        if (level == null || !level.isClientSide || !OptionalModCompat.coloredLightAvailable()) {
            return;
        }
        try {
            Class<?> manager = Class.forName("com.lights.yesmenn.client.VeilColoredLightManager");
            manager.getMethod(method, ColoredLightBlockEntity.class).invoke(null, this);
        } catch (ClassNotFoundException ignored) {
            // Dedicated servers never load the client manager.
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not update Veil colored light.", exception);
        }
    }

    private void invokeClientRenderHook() {
        if (level == null || !level.isClientSide) {
            return;
        }
        try {
            Class<?> hooks = Class.forName("com.lights.yesmenn.client.ColoredLightClientHooks");
            hooks.getMethod("refreshBlockTint", BlockPos.class).invoke(null, worldPosition);
        } catch (ClassNotFoundException ignored) {
            // Dedicated servers never load the client hooks.
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not refresh colored light block tint.", exception);
        }
    }
}
