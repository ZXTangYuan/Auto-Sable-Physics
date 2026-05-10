package com.zxtangyuan.autosablephysics.util;

import com.zxtangyuan.autosablephysics.AutoSablePhysics;
import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 核心扫描服务：把受影响坐标拆成组件，并提交给延迟物理化队列。
 * Core scan service: splits affected positions into components and submits them to delayed assembly.
 */
public final class SableAssemblyService {
    private SableAssemblyService() {
    }

    public static AssemblyStats scanAndAssemble(ServerLevel level, BlockPos center, ScanReason reason, int manualRadiusOverride) {
        return scanAndAssemble(level, center, reason, manualRadiusOverride, null);
    }

    /**
     * 扫描一个中心点附近的候选方块，并把符合条件的有限连通结构装配为 Sable sub-level。
     *
     * <p>0.1.4 起，自动扫描优先使用受影响区域计划。受影响计划来自 SavedData 中保存的玩家行为影响坐标，
     * 这样未受影响的世界生成地形默认被视为稳定支撑，不会作为自动扫描候选，也不会被 Sable BFS 横向吞进整片地形。</p>
     *
     * @param manualRadiusOverride 大于 0 时覆盖配置半径，供命令使用。
     * @param preparedPlan 自动队列异步/同步准备出的受影响区域候选；手动命令传 null，走旧半径扫描。
     */
    public static AssemblyStats scanAndAssemble(
            ServerLevel level,
            BlockPos center,
            ScanReason reason,
            int manualRadiusOverride,
            @Nullable AffectedScanPlanner.PreparedScanPlan preparedPlan
    ) {
        if (!ASPServerConfig.ENABLED.get()) {
            return AssemblyStats.EMPTY;
        }

        long startNanos = System.nanoTime();

        boolean manualScan = reason == ScanReason.MANUAL_COMMAND;
        boolean smallObjectOnly = reason == ScanReason.SMALL_OBJECT_CHECK;
        if (!manualScan
                && ASPServerConfig.IGNORE_EVENTS_INSIDE_SABLE_SUB_LEVELS.get()
                && AssemblyFilters.isInsideSableSubLevel(level, center)) {
            return AssemblyStats.EMPTY;
        }

        boolean useAffectedPlan = !manualScan && !smallObjectOnly && ASPServerConfig.USE_AFFECTED_BLOCK_STATE.get();
        if (useAffectedPlan && (preparedPlan == null || preparedPlan.isEmpty())) {
            // 受影响系统开启时，自动扫描不再回退到旧的半径全候选扫描，避免误把自然地形当作可物理化结构。
            return AssemblyStats.EMPTY;
        }

        int triggerRadius = manualRadiusOverride > 0
                ? manualRadiusOverride
                : (manualScan ? ASPServerConfig.MANUAL_SCAN_RADIUS.get() : ASPServerConfig.TRIGGER_RADIUS.get());
        int maxExpansionRadius = Math.max(triggerRadius, ASPServerConfig.MAX_EXPANSION_RADIUS.get());
        int maxBlocks = ASPServerConfig.MAX_BLOCKS_PER_ASSEMBLY.get();
        int minBlocks = ASPServerConfig.MIN_BLOCKS_PER_ASSEMBLY.get();
        int maxOrigins = ASPServerConfig.MAX_ORIGINS_PER_SCAN.get();
        int tooLargeRejectRadius = ASPServerConfig.TOO_LARGE_REJECT_RADIUS.get();

        if (useAffectedPlan) {
            maxExpansionRadius = Math.max(maxExpansionRadius, ASPServerConfig.AFFECTED_BOUNDARY_VERTICAL_RANGE.get());
        }

        // 旧世界的 serverconfig 可能还保留 0.1.0 的高预算。自动触发走安全上限，避免用户升级后仍然卡死。
        if (!manualScan) {
            triggerRadius = Math.min(triggerRadius, ASPServerConfig.AUTO_MAX_TRIGGER_RADIUS.get());
            maxExpansionRadius = Math.max(triggerRadius, Math.min(maxExpansionRadius, Math.max(ASPServerConfig.AUTO_MAX_EXPANSION_RADIUS.get(), ASPServerConfig.AFFECTED_BOUNDARY_VERTICAL_RANGE.get())));
            maxBlocks = Math.min(maxBlocks, ASPServerConfig.AUTO_MAX_BLOCKS_PER_ASSEMBLY.get());
            maxOrigins = Math.min(maxOrigins, ASPServerConfig.AUTO_MAX_ORIGINS_PER_SCAN.get());
        }

        if (useAffectedPlan) {
            minBlocks = Math.min(minBlocks, ASPServerConfig.AUTO_AFFECTED_MIN_BLOCKS_PER_ASSEMBLY.get());
            // 受影响计划的语义是“本次影响区 -> 相连受影响区 -> 上下延伸区”递进扫描，
            // 不能再被 0.1.1 的 autoMaxOriginsPerScan=12 截断到只扫前几个候选。
            // 候选准备本身已经受 maxPreparedCandidatesPerScan 限制，Sable BFS 也受 maxBlocksPerAssembly 与 allowedPositions 限制。
            maxOrigins = Math.max(maxOrigins, preparedPlan.candidateOrigins().size());
        }

        final int expansionRadiusLimit = maxExpansionRadius;
        final Set<Long> allowedAutoPositions = useAffectedPlan ? preparedPlan.allowedAssemblyPositions() : null;
        final boolean requireFaceContact = !manualScan && ASPServerConfig.REQUIRE_FACE_CONTACT_FOR_AUTO_ASSEMBLY.get();
        boolean relaxDirectBreakNeighbors = reason == ScanReason.BLOCK_BREAK
                && ASPServerConfig.RELAX_START_SUPPORT_FOR_DIRECT_BREAK_NEIGHBORS.get();

        List<CandidateOrigin> candidates = useAffectedPlan
                ? List.of()
                : collectCandidatePositions(level, center, triggerRadius, relaxDirectBreakNeighbors);

        Set<BlockPos> alreadyConsumed = new HashSet<>();
        List<BlockPos> rejectedLargeAreas = new ArrayList<>();

        int candidateOrigins = 0;
        int assembledObjects = 0;
        int assembledBlocks = 0;
        int skippedTooManyBlocks = 0;
        int skippedBoundary = 0;
        int skippedSupported = 0;
        int skippedTooSmall = 0;

        if (useAffectedPlan) {
            AssemblyStats stats = scanAffectedComponents(level, center, reason, preparedPlan, maxBlocks, minBlocks);
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            if (ASPServerConfig.LOG_DEBUG.get() && (stats.candidateOrigins() > 0 || stats.assembledObjects() > 0)) {
                AutoSablePhysics.LOGGER.info(
                        "Auto Sable component scan {} at {} in {} took {} ms -> {}, currentImpact={}, affectedRegion={}, boundaryColumns={}, orderedPositions={}",
                        reason,
                        center,
                        level.dimension().location(),
                        elapsedMillis,
                        stats,
                        preparedPlan.currentImpactBlocks(),
                        preparedPlan.connectedAffectedBlocks(),
                        preparedPlan.boundaryColumns(),
                        preparedPlan.orderedAssemblyPositions().size()
                );
            } else if (ASPServerConfig.LOG_SLOW_SCANS.get() && elapsedMillis >= ASPServerConfig.SLOW_SCAN_LOG_MILLIS.get()) {
                AutoSablePhysics.LOGGER.warn(
                        "Slow Auto Sable component scan {} at {} in {} took {} ms -> {}. Consider lowering maxBlocksPerAssembly/maxPreparedCandidatesPerScan/maxAffectedRegionBlocksPerScan.",
                        reason,
                        center,
                        level.dimension().location(),
                        elapsedMillis,
                        stats
                );
            }
            return stats;
        }

        if (!manualScan && ASPServerConfig.ENABLE_SMALL_OBJECT_FALLBACK.get()) {
            AssemblyStats smallStats = runSmallObjectFallback(level, center, reason, preparedPlan, alreadyConsumed);
            candidateOrigins += smallStats.candidateOrigins();
            assembledObjects += smallStats.assembledObjects();
            assembledBlocks += smallStats.assembledBlocks();
            skippedTooManyBlocks += smallStats.skippedTooManyBlocks();
            skippedBoundary += smallStats.skippedBoundary();
            skippedSupported += smallStats.skippedSupported();
            skippedTooSmall += smallStats.skippedTooSmall();
        }

        if (smallObjectOnly) {
            candidates = List.of();
        }

        int regularCandidateOrigins = 0;
        for (CandidateOrigin candidate : candidates) {
            BlockPos origin = candidate.pos();
            if (regularCandidateOrigins >= maxOrigins) {
                break;
            }
            if (alreadyConsumed.contains(origin) || isRejectedByLargeArea(origin, rejectedLargeAreas, tooLargeRejectRadius) || !level.isLoaded(origin)) {
                continue;
            }

            if (allowedAutoPositions != null && !allowedAutoPositions.contains(origin.asLong())) {
                continue;
            }

            BlockState state = level.getBlockState(origin);
            if (!AssemblyFilters.canStartFrom(level, origin, state, candidate.ignoreBottomSupportPrecheck())) {
                continue;
            }

            regularCandidateOrigins++;
            candidateOrigins++;

            SubLevelAssemblyHelper.GatherResult result = SubLevelAssemblyHelper.gatherConnectedBlocks(
                    origin,
                    level,
                    maxBlocks,
                    (originPos, originState, candidatePos, candidateState, directionFrom) ->
                            AssemblyFilters.canConnect(
                                    level,
                                    originPos,
                                    originState,
                                    candidatePos,
                                    candidateState,
                                    directionFrom,
                                    origin,
                                    expansionRadiusLimit,
                                    allowedAutoPositions,
                                    requireFaceContact
                            )
            );

            if (result.assemblyState() == SubLevelAssemblyHelper.GatherResult.State.TOO_MANY_BLOCKS) {
                skippedTooManyBlocks++;
                rememberRejectedArea(rejectedLargeAreas, origin, tooLargeRejectRadius);
                continue;
            }
            if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS
                    || result.blocks() == null
                    || result.boundingBox() == null
                    || result.blocks().isEmpty()) {
                continue;
            }

            Set<BlockPos> blocks = result.blocks();
            alreadyConsumed.addAll(blocks);

            if (blocks.size() < minBlocks) {
                skippedTooSmall++;
                continue;
            }

            if (!useAffectedPlan
                    && ASPServerConfig.SKIP_IF_TOUCHES_SEARCH_BOUNDARY.get()
                    && AssemblyFilters.touchesChebyshevBoundary(blocks, origin, maxExpansionRadius)) {
                skippedBoundary++;
                rememberRejectedArea(rejectedLargeAreas, origin, tooLargeRejectRadius);
                continue;
            }

            if (ASPServerConfig.REQUIRE_NO_EXTERNAL_BOTTOM_SUPPORT.get()
                    && AssemblyFilters.hasExternalBottomSupport(level, blocks)) {
                skippedSupported++;
                continue;
            }

            if (DelayedAssemblyManager.submitOrAssembleNow(level, origin, blocks, result.boundingBox(), reason, !manualScan)) {
                assembledObjects++;
                assembledBlocks += blocks.size();
            }
        }

        AssemblyStats stats = new AssemblyStats(
                candidateOrigins,
                assembledObjects,
                assembledBlocks,
                skippedTooManyBlocks,
                skippedBoundary,
                skippedSupported,
                skippedTooSmall
        );

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        if (ASPServerConfig.LOG_DEBUG.get() && (stats.candidateOrigins() > 0 || stats.assembledObjects() > 0)) {
            if (useAffectedPlan) {
                AutoSablePhysics.LOGGER.info(
                        "Auto Sable affected scan {} at {} in {} took {} ms -> {}, currentImpact={}, affectedRegion={}, boundaryColumns={}, preparedCandidates={}",
                        reason,
                        center,
                        level.dimension().location(),
                        elapsedMillis,
                        stats,
                        preparedPlan.currentImpactBlocks(),
                        preparedPlan.connectedAffectedBlocks(),
                        preparedPlan.boundaryColumns(),
                        preparedPlan.candidateOrigins().size()
                );
            } else {
                AutoSablePhysics.LOGGER.info(
                        "Auto Sable scan {} at {} in {} took {} ms -> {}",
                        reason,
                        center,
                        level.dimension().location(),
                        elapsedMillis,
                        stats
                );
            }
        } else if (ASPServerConfig.LOG_SLOW_SCANS.get() && elapsedMillis >= ASPServerConfig.SLOW_SCAN_LOG_MILLIS.get()) {
            AutoSablePhysics.LOGGER.warn(
                    "Slow Auto Sable scan {} at {} in {} took {} ms -> {}. Consider lowering maxBlocksPerAssembly/maxExpansionRadius/maxOriginsPerScan.",
                    reason,
                    center,
                    level.dimension().location(),
                    elapsedMillis,
                    stats
            );
        }

        return stats;
    }


    /**
     * 受影响计划的组件级自动扫描。
     *
     * <p>0.1.8 起，自动扫描不再把允许区域中的坐标逐个当作 Sable gather 起点。
     * 这里会先在允许区域内读取真实方块，并按 6 面接触拆成互不污染的可移动组件。
     * 每个组件只判断一次：过大、过小、有外部底部支撑都会只影响该组件，不会再通过 alreadyConsumed
     * 把同一区域里的其它组件吞掉。</p>
     */
    private static AssemblyStats scanAffectedComponents(
            ServerLevel level,
            BlockPos center,
            ScanReason reason,
            AffectedScanPlanner.PreparedScanPlan preparedPlan,
            int maxBlocks,
            int minBlocks
    ) {
        Set<Long> allowedPositions = preparedPlan.allowedAssemblyPositions();
        List<BlockPos> orderedPositions = preparedPlan.orderedAssemblyPositions().isEmpty()
                ? preparedPlan.candidateOrigins()
                : preparedPlan.orderedAssemblyPositions();
        if (allowedPositions.isEmpty() || orderedPositions.isEmpty()) {
            return AssemblyStats.EMPTY;
        }

        Set<Long> visited = new HashSet<>();
        int componentsChecked = 0;
        int assembledObjects = 0;
        int assembledBlocks = 0;
        int skippedTooManyBlocks = 0;
        int skippedSupported = 0;
        int skippedTooSmall = 0;

        for (BlockPos start : orderedPositions) {
            long packedStart = start.asLong();
            if (visited.contains(packedStart) || !allowedPositions.contains(packedStart)) {
                continue;
            }

            ComponentResult component = collectFaceConnectedMovableComponent(level, start, allowedPositions, visited);
            if (component.blocks().isEmpty()) {
                continue;
            }

            componentsChecked++;
            Set<BlockPos> blocks = component.blocks();
            if (blocks.size() > maxBlocks) {
                skippedTooManyBlocks++;
                continue;
            }

            if (blocks.size() < minBlocks) {
                skippedTooSmall++;
                continue;
            }

            if (ASPServerConfig.REQUIRE_NO_EXTERNAL_BOTTOM_SUPPORT.get()
                    && AssemblyFilters.hasExternalBottomSupport(level, blocks)) {
                skippedSupported++;
                continue;
            }

            if (blocks.size() == 1) {
                BlockPos single = blocks.iterator().next();
                BlockState singleState = level.getBlockState(single);
                if (NaturalFallingService.tryConvertSingleBlockComponentToFalling(level, single, singleState)) {
                    assembledObjects++;
                    assembledBlocks++;
                    continue;
                }
            }

            BlockPos anchor = chooseAnchor(blocks, center);
            BoundingBox3i bounds = computeBoundingBox(blocks);
            if (DelayedAssemblyManager.submitOrAssembleNow(level, anchor, blocks, bounds, reason, true)) {
                assembledObjects++;
                assembledBlocks += blocks.size();
            }
        }

        return new AssemblyStats(
                componentsChecked,
                assembledObjects,
                assembledBlocks,
                skippedTooManyBlocks,
                0,
                skippedSupported,
                skippedTooSmall
        );
    }

    private static ComponentResult collectFaceConnectedMovableComponent(
            ServerLevel level,
            BlockPos start,
            Set<Long> allowedPositions,
            Set<Long> visited
    ) {
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        if (!markIfMovable(level, start, allowedPositions, visited, queue)) {
            return new ComponentResult(blocks);
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            blocks.add(current);
            BlockState currentState = level.getBlockState(current);

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                markIfConnectable(level, start, current, currentState, next, direction, allowedPositions, visited, queue);
            }
        }

        return new ComponentResult(blocks);
    }

    private static boolean markIfMovable(
            ServerLevel level,
            BlockPos pos,
            Set<Long> allowedPositions,
            Set<Long> visited,
            ArrayDeque<BlockPos> queue
    ) {
        BlockPos immutable = pos.immutable();
        long packed = immutable.asLong();
        if (!allowedPositions.contains(packed) || !visited.add(packed)) {
            return false;
        }
        if (!level.isLoaded(immutable)) {
            return false;
        }

        BlockState state = level.getBlockState(immutable);
        if (!AssemblyFilters.canMove(level, immutable, state)) {
            return false;
        }

        queue.addLast(immutable);
        return true;
    }

    private static boolean markIfConnectable(
            ServerLevel level,
            BlockPos componentOrigin,
            BlockPos current,
            BlockState currentState,
            BlockPos candidate,
            Direction direction,
            Set<Long> allowedPositions,
            Set<Long> visited,
            ArrayDeque<BlockPos> queue
    ) {
        long packed = candidate.asLong();
        if (!allowedPositions.contains(packed) || visited.contains(packed)) {
            return false;
        }
        if (!level.isLoaded(candidate)) {
            visited.add(packed);
            return false;
        }

        BlockState candidateState = level.getBlockState(candidate);
        if (!AssemblyFilters.canMove(level, candidate, candidateState)) {
            visited.add(packed);
            return false;
        }

        // 中文：连接被规则阻断时，不把 candidate 标记为 visited。
        // EN: When a rule blocks connectivity, do not mark the candidate as visited.
        // 这样它稍后仍可作为另一个独立组件的起点被扫描，避免“泥土/树叶横向断连后整个分支被吞掉”。
        if (!AssemblyFilters.canComponentConnect(level, current, currentState, candidate, candidateState, direction, componentOrigin)) {
            return false;
        }

        visited.add(packed);
        queue.addLast(candidate);
        return true;
    }

    private static BlockPos chooseAnchor(Set<BlockPos> blocks, BlockPos center) {
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockPos block : blocks) {
            long distance = distanceSquared(block, center);
            if (best == null || distance < bestDistance) {
                best = block;
                bestDistance = distance;
            }
        }
        return best == null ? center : best;
    }

    private static BoundingBox3i computeBoundingBox(Set<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos block : blocks) {
            minX = Math.min(minX, block.getX());
            minY = Math.min(minY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX());
            maxY = Math.max(maxY, block.getY());
            maxZ = Math.max(maxZ, block.getZ());
        }

        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record ComponentResult(Set<BlockPos> blocks) {
    }

    /**
     * 小型孤立物体补偿扫描。
     *
     * <p>0.1.5 后，大结构主要依赖受影响区域计划；但 1-2 个方块的小物体很容易因为没有连接点、
     * 没有足够邻位变化，或者被普通候选预算挤出而长期悬空。这里用一个独立的极小预算扫描处理它们：</p>
     *
     * <ul>
     *     <li>只检查本次变化附近的少量起点；</li>
     *     <li>强制面接触连接，避免边接触把地形误连进来；</li>
     *     <li>最大连通块数默认 2，超过立即放弃，因此扫到地形时也只会多检查极少数方块；</li>
     *     <li>最终仍执行外部底部支撑检查。</li>
     * </ul>
     */
    private static AssemblyStats runSmallObjectFallback(
            ServerLevel level,
            BlockPos center,
            ScanReason reason,
            @Nullable AffectedScanPlanner.PreparedScanPlan preparedPlan,
            Set<BlockPos> alreadyConsumed
    ) {
        int maxSmallBlocks = ASPServerConfig.SMALL_OBJECT_MAX_BLOCKS.get();
        int maxOrigins = ASPServerConfig.MAX_SMALL_OBJECT_ORIGINS_PER_SCAN.get();
        int scanRadius = ASPServerConfig.SMALL_OBJECT_SCAN_RADIUS.get();
        if (maxSmallBlocks <= 0 || maxOrigins <= 0 || scanRadius < 0) {
            return AssemblyStats.EMPTY;
        }

        List<BlockPos> origins = collectSmallObjectCandidatePositions(level, center, reason, preparedPlan, scanRadius);
        int candidateOrigins = 0;
        int assembledObjects = 0;
        int assembledBlocks = 0;
        int skippedTooManyBlocks = 0;
        int skippedSupported = 0;
        int skippedTooSmall = 0;

        for (BlockPos origin : origins) {
            if (candidateOrigins >= maxOrigins) {
                break;
            }
            if (alreadyConsumed.contains(origin) || !level.isLoaded(origin)) {
                continue;
            }

            BlockState state = level.getBlockState(origin);
            // 小物体补偿阶段允许跳过起点底部支撑预过滤；最终仍检查整个结构的外部底部支撑。
            if (!AssemblyFilters.canStartFrom(level, origin, state, true)) {
                continue;
            }

            candidateOrigins++;
            final int radiusLimit = Math.max(1, scanRadius);
            SubLevelAssemblyHelper.GatherResult result = SubLevelAssemblyHelper.gatherConnectedBlocks(
                    origin,
                    level,
                    maxSmallBlocks,
                    (originPos, originState, candidatePos, candidateState, directionFrom) ->
                            AssemblyFilters.canConnect(
                                    level,
                                    originPos,
                                    originState,
                                    candidatePos,
                                    candidateState,
                                    directionFrom,
                                    origin,
                                    radiusLimit,
                                    null,
                                    true
                            )
            );

            if (result.assemblyState() == SubLevelAssemblyHelper.GatherResult.State.TOO_MANY_BLOCKS) {
                skippedTooManyBlocks++;
                continue;
            }
            if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS
                    || result.blocks() == null
                    || result.boundingBox() == null
                    || result.blocks().isEmpty()) {
                continue;
            }

            Set<BlockPos> blocks = result.blocks();
            boolean overlapsConsumed = false;
            for (BlockPos block : blocks) {
                if (alreadyConsumed.contains(block)) {
                    overlapsConsumed = true;
                    break;
                }
            }
            if (overlapsConsumed) {
                continue;
            }

            if (blocks.size() < Math.min(ASPServerConfig.MIN_BLOCKS_PER_ASSEMBLY.get(), ASPServerConfig.AUTO_AFFECTED_MIN_BLOCKS_PER_ASSEMBLY.get())) {
                skippedTooSmall++;
                continue;
            }

            if (ASPServerConfig.REQUIRE_NO_EXTERNAL_BOTTOM_SUPPORT.get()
                    && AssemblyFilters.hasExternalBottomSupport(level, blocks)) {
                skippedSupported++;
                continue;
            }

            if (DelayedAssemblyManager.submitOrAssembleNow(level, origin, blocks, result.boundingBox(), reason, true)) {
                alreadyConsumed.addAll(blocks);
                assembledObjects++;
                assembledBlocks += blocks.size();
            }
        }

        return new AssemblyStats(candidateOrigins, assembledObjects, assembledBlocks, skippedTooManyBlocks, 0, skippedSupported, skippedTooSmall);
    }

    private static List<BlockPos> collectSmallObjectCandidatePositions(
            ServerLevel level,
            BlockPos center,
            ScanReason reason,
            @Nullable AffectedScanPlanner.PreparedScanPlan preparedPlan,
            int scanRadius
    ) {
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();

        if (reason == ScanReason.BLOCK_PLACE || reason == ScanReason.SMALL_OBJECT_CHECK) {
            addSmallCandidate(level, unique, center);
        }

        for (Direction direction : directNeighborPriority()) {
            addSmallCandidate(level, unique, center.relative(direction));
        }

        int radius = Math.max(scanRadius, reason == ScanReason.BLOCK_PLACE || reason == ScanReason.SMALL_OBJECT_CHECK
                ? ASPServerConfig.AFFECTED_RADIUS_ON_PLACE.get()
                : ASPServerConfig.AFFECTED_RADIUS_ON_BREAK.get());
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            addSmallCandidate(level, unique, pos.immutable());
        }

        if (preparedPlan != null) {
            for (BlockPos pos : preparedPlan.candidateOrigins()) {
                addSmallCandidate(level, unique, pos);
            }
        }

        List<BlockPos> result = new ArrayList<>(unique);
        result.sort((a, b) -> Long.compare(distanceSquared(a, center), distanceSquared(b, center)));
        return result;
    }

    private static void addSmallCandidate(ServerLevel level, LinkedHashSet<BlockPos> result, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (level.isLoaded(immutable)) {
            result.add(immutable);
        }
    }

    private static List<CandidateOrigin> collectCandidatePositionsFromPlan(
            ServerLevel level,
            BlockPos center,
            AffectedScanPlanner.PreparedScanPlan preparedPlan,
            boolean relaxDirectBreakNeighbors
    ) {
        Map<BlockPos, CandidateOrigin> unique = new LinkedHashMap<>();
        int relaxedBudget = relaxDirectBreakNeighbors ? ASPServerConfig.MAX_RELAXED_BREAK_NEIGHBOR_ORIGINS.get() : 0;
        Set<Long> directNeighbors = new LinkedHashSet<>();
        for (Direction direction : directNeighborPriority()) {
            BlockPos directNeighbor = center.relative(direction).immutable();
            directNeighbors.add(directNeighbor.asLong());

            // 受影响计划也保留“被破坏方块直接邻位优先”的旧逻辑。
            // 这可以保证破坏柱子/树木最底部时，正上方的结构方块先于周围地面候选进入 Sable BFS。
            if (preparedPlan.allowedAssemblyPositions().contains(directNeighbor.asLong())
                    && level.isLoaded(directNeighbor)
                    && !unique.containsKey(directNeighbor)) {
                boolean relaxed = relaxedBudget > 0;
                unique.put(directNeighbor, new CandidateOrigin(directNeighbor, relaxed));
                if (relaxed) {
                    relaxedBudget--;
                }
            }
        }

        for (BlockPos pos : preparedPlan.candidateOrigins()) {
            BlockPos immutable = pos.immutable();
            if (unique.containsKey(immutable) || !level.isLoaded(immutable)) {
                continue;
            }

            boolean relaxed = false;
            if (relaxedBudget > 0 && directNeighbors.contains(immutable.asLong())) {
                relaxed = true;
                relaxedBudget--;
            }
            unique.put(immutable, new CandidateOrigin(immutable, relaxed));
        }

        return new ArrayList<>(unique.values());
    }

    private static List<CandidateOrigin> collectCandidatePositions(ServerLevel level, BlockPos center, int radius, boolean relaxDirectBreakNeighbors) {
        Map<BlockPos, CandidateOrigin> unique = new LinkedHashMap<>();
        int relaxedBudget = relaxDirectBreakNeighbors ? ASPServerConfig.MAX_RELAXED_BREAK_NEIGHBOR_ORIGINS.get() : 0;

        // 最高优先级：被改变方块的直接邻位。
        for (Direction direction : directNeighborPriority()) {
            boolean relaxed = relaxedBudget > 0;
            if (tryAddCandidate(level, unique, center.relative(direction), relaxed)) {
                if (relaxed) {
                    relaxedBudget--;
                }
            }
        }

        if (radius > 1) {
            BlockPos min = center.offset(-radius, -radius, -radius);
            BlockPos max = center.offset(radius, radius, radius);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                tryAddCandidate(level, unique, pos.immutable(), false);
            }
        }

        List<CandidateOrigin> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> {
            int relaxedCompare = Boolean.compare(b.ignoreBottomSupportPrecheck(), a.ignoreBottomSupportPrecheck());
            if (relaxedCompare != 0) {
                return relaxedCompare;
            }
            return Long.compare(distanceSquared(a.pos(), center), distanceSquared(b.pos(), center));
        });
        return result;
    }

    private static Direction[] directNeighborPriority() {
        return new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    }

    private static boolean tryAddCandidate(ServerLevel level, Map<BlockPos, CandidateOrigin> result, BlockPos pos, boolean ignoreBottomSupportPrecheck) {
        BlockPos immutable = pos.immutable();
        if (!level.isLoaded(immutable) || result.containsKey(immutable)) {
            return false;
        }
        BlockState state = level.getBlockState(immutable);
        if (!AssemblyFilters.canStartFrom(level, immutable, state, ignoreBottomSupportPrecheck)) {
            return false;
        }
        result.put(immutable, new CandidateOrigin(immutable, ignoreBottomSupportPrecheck));
        return true;
    }

    private static boolean isRejectedByLargeArea(BlockPos pos, List<BlockPos> rejectedLargeAreas, int radius) {
        if (radius <= 0 || rejectedLargeAreas.isEmpty()) {
            return false;
        }
        for (BlockPos rejected : rejectedLargeAreas) {
            if (AssemblyFilters.isWithinChebyshevRadius(pos, rejected, radius)) {
                return true;
            }
        }
        return false;
    }

    private static void rememberRejectedArea(List<BlockPos> rejectedLargeAreas, BlockPos origin, int radius) {
        if (radius <= 0) {
            return;
        }
        rejectedLargeAreas.add(origin.immutable());
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record CandidateOrigin(BlockPos pos, boolean ignoreBottomSupportPrecheck) {
    }
}
