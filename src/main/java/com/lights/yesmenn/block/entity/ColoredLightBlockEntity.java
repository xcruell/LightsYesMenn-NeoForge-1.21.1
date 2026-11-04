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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

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
    public static final int DEFAULT_FLICKER = 0;
    public static final int DEFAULT_GLOW = 0;
    /** Warm-up time in tenths of a second (hanging lamp only), like an HQL lamp slowly reaching full brightness. */
    public static final int DEFAULT_WARMUP = 30;
    public static final int MAX_WARMUP = 100;
    private int color = DEFAULT_COLOR;
    /** Light radius/range in blocks; 0 = not set yet, use the legacy block state value. */
    private int radius;
    private int brightness = DEFAULT_BRIGHTNESS;
    private int spotlightAngle = DEFAULT_SPOTLIGHT_ANGLE;
    private int spotlightBrightness = DEFAULT_SPOTLIGHT_BRIGHTNESS;
    private int spotlightYaw = DEFAULT_SPOTLIGHT_YAW;
    private int spotlightPitch = DEFAULT_SPOTLIGHT_PITCH;
    private int spotlightSize = DEFAULT_SPOTLIGHT_SIZE;
    private int spotlightSoftness = DEFAULT_SPOTLIGHT_SOFTNESS;
    private int spotlightFocus = DEFAULT_SPOTLIGHT_FOCUS;
    /** 0-100: gentle bulb-like brightness flicker. */
    private int flicker = DEFAULT_FLICKER;
    /** 0-100: visible light beam in the air (Veil in-scattering, rendered by VILC). */
    private int glow = DEFAULT_GLOW;
    private int warmup = DEFAULT_WARMUP;
    /** Index into HangingLampBlock.WARMUP_TINTS; 0 = no warm-up color. */
    private int warmupTint;

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
        return radius > 0 ? radius : getBlockState().getValue(ColoredLightBlock.RADIUS);
    }

    private int clampRadius(int value) {
        return Math.max(1, Math.min(ColoredLightBlock.maxRadius(getBlockState()), value));
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

    public int getFlicker() {
        return flicker;
    }

    public int getGlow() {
        return glow;
    }

    public void setFlicker(int flicker) {
        int next = Math.max(0, Math.min(100, flicker));
        if (this.flicker == next) {
            return;
        }
        this.flicker = next;
        markUpdated();
    }

    public int getWarmup() {
        return warmup;
    }

    public int getWarmupTint() {
        return warmupTint;
    }

    public void setWarmupTint(int warmupTint) {
        int next = Math.floorMod(warmupTint, com.lights.yesmenn.block.HangingLampBlock.WARMUP_TINTS.length);
        if (this.warmupTint == next) {
            return;
        }
        this.warmupTint = next;
        markUpdated();
    }

    public void setWarmup(int warmup) {
        int next = Math.max(0, Math.min(MAX_WARMUP, warmup));
        if (this.warmup == next) {
            return;
        }
        this.warmup = next;
        markUpdated();
    }

    public void setGlow(int glow) {
        int next = Math.max(0, Math.min(100, glow));
        if (this.glow == next) {
            return;
        }
        this.glow = next;
        markUpdated();
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

        int clamped = clampRadius(radius);
        if (getRadius() == clamped && this.radius > 0) {
            return;
        }
        this.radius = clamped;
        refreshSpawnProtection();
        markUpdated();
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

        int clampedRadius = clampRadius(radius);
        int nextColor = color & 0xFFFFFF;
        BlockState state = getBlockState();
        boolean radiusChanged = getRadius() != clampedRadius || this.radius <= 0;
        boolean colorChanged = this.color != nextColor;
        boolean redstoneChanged = state.getValue(ColoredLightBlock.REDSTONE_INVERTED) != redstoneInverted;
        if (!radiusChanged && !colorChanged && !redstoneChanged) {
            return;
        }

        this.color = nextColor;
        this.radius = clampedRadius;
        refreshSpawnProtection();
        if (redstoneChanged) {
            level.setBlock(
                    worldPosition,
                    state.setValue(ColoredLightBlock.REDSTONE_INVERTED, redstoneInverted),
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
                spotlightFocus,
                flicker,
                glow,
                warmup,
                warmupTint);
    }

    public void setSpotlightConfig(int angle, int brightness, int yaw, int pitch) {
        setSpotlightConfig(angle, brightness, yaw, pitch, spotlightSize, spotlightSoftness, spotlightFocus);
    }

    public void setSpotlightConfig(int angle, int brightness, int yaw, int pitch, int size, int softness, int focus) {
        int nextAngle = Math.max(5, Math.min(
                com.lights.yesmenn.block.HangingLampBlock.maxSpotlightAngle(getBlockState()), angle));
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
        int previousColor = color;
        color = tag.contains("Color") ? tag.getInt("Color") & 0xFFFFFF : DEFAULT_COLOR;
        // Lights saved before the radius moved into the block entity keep their block state radius.
        radius = tag.contains("Radius") ? Math.max(0, tag.getInt("Radius")) : 0;
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
        flicker = Math.max(0, Math.min(100, tag.contains("Flicker") ? tag.getInt("Flicker") : DEFAULT_FLICKER));
        glow = Math.max(0, Math.min(100, tag.contains("Glow") ? tag.getInt("Glow") : DEFAULT_GLOW));
        warmup = Math.max(0, Math.min(MAX_WARMUP, tag.contains("Warmup") ? tag.getInt("Warmup") : DEFAULT_WARMUP));
        warmupTint = Math.floorMod(tag.getInt("WarmupTint"), com.lights.yesmenn.block.HangingLampBlock.WARMUP_TINTS.length);
        if (color != previousColor) {
            // Only a color change affects the baked block tint; other updates must not rebuild the chunk section.
            invokeClientRenderHook();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Color", color);
        if (radius > 0) {
            tag.putInt("Radius", radius);
        }
        tag.putInt("Brightness", brightness);
        tag.putInt("SpotlightAngle", spotlightAngle);
        tag.putInt("SpotlightBrightness", spotlightBrightness);
        tag.putInt("SpotlightYaw", spotlightYaw);
        tag.putInt("SpotlightPitch", spotlightPitch);
        tag.putInt("SpotlightSize", spotlightSize);
        tag.putInt("SpotlightSoftness", spotlightSoftness);
        tag.putInt("SpotlightFocus", spotlightFocus);
        tag.putInt("Flicker", flicker);
        tag.putInt("Glow", glow);
        tag.putInt("Warmup", warmup);
        tag.putInt("WarmupTint", warmupTint);
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

    private void refreshSpawnProtection() {
        if (level instanceof ServerLevel serverLevel) {
            FakeColoredLightManager.refresh(serverLevel, this);
        }
    }

    private void markUpdated() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void invokeClientLightHook(String method) {
        if (level == null || !level.isClientSide || !OptionalModCompat.coloredLightAvailable()) {
            return;
        }
        MethodHandle hook = "register".equals(method) ? ClientHooks.REGISTER_LIGHT : ClientHooks.UNREGISTER_LIGHT;
        if (hook == null) {
            return;
        }
        try {
            hook.invokeExact(this);
        } catch (Throwable exception) {
            throw new IllegalStateException("Could not update Veil colored light.", exception);
        }
    }

    private void invokeClientRenderHook() {
        if (level == null || !level.isClientSide || ClientHooks.REFRESH_TINT == null) {
            return;
        }
        try {
            ClientHooks.REFRESH_TINT.invokeExact(worldPosition);
        } catch (Throwable exception) {
            throw new IllegalStateException("Could not refresh colored light block tint.", exception);
        }
    }

    // Resolved once on first client use; dedicated servers never touch this holder.
    private static final class ClientHooks {
        private static final MethodHandle REGISTER_LIGHT = find(
                "com.lights.yesmenn.client.VeilColoredLightManager", "register",
                MethodType.methodType(void.class, ColoredLightBlockEntity.class));
        private static final MethodHandle UNREGISTER_LIGHT = find(
                "com.lights.yesmenn.client.VeilColoredLightManager", "unregister",
                MethodType.methodType(void.class, ColoredLightBlockEntity.class));
        private static final MethodHandle REFRESH_TINT = find(
                "com.lights.yesmenn.client.ColoredLightClientHooks", "refreshBlockTint",
                MethodType.methodType(void.class, BlockPos.class));

        private static MethodHandle find(String className, String name, MethodType type) {
            try {
                return MethodHandles.publicLookup().findStatic(Class.forName(className), name, type);
            } catch (ClassNotFoundException | LinkageError ignored) {
                return null;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Missing client hook " + className + "#" + name, exception);
            }
        }
    }
}
