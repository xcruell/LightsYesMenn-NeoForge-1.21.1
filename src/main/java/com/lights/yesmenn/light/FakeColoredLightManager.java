package com.lights.yesmenn.light;

import com.lights.yesmenn.block.ColoredLightBlock;
import com.lights.yesmenn.block.entity.ColoredLightBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side monster spawn protection around enabled colored lights.
 *
 * <p>Coverage index: every light is entered into each chunk its radius reaches. That costs a few hundred map
 * entries for a 128 block light, but only when a light loads or its radius changes. A spawn check then needs a
 * single map lookup for the spawn chunk plus a distance test against the few lights covering it, independent of the
 * light radius (the old check scanned all chunks within the maximum radius on every spawn attempt).
 */
public final class FakeColoredLightManager {
    private static final Map<ServerLevel, LevelData> LEVELS = new WeakHashMap<>();

    private FakeColoredLightManager() {
    }

    public static synchronized void register(ServerLevel level, ColoredLightBlockEntity light) {
        LevelData data = levelData(level);
        long pos = light.getBlockPos().asLong();
        data.deindex(pos);
        data.lights.put(pos, light);
        data.index(light.getBlockPos(), light.getRadius());
    }

    /** Re-indexes a light after its radius changed. */
    public static synchronized void refresh(ServerLevel level, ColoredLightBlockEntity light) {
        LevelData data = LEVELS.get(level);
        long pos = light.getBlockPos().asLong();
        if (data == null || !data.lights.containsKey(pos)) {
            return;
        }
        if (data.indexedRadius.get(pos) != light.getRadius()) {
            data.deindex(pos);
            data.index(light.getBlockPos(), light.getRadius());
        }
    }

    public static synchronized void unregister(ServerLevel level, BlockPos pos) {
        LevelData data = LEVELS.get(level);
        if (data == null) {
            return;
        }
        long packedPos = pos.asLong();
        data.deindex(packedPos);
        data.lights.remove(packedPos);
        if (data.lights.isEmpty()) {
            LEVELS.remove(level);
        }
    }

    public static synchronized boolean preventsMonsterSpawn(ServerLevel level, BlockPos pos) {
        LevelData data = LEVELS.get(level);
        if (data == null) {
            return false;
        }
        LongOpenHashSet covering = data.coverage.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        if (covering == null) {
            return false;
        }

        LongIterator iterator = covering.iterator();
        while (iterator.hasNext()) {
            long packedLightPos = iterator.nextLong();
            ColoredLightBlockEntity light = data.lights.get(packedLightPos);
            if (light == null || light.isRemoved()) {
                // Stale entry (unloaded without unregister); dropped lazily on its next index change.
                continue;
            }
            if (ColoredLightBlock.isLightEnabled(light.getBlockState())
                    && light.getBlockPos().distManhattan(pos) <= light.getRadius()) {
                return true;
            }
        }
        return false;
    }

    private static LevelData levelData(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new LevelData());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    private static final class LevelData {
        private final Long2ObjectOpenHashMap<ColoredLightBlockEntity> lights = new Long2ObjectOpenHashMap<>();
        /** Chunk -> positions of the lights whose radius reaches into it. */
        private final Long2ObjectOpenHashMap<LongOpenHashSet> coverage = new Long2ObjectOpenHashMap<>();
        /** Light position -> radius it is currently indexed with. */
        private final Long2IntOpenHashMap indexedRadius = new Long2IntOpenHashMap();

        private LevelData() {
            indexedRadius.defaultReturnValue(-1);
        }

        private void index(BlockPos pos, int radius) {
            long packedPos = pos.asLong();
            indexedRadius.put(packedPos, radius);
            int minChunkX = (pos.getX() - radius) >> 4;
            int maxChunkX = (pos.getX() + radius) >> 4;
            int minChunkZ = (pos.getZ() - radius) >> 4;
            int maxChunkZ = (pos.getZ() + radius) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    coverage.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new LongOpenHashSet()).add(packedPos);
                }
            }
        }

        private void deindex(long packedPos) {
            int radius = indexedRadius.remove(packedPos);
            if (radius < 0) {
                return;
            }
            BlockPos pos = BlockPos.of(packedPos);
            int minChunkX = (pos.getX() - radius) >> 4;
            int maxChunkX = (pos.getX() + radius) >> 4;
            int minChunkZ = (pos.getZ() - radius) >> 4;
            int maxChunkZ = (pos.getZ() + radius) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long key = chunkKey(chunkX, chunkZ);
                    LongOpenHashSet covering = coverage.get(key);
                    if (covering != null && covering.remove(packedPos) && covering.isEmpty()) {
                        coverage.remove(key);
                    }
                }
            }
        }
    }
}
