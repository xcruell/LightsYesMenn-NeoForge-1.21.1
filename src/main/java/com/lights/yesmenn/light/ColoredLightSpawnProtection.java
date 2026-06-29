package com.lights.yesmenn.light;

import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

public final class ColoredLightSpawnProtection {
    private ColoredLightSpawnProtection() {
    }

    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        if (FakeColoredLightManager.preventsMonsterSpawn(event.getLevel().getLevel(), event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }
}
