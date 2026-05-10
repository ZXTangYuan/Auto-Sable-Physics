package com.zxtangyuan.autosablephysics.util;

import com.zxtangyuan.autosablephysics.AutoSablePhysics;
import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import com.zxtangyuan.autosablephysics.data.AffectedBlockData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


/**
 * 自动扫描队列：负责延迟、冷却和异步计划准备。
 * Automatic scan queue: handles delays, cooldowns, and asynchronous plan preparation.
 */
public final class AssemblyQueue {
    private static final Map<ResourceKey<Level>, ArrayDeque<PendingAssemblyScan>> PENDING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> RECENTLY_QUEUED = new HashMap<>();

    private AssemblyQueue() {
    }

    public static void resetAll() {
        PENDING.clear();
        RECENTLY_QUEUED.clear();
    }

    public static void enqueue(ServerLevel level, BlockPos center, ScanReason reason) {
        if (!ASPServerConfig.ENABLED.get()) {
            return;
        }

        int currentTick = level.getServer().getTickCount();
        int delay = delayForReason(reason);
        int runAtTick = currentTick + delay;
        BlockPos immutableCenter = center.immutable();

        Map<BlockPos, Integer> recent = RECENTLY_QUEUED.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        Integer oldExpireTick = recent.get(immutableCenter);
        if (oldExpireTick != null && oldExpireTick >= currentTick) {
            return;
        }
        recent.put(immutableCenter, currentTick + ASPServerConfig.SAME_POSITION_COOLDOWN_TICKS.get());

        CompletableFuture<AffectedScanPlanner.PreparedScanPlan> preparedPlanFuture = prepareAffectedPlan(level, immutableCenter, reason);

        PENDING.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>())
                .addLast(new PendingAssemblyScan(level.dimension(), immutableCenter, runAtTick, reason, preparedPlanFuture));
    }

    public static void tick(ServerLevel level) {
        ArrayDeque<PendingAssemblyScan> queue = PENDING.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            cleanupRecent(level);
            return;
        }

        int currentTick = level.getServer().getTickCount();
        int budget = Math.min(
                ASPServerConfig.MAX_JOBS_PER_LEVEL_TICK.get(),
                ASPServerConfig.AUTO_MAX_JOBS_PER_LEVEL_TICK.get()
        );

        while (budget-- > 0 && !queue.isEmpty()) {
            PendingAssemblyScan scan = queue.peekFirst();
            if (scan.runAtTick() > currentTick) {
                break;
            }

            CompletableFuture<AffectedScanPlanner.PreparedScanPlan> future = scan.preparedPlanFuture();
            if (future != null && !future.isDone()) {
                // 中文：不阻塞主线程。异步候选准备通常很快；没准备好就下个 tick 再处理。
                // EN: Do not block the main thread; if async planning is not ready, retry next tick.
                break;
            }

            queue.removeFirst();
            if (ASPServerConfig.IGNORE_EVENTS_INSIDE_SABLE_SUB_LEVELS.get()
                    && AssemblyFilters.isInsideSableSubLevel(level, scan.center())) {
                continue;
            }

            AffectedScanPlanner.PreparedScanPlan preparedPlan = null;
            if (future != null) {
                try {
                    preparedPlan = future.join();
                } catch (Throwable throwable) {
                    AutoSablePhysics.LOGGER.warn("Failed to prepare affected scan plan near {} in {}. Falling back to legacy candidates.", scan.center(), level.dimension().location(), throwable);
                }
            }

            SableAssemblyService.scanAndAssemble(level, scan.center(), scan.reason(), -1, preparedPlan);
        }

        if (queue.isEmpty()) {
            PENDING.remove(level.dimension());
        }

        cleanupRecent(level);
    }

    private static int delayForReason(ScanReason reason) {
        int baseDelay = ASPServerConfig.DELAY_TICKS.get();
        return switch (reason) {
            case EXPLOSION, PISTON, LIVING_DESTROY_BLOCK, ENDERMAN_CARRY, FALLING_BLOCK,
                 FLUID_PLACE_BLOCK, FARMLAND_TRAMPLE, TOOL_MODIFICATION, NEIGHBOR_PHYSICS, SUBLEVEL_RESTORE ->
                    baseDelay + ASPServerConfig.NON_PLAYER_EVENT_STABILIZATION_DELAY_TICKS.get();
            default -> baseDelay;
        };
    }

    private static CompletableFuture<AffectedScanPlanner.PreparedScanPlan> prepareAffectedPlan(ServerLevel level, BlockPos center, ScanReason reason) {
        if (!ASPServerConfig.USE_AFFECTED_BLOCK_STATE.get()) {
            return null;
        }

        Set<Long> snapshot = AffectedBlockData.get(level).snapshot();
        int impactRadius = (reason == ScanReason.BLOCK_PLACE || reason == ScanReason.SMALL_OBJECT_CHECK || reason == ScanReason.SUBLEVEL_RESTORE)
                ? ASPServerConfig.AFFECTED_RADIUS_ON_PLACE.get()
                : ASPServerConfig.AFFECTED_RADIUS_ON_BREAK.get();

        if (ASPServerConfig.ASYNC_PREPARE_AFFECTED_CANDIDATES.get()) {
            return AffectedScanPlanner.prepareAsync(
                    snapshot,
                    center,
                    reason,
                    impactRadius,
                    ASPServerConfig.AFFECTED_BOUNDARY_VERTICAL_RANGE.get(),
                    ASPServerConfig.MAX_AFFECTED_REGION_BLOCKS_PER_SCAN.get(),
                    ASPServerConfig.MAX_AFFECTED_BOUNDARY_COLUMNS_PER_SCAN.get(),
                    ASPServerConfig.MAX_PREPARED_CANDIDATES_PER_SCAN.get()
            );
        }

        return CompletableFuture.completedFuture(AffectedScanPlanner.prepare(
                snapshot,
                center,
                reason,
                impactRadius,
                ASPServerConfig.AFFECTED_BOUNDARY_VERTICAL_RANGE.get(),
                ASPServerConfig.MAX_AFFECTED_REGION_BLOCKS_PER_SCAN.get(),
                ASPServerConfig.MAX_AFFECTED_BOUNDARY_COLUMNS_PER_SCAN.get(),
                ASPServerConfig.MAX_PREPARED_CANDIDATES_PER_SCAN.get()
        ));
    }

    private static void cleanupRecent(ServerLevel level) {
        Map<BlockPos, Integer> recent = RECENTLY_QUEUED.get(level.dimension());
        if (recent == null || recent.size() < 2048) {
            return;
        }

        int currentTick = level.getServer().getTickCount();
        Iterator<Map.Entry<BlockPos, Integer>> iterator = recent.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < currentTick) {
                iterator.remove();
            }
        }

        if (recent.isEmpty()) {
            RECENTLY_QUEUED.remove(level.dimension());
        }
    }
}
