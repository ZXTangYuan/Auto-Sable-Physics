package com.zxtangyuan.autosablephysics.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.concurrent.CompletableFuture;


/**
 * 等待执行的自动扫描任务。
 * Pending automatic scan job.
 */
public record PendingAssemblyScan(
        ResourceKey<Level> dimension,
        BlockPos center,
        int runAtTick,
        ScanReason reason,
        CompletableFuture<AffectedScanPlanner.PreparedScanPlan> preparedPlanFuture
) {
}
