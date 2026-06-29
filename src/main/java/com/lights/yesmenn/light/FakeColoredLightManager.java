package com.lights.yesmenn.light;

import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

public final class FakeColoredLightManager {
    private static final int MAX_RADIUS = 32;
    private static final Map<ServerLevel, LevelData> LEVELS = new WeakHashMap<>();

    private FakeColoredLightManager() {
    }

    public static synchronized void register(ServerLevel level, ColoredLightBlockEntity light) {
        LevelData data = levelData(level);
        long pos = light.getBlockPos().asLong();
        data.lights.put(pos, light);
        data.lightChunks.computeIfAbsent(chunkKey(light.getBlockPos()), ignored -> new LongOpenHashSet()).add(pos);
    }

    public static synchronized void unregister(ServerLevel level, BlockPos pos) {
        LevelData data = LEVELS.get(level);
        if (data != null) {
            long packedPos = pos.asLong();
            data.lights.remove(packedPos);
            LongOpenHashSet chunkLights = data.lightChunks.get(chunkKey(pos));
            if (chunkLights != null) {
                chunkLights.remove(packedPos);
                if (chunkLights.isEmpty()) {
                    data.lightChunks.remove(chunkKey(pos));
                }
            }
            if (data.lights.isEmpty()) {
                LEVELS.remove(level);
            }
        }
    }

    public static void markDirty(ServerLevel level) {
    }

    public static void markDirty(ServerLevel level, BlockPos changedPos) {
    }

    public static synchronized boolean preventsMonsterSpawn(ServerLevel level, BlockPos pos) {
        LevelData data = LEVELS.get(level);
        if (data == null || data.lights.isEmpty()) {
            return false;
        }

        int minChunkX = (pos.getX() - MAX_RADIUS) >> 4;
        int maxChunkX = (pos.getX() + MAX_RADIUS) >> 4;
        int minChunkZ = (pos.getZ() - MAX_RADIUS) >> 4;
        int maxChunkZ = (pos.getZ() + MAX_RADIUS) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LongOpenHashSet chunkLights = data.lightChunks.get(ChunkPos.asLong(chunkX, chunkZ));
                if (chunkLights == null || chunkLights.isEmpty()) {
                    continue;
                }

                LongIterator iterator = chunkLights.iterator();
                while (iterator.hasNext()) {
                    long packedLightPos = iterator.nextLong();
                    ColoredLightBlockEntity light = data.lights.get(packedLightPos);
                    if (light == null || light.isRemoved()) {
                        iterator.remove();
                        data.lights.remove(packedLightPos);
                        continue;
                    }
                    if (!ColoredLightBlock.isLightEnabled(light.getBlockState())) {
                        continue;
                    }

                    BlockPos lightPos = light.getBlockPos();
                    int radius = light.getRadius();
                    if (lightPos.distManhattan(pos) <= radius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static LevelData levelData(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new LevelData());
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static final class LevelData {
        private final Long2ObjectOpenHashMap<ColoredLightBlockEntity> lights = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<LongOpenHashSet> lightChunks = new Long2ObjectOpenHashMap<>();
    }
}
