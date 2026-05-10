package com.zxtangyuan.autosablephysics.util;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 受影响区域扫描计划生成器。
 *
 * <p>这个类只处理已经保存的 BlockPos 坐标集合，不读取 Level、不读取方块状态、不调用 Sable。
 * 因此它可以安全地放到异步线程中准备候选列表。真正的方块读取和装配仍由服务端主线程执行。</p>
 *
 * <p>0.1.7 起，扫描计划严格按三段式生成：</p>
 * <ol>
 *     <li>先扫描本次方块变化产生的影响立方体内的所有坐标；</li>
 *     <li>再扫描与本次影响立方体接触的历史受影响连通区域内的所有坐标；</li>
 *     <li>最后扫描这些受影响区域边界列向上/向下延伸出的坐标。</li>
 * </ol>
 */
public final class AffectedScanPlanner {
    private AffectedScanPlanner() {
    }

    public static CompletableFuture<PreparedScanPlan> prepareAsync(
            Set<Long> affectedSnapshot,
            BlockPos center,
            ScanReason reason,
            int currentImpactRadius,
            int verticalRange,
            int maxRegionBlocks,
            int maxBoundaryColumns,
            int maxPreparedCandidates
    ) {
        return CompletableFuture.supplyAsync(() -> prepare(
                affectedSnapshot,
                center,
                reason,
                currentImpactRadius,
                verticalRange,
                maxRegionBlocks,
                maxBoundaryColumns,
                maxPreparedCandidates
        ));
    }

    public static PreparedScanPlan prepare(
            Set<Long> affectedSnapshot,
            BlockPos center,
            ScanReason reason,
            int currentImpactRadius,
            int verticalRange,
            int maxRegionBlocks,
            int maxBoundaryColumns,
            int maxPreparedCandidates
    ) {
        LinkedHashSet<Long> allowed = new LinkedHashSet<>();
        LinkedHashSet<Long> candidates = new LinkedHashSet<>();

        // 第一阶段：本次破坏/放置产生的影响立方体内，所有坐标都进入允许集合与优先候选。
        // 这一步不依赖 affectedSnapshot，避免单方块/小物体因为没有历史受影响入口而漏扫。
        List<Long> currentImpact = collectCurrentImpactPositions(center, currentImpactRadius);
        currentImpact.sort(distanceComparator(center));
        addOrderedPositions(currentImpact, allowed, candidates, maxPreparedCandidates);

        // 第二阶段：找出与本次影响立方体接触的历史受影响连通区域。
        Set<Long> seeds = collectAffectedSeedsTouchingCurrentImpact(affectedSnapshot, currentImpact);
        Set<Long> connectedRegion = seeds.isEmpty()
                ? Set.of()
                : collectConnectedAffectedRegion(affectedSnapshot, seeds, maxRegionBlocks);

        if (!connectedRegion.isEmpty()) {
            List<Long> progressiveRegion = new ArrayList<>(connectedRegion);
            progressiveRegion.sort(distanceComparator(center));
            addOrderedPositions(progressiveRegion, allowed, candidates, maxPreparedCandidates);

            // 第三阶段：受影响连通区域边界列向上/向下延伸。
            List<Long> boundary = collectBoundaryColumns(connectedRegion, maxBoundaryColumns);
            boundary.sort(distanceComparator(center));

            int boundaryColumns = 0;
            for (long packedBoundary : boundary) {
                boundaryColumns++;
                BlockPos boundaryPos = BlockPos.of(packedBoundary);
                List<Long> verticalCandidates = new ArrayList<>();
                for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                    verticalCandidates.add(boundaryPos.offset(0, dy, 0).asLong());
                }
                verticalCandidates.sort(distanceComparator(center));
                addOrderedPositions(verticalCandidates, allowed, candidates, maxPreparedCandidates);
            }

            return new PreparedScanPlan(
                    toBlockPosList(candidates),
                    toBlockPosList(allowed),
                    Set.copyOf(allowed),
                    currentImpact.size(),
                    connectedRegion.size(),
                    boundaryColumns,
                    reason
            );
        }

        return new PreparedScanPlan(
                toBlockPosList(candidates),
                toBlockPosList(allowed),
                Set.copyOf(allowed),
                currentImpact.size(),
                0,
                0,
                reason
        );
    }

    private static void addOrderedPositions(
            List<Long> ordered,
            LinkedHashSet<Long> allowed,
            LinkedHashSet<Long> candidates,
            int maxPreparedCandidates
    ) {
        for (long packed : ordered) {
            allowed.add(packed);
            if (candidates.size() < maxPreparedCandidates) {
                candidates.add(packed);
            }
        }
    }

    private static List<BlockPos> toBlockPosList(LinkedHashSet<Long> candidates) {
        List<BlockPos> candidatePositions = new ArrayList<>(candidates.size());
        for (long packed : candidates) {
            candidatePositions.add(BlockPos.of(packed));
        }
        return candidatePositions;
    }

    private static List<Long> collectCurrentImpactPositions(BlockPos center, int radius) {
        List<Long> result = new ArrayList<>();
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            result.add(pos.asLong());
        }
        return result;
    }

    private static Set<Long> collectAffectedSeedsTouchingCurrentImpact(Set<Long> affectedSnapshot, List<Long> currentImpact) {
        if (affectedSnapshot.isEmpty()) {
            return Set.of();
        }

        Set<Long> seeds = new HashSet<>();
        for (long packed : currentImpact) {
            if (affectedSnapshot.contains(packed)) {
                seeds.add(packed);
            }

            // “与本次影响区域连接”的历史受影响区域：不仅包括影响立方体内部的 affected，
            // 也包括和影响立方体 6 面相邻的 affected 方块。
            BlockPos pos = BlockPos.of(packed);
            for (long neighbor : packedNeighbors(pos)) {
                if (affectedSnapshot.contains(neighbor)) {
                    seeds.add(neighbor);
                }
            }
        }
        return seeds;
    }

    private static Set<Long> collectConnectedAffectedRegion(Set<Long> affectedSnapshot, Set<Long> seeds, int maxRegionBlocks) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();

        for (long seed : seeds) {
            if (affectedSnapshot.contains(seed) && visited.add(seed)) {
                queue.addLast(seed);
            }
        }

        while (!queue.isEmpty() && visited.size() < maxRegionBlocks) {
            BlockPos current = BlockPos.of(queue.removeFirst());
            for (long neighbor : packedNeighbors(current)) {
                if (!affectedSnapshot.contains(neighbor) || !visited.add(neighbor)) {
                    continue;
                }
                queue.addLast(neighbor);
                if (visited.size() >= maxRegionBlocks) {
                    break;
                }
            }
        }

        return visited;
    }

    private static List<Long> collectBoundaryColumns(Set<Long> connectedRegion, int maxBoundaryColumns) {
        List<Long> boundary = new ArrayList<>();
        for (long packed : connectedRegion) {
            BlockPos pos = BlockPos.of(packed);
            boolean isBoundary = false;
            for (long neighbor : packedNeighbors(pos)) {
                if (!connectedRegion.contains(neighbor)) {
                    isBoundary = true;
                    break;
                }
            }
            if (isBoundary) {
                boundary.add(packed);
                if (boundary.size() >= maxBoundaryColumns) {
                    break;
                }
            }
        }
        return boundary;
    }

    private static long[] packedNeighbors(BlockPos pos) {
        return new long[]{
                pos.offset(1, 0, 0).asLong(),
                pos.offset(-1, 0, 0).asLong(),
                pos.offset(0, 1, 0).asLong(),
                pos.offset(0, -1, 0).asLong(),
                pos.offset(0, 0, 1).asLong(),
                pos.offset(0, 0, -1).asLong()
        };
    }

    private static Comparator<Long> distanceComparator(BlockPos center) {
        return Comparator
                .comparingLong((Long packed) -> chebyshevDistance(BlockPos.of(packed), center))
                .thenComparingLong(packed -> distanceSquared(BlockPos.of(packed), center));
    }

    private static long chebyshevDistance(BlockPos a, BlockPos b) {
        long dx = Math.abs((long) a.getX() - b.getX());
        long dy = Math.abs((long) a.getY() - b.getY());
        long dz = Math.abs((long) a.getZ() - b.getZ());
        return Math.max(dx, Math.max(dy, dz));
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public record PreparedScanPlan(
            List<BlockPos> candidateOrigins,
            List<BlockPos> orderedAssemblyPositions,
            Set<Long> allowedAssemblyPositions,
            int currentImpactBlocks,
            int connectedAffectedBlocks,
            int boundaryColumns,
            ScanReason reason
    ) {
        public static PreparedScanPlan empty() {
            return new PreparedScanPlan(List.of(), List.of(), Set.of(), 0, 0, 0, ScanReason.BLOCK_BREAK);
        }

        public boolean isEmpty() {
            return candidateOrigins.isEmpty() && orderedAssemblyPositions.isEmpty();
        }
    }
}
