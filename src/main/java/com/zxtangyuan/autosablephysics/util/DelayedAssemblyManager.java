package com.zxtangyuan.autosablephysics.util;

import com.zxtangyuan.autosablephysics.AutoSablePhysics;
import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 自动物理化延迟创建调度器与 Auto Sable Physics 创建物体的生命周期跟踪器。
 */
public final class DelayedAssemblyManager {
    private static final Map<ResourceKey<Level>, ArrayDeque<PendingAssembly>> PENDING = new HashMap<>();
    private static final Map<ResourceKey<Level>, LastAssemblyGate> LAST_ASSEMBLY = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> LAST_WARNING_TICK = new HashMap<>();
    private static final Map<UUID, TrackedSubLevel> TRACKED_SUB_LEVELS = new HashMap<>();
    private static final Set<UUID> PINNED_SUB_LEVELS = new HashSet<>();
    /**
     * 静止物理体占用区域。玩家在这些位置放置火把、拉杆、按钮、花草等无碰撞不可支撑方块时，
     * 会被立即破坏，模拟“物理体仍占据该网格空间”。使用引用计数处理多个物理体区域重叠。
     */
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> BLOCKED_NO_COLLISION_PLACEMENT = new HashMap<>();

    private DelayedAssemblyManager() {
    }

    public static boolean submitOrAssembleNow(
            ServerLevel level,
            BlockPos anchor,
            Set<BlockPos> blocks,
            BoundingBox3i bounds,
            ScanReason reason,
            boolean allowDelayed
    ) {
        if (blocks.isEmpty()) {
            return false;
        }

        if (!allowDelayed || !ASPServerConfig.ENABLE_DELAYED_ASSEMBLY.get()) {
            return assembleNow(level, anchor, blocks, bounds, reason, false);
        }

        LinkedHashSet<BlockPos> immutableBlocks = new LinkedHashSet<>();
        for (BlockPos block : blocks) {
            immutableBlocks.add(block.immutable());
        }
        PendingAssembly pending = new PendingAssembly(
                level.dimension(),
                System.identityHashCode(level),
                anchor.immutable(),
                immutableBlocks,
                copyBounds(bounds),
                level.getServer().getTickCount(),
                reason
        );
        PENDING.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>()).addLast(pending);
        return true;
    }

    public static void tick(ServerLevel level) {
        tickTrackedSubLevels(level);
        tickPendingAssemblies(level);
    }

    public static void resetAll() {
        PENDING.clear();
        LAST_ASSEMBLY.clear();
        LAST_WARNING_TICK.clear();
        TRACKED_SUB_LEVELS.clear();
        PINNED_SUB_LEVELS.clear();
        BLOCKED_NO_COLLISION_PLACEMENT.clear();
    }

    public static void setSubLevelPinned(UUID uuid, boolean pinned) {
        if (uuid == null) {
            return;
        }
        if (pinned) {
            PINNED_SUB_LEVELS.add(uuid);
        } else {
            PINNED_SUB_LEVELS.remove(uuid);
        }
        TrackedSubLevel tracked = TRACKED_SUB_LEVELS.get(uuid);
        if (tracked != null) {
            tracked.pinned = pinned;
        }
    }

    public static boolean isSubLevelPinned(UUID uuid) {
        return uuid != null && PINNED_SUB_LEVELS.contains(uuid);
    }

    public static void forgetSubLevel(UUID uuid) {
        if (uuid == null) {
            return;
        }
        TrackedSubLevel tracked = TRACKED_SUB_LEVELS.remove(uuid);
        if (tracked != null) {
            clearBlockedNoCollisionZone(tracked);
        }
        PINNED_SUB_LEVELS.remove(uuid);
        for (Iterator<Map.Entry<ResourceKey<Level>, LastAssemblyGate>> iterator = LAST_ASSEMBLY.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<ResourceKey<Level>, LastAssemblyGate> entry = iterator.next();
            LastAssemblyGate gate = entry.getValue();
            if (gate.subLevel() != null && uuid.equals(gate.subLevel().getUniqueId())) {
                iterator.remove();
            }
        }
    }

    /**
     * 玩家尝试在静止物理体占用区放置无碰撞不可支撑方块时调用。
     *
     * @return true 表示该方块已被立即破坏，调用方应停止后续自动扫描。
     */
    public static boolean destroyForbiddenNoCollisionPlacement(ServerLevel level, BlockPos pos, BlockState state) {
        if (!ASPServerConfig.CRUSH_NO_COLLISION_SUPPORTS.get()
                || !ASPServerConfig.ENABLE_SUB_LEVEL_CRUSH_NO_COLLISION_BLOCKS.get()) {
            return false;
        }
        Map<Long, Integer> blocked = BLOCKED_NO_COLLISION_PLACEMENT.get(level.dimension());
        if (blocked == null || !blocked.containsKey(pos.asLong())) {
            return false;
        }
        if (!AssemblyFilters.isCrushableNonSupportingBlock(level, pos, state)) {
            return false;
        }
        level.destroyBlock(pos, true);
        return true;
    }

    private static void tickPendingAssemblies(ServerLevel level) {
        ArrayDeque<PendingAssembly> queue = PENDING.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            return;
        }

        int currentTick = level.getServer().getTickCount();
        maybeWarnPlayers(level, queue, currentTick);

        int budget = ASPServerConfig.MAX_ASSEMBLIES_PER_LEVEL_TICK.get();
        while (budget-- > 0 && !queue.isEmpty()) {
            PendingAssembly pending = queue.peekFirst();
            if (pending.levelIdentity() != System.identityHashCode(level)) {
                // 同一 JVM 内退出世界再进新世界时，维度 key 可能相同。旧世界遗留队列必须丢弃，避免永久阻塞。
                queue.removeFirst();
                continue;
            }

            PendingValidation validation = validatePending(level, pending);
            if (validation == PendingValidation.CANCEL) {
                queue.removeFirst();
                continue;
            }
            if (validation == PendingValidation.WAIT) {
                break;
            }

            if (!isGateOpen(level, currentTick)) {
                break;
            }

            queue.removeFirst();
            boolean assembled = assembleNow(level, pending.anchor(), pending.blocks(), pending.bounds(), pending.reason(), true);
            if (assembled) {
                break;
            }
        }

        if (queue.isEmpty()) {
            PENDING.remove(level.dimension());
        }
    }

    private static boolean assembleNow(
            ServerLevel level,
            BlockPos anchor,
            Set<BlockPos> blocks,
            BoundingBox3i bounds,
            ScanReason reason,
            boolean updateGate
    ) {
        try {
            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
            if (updateGate) {
                int tick = level.getServer().getTickCount();
                LAST_ASSEMBLY.put(level.dimension(), new LastAssemblyGate(subLevel, tick, System.identityHashCode(level)));
                if (subLevel != null && subLevel.getUniqueId() != null) {
                    TRACKED_SUB_LEVELS.put(subLevel.getUniqueId(), new TrackedSubLevel(level.dimension(), System.identityHashCode(level), subLevel, tick, blocks.size(), reason));
                }
            }
            return true;
        } catch (Throwable throwable) {
            AutoSablePhysics.LOGGER.error("Failed to assemble delayed Sable sub-level near {} in {}", anchor, level.dimension().location(), throwable);
            return false;
        }
    }

    private static PendingValidation validatePending(ServerLevel level, PendingAssembly pending) {
        boolean anyLoaded = false;
        for (BlockPos block : pending.blocks()) {
            if (!level.isLoaded(block)) {
                return PendingValidation.WAIT;
            }
            anyLoaded = true;
            BlockState state = level.getBlockState(block);
            if (!AssemblyFilters.canMove(level, block, state)) {
                return PendingValidation.CANCEL;
            }
        }

        if (!anyLoaded) {
            return PendingValidation.CANCEL;
        }

        if (ASPServerConfig.CANCEL_PENDING_IF_SUPPORTED.get()
                && AssemblyFilters.hasExternalBottomSupport(level, pending.blocks())) {
            return PendingValidation.CANCEL;
        }

        if (ASPServerConfig.CANCEL_PENDING_IF_CONNECTED.get()
                && hasExternalMovableFaceConnection(level, pending.blocks())) {
            return PendingValidation.CANCEL;
        }

        return PendingValidation.READY;
    }

    private static boolean hasExternalMovableFaceConnection(ServerLevel level, Set<BlockPos> blocks) {
        Set<Long> packedBlocks = new HashSet<>();
        for (BlockPos block : blocks) {
            packedBlocks.add(block.asLong());
        }

        for (BlockPos block : blocks) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = block.relative(direction).immutable();
                if (packedBlocks.contains(neighbor.asLong())) {
                    continue;
                }
                if (!level.isLoaded(neighbor)) {
                    continue;
                }
                BlockState blockState = level.getBlockState(block);
                BlockState neighborState = level.getBlockState(neighbor);
                if (AssemblyFilters.canComponentConnect(level, block, blockState, neighbor, neighborState, direction)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isGateOpen(ServerLevel level, int currentTick) {
        LastAssemblyGate last = LAST_ASSEMBLY.get(level.dimension());
        if (last == null) {
            return true;
        }
        if (last.levelIdentity() != System.identityHashCode(level)) {
            LAST_ASSEMBLY.remove(level.dimension());
            return true;
        }

        int age = currentTick - last.createdAtTick();
        int minDelay = Math.max(ASPServerConfig.MIN_TICKS_BETWEEN_ASSEMBLIES.get(), ASPServerConfig.MIN_SABLE_SYNC_WAIT_TICKS.get());
        if (age < minDelay) {
            return false;
        }

        ServerSubLevel subLevel = last.subLevel();
        if (subLevel == null || subLevel.isRemoved() || subLevel.getLevel() != level) {
            LAST_ASSEMBLY.remove(level.dimension());
            return true;
        }

        if (!ASPServerConfig.REQUIRE_SABLE_TRACKING_CONFIRMATION.get()) {
            return true;
        }

        int playersInLevel = level.players().size();
        if (playersInLevel <= 0) {
            return false;
        }
        return subLevel.getTrackingPlayers().size() >= playersInLevel;
    }

    private static void crushOverlappedNonSupportingBlocks(ServerLevel level, ServerSubLevel subLevel, TrackedSubLevel tracked, int currentTick) {
        if (!ASPServerConfig.CRUSH_NO_COLLISION_SUPPORTS.get()
                || !ASPServerConfig.ENABLE_SUB_LEVEL_CRUSH_NO_COLLISION_BLOCKS.get()) {
            clearBlockedNoCollisionZone(tracked);
            tracked.crushZoneScanned = false;
            tracked.stationaryCrushTicks = 0;
            tracked.lastCrushPosition = new Vector3d(subLevel.logicalPose().position());
            tracked.lastCrushCheckTick = currentTick;
            return;
        }

        int interval = ASPServerConfig.SUB_LEVEL_CRUSH_CHECK_INTERVAL_TICKS.get();
        if (interval > 1 && currentTick - tracked.lastCrushCheckTick < interval) {
            return;
        }

        Vector3d currentPosition = new Vector3d(subLevel.logicalPose().position());
        double positionThresholdSq = ASPServerConfig.RESTORE_POSITION_EPSILON.get() * ASPServerConfig.RESTORE_POSITION_EPSILON.get();
        double linearThresholdSq = ASPServerConfig.RESTORE_LINEAR_SPEED_THRESHOLD.get() * ASPServerConfig.RESTORE_LINEAR_SPEED_THRESHOLD.get();
        double angularThresholdSq = ASPServerConfig.RESTORE_ANGULAR_SPEED_THRESHOLD.get() * ASPServerConfig.RESTORE_ANGULAR_SPEED_THRESHOLD.get();
        boolean slow = subLevel.latestLinearVelocity.lengthSquared() <= linearThresholdSq
                && subLevel.latestAngularVelocity.lengthSquared() <= angularThresholdSq;
        boolean moved = tracked.lastCrushPosition == null || currentPosition.distanceSquared(tracked.lastCrushPosition) > positionThresholdSq || !slow;
        int elapsed = tracked.lastCrushCheckTick <= 0 ? 0 : currentTick - tracked.lastCrushCheckTick;
        tracked.lastCrushCheckTick = currentTick;
        tracked.lastCrushPosition = currentPosition;

        if (moved) {
            // 物理体重新移动后，旧的禁止无碰撞方块放置区不再可信，必须清除，等待下次静止后重新登记。
            clearBlockedNoCollisionZone(tracked);
            tracked.crushZoneScanned = false;
            tracked.stationaryCrushTicks = 0;
            return;
        }

        tracked.stationaryCrushTicks += Math.max(0, elapsed);
        if (tracked.stationaryCrushTicks < ASPServerConfig.STATIONARY_SUB_LEVEL_CRUSH_IDLE_TICKS.get()) {
            return;
        }
        if (tracked.crushZoneScanned) {
            return;
        }
        if (!isSubLevelNearAnyPlayer(level, subLevel, ASPServerConfig.SUB_LEVEL_CRUSH_PLAYER_RADIUS.get())) {
            return;
        }

        // 中文：0.2.9 起，只在 sub-level 已经静止后执行一次 AABB 扫描。
        // EN: Since 0.2.9, run AABB crush scans only once after the sub-level is stationary.
        // 扫描结果会登记为禁止放置无碰撞方块区域；后续玩家在该区域放火把/拉杆等，会被立即破坏。
        // 这样避免 0.2.7 中运动状态频繁修改世界导致 Rapier native panic 的风险。
        double expansion = ASPServerConfig.SUB_LEVEL_CRUSH_AABB_EXPANSION.get();
        double minX = subLevel.boundingBox().minX() - expansion;
        double minY = subLevel.boundingBox().minY() - expansion;
        double minZ = subLevel.boundingBox().minZ() - expansion;
        double maxX = subLevel.boundingBox().maxX() + expansion;
        double maxY = subLevel.boundingBox().maxY() + expansion;
        double maxZ = subLevel.boundingBox().maxZ() + expansion;

        int x0 = floorForBlockRange(minX);
        int y0 = floorForBlockRange(minY);
        int z0 = floorForBlockRange(minZ);
        int x1 = floorForBlockRange(maxX - 1.0E-7D);
        int y1 = floorForBlockRange(maxY - 1.0E-7D);
        int z1 = floorForBlockRange(maxZ - 1.0E-7D);

        int checked = 0;
        int maxChecks = ASPServerConfig.SUB_LEVEL_CRUSH_MAX_BLOCKS_PER_CHECK.get();
        Set<Long> newZone = new HashSet<>();
        for (BlockPos mutable : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (++checked > maxChecks) {
                break;
            }
            BlockPos pos = mutable.immutable();
            if (!level.isLoaded(pos)) {
                continue;
            }
            newZone.add(pos.asLong());
            BlockState state = level.getBlockState(pos);
            if (AssemblyFilters.isCrushableNonSupportingBlock(level, pos, state)) {
                level.destroyBlock(pos, true);
            }
        }

        replaceBlockedNoCollisionZone(tracked, newZone);
        tracked.crushZoneScanned = true;
    }

    private static boolean isSubLevelNearAnyPlayer(ServerLevel level, ServerSubLevel subLevel, int radius) {
        if (radius <= 0) {
            return true;
        }
        double centerX = (subLevel.boundingBox().minX() + subLevel.boundingBox().maxX()) * 0.5D;
        double centerY = (subLevel.boundingBox().minY() + subLevel.boundingBox().maxY()) * 0.5D;
        double centerZ = (subLevel.boundingBox().minZ() + subLevel.boundingBox().maxZ()) * 0.5D;
        double radiusSq = (double) radius * (double) radius;
        for (var player : level.players()) {
            double dx = player.getX() - centerX;
            double dy = player.getY() - centerY;
            double dz = player.getZ() - centerZ;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private static void replaceBlockedNoCollisionZone(TrackedSubLevel tracked, Set<Long> newZone) {
        clearBlockedNoCollisionZone(tracked);
        if (newZone.isEmpty()) {
            return;
        }
        Map<Long, Integer> dimensionMap = BLOCKED_NO_COLLISION_PLACEMENT.computeIfAbsent(tracked.dimension(), ignored -> new HashMap<>());
        for (long packed : newZone) {
            dimensionMap.merge(packed, 1, Integer::sum);
        }
        tracked.blockedNoCollisionZone = new HashSet<>(newZone);
    }

    private static void clearBlockedNoCollisionZone(TrackedSubLevel tracked) {
        if (tracked == null || tracked.blockedNoCollisionZone == null || tracked.blockedNoCollisionZone.isEmpty()) {
            return;
        }
        Map<Long, Integer> dimensionMap = BLOCKED_NO_COLLISION_PLACEMENT.get(tracked.dimension());
        if (dimensionMap != null) {
            for (long packed : tracked.blockedNoCollisionZone) {
                Integer count = dimensionMap.get(packed);
                if (count == null || count <= 1) {
                    dimensionMap.remove(packed);
                } else {
                    dimensionMap.put(packed, count - 1);
                }
            }
            if (dimensionMap.isEmpty()) {
                BLOCKED_NO_COLLISION_PLACEMENT.remove(tracked.dimension());
            }
        }
        tracked.blockedNoCollisionZone.clear();
    }

    private static int floorForBlockRange(double value) {
        return (int) Math.floor(value);
    }

    private static boolean shouldFastGridRestore(ServerSubLevel subLevel, TrackedSubLevel tracked) {
        if (!ASPServerConfig.ENABLE_GRID_ALIGNED_FAST_RESTORE.get()) {
            return false;
        }
        if (tracked.idleTicks < ASPServerConfig.GRID_ALIGNED_RESTORE_IDLE_TICKS.get()) {
            return false;
        }
        return isSubLevelGridAligned(subLevel, ASPServerConfig.GRID_ALIGNMENT_EPSILON.get(), ASPServerConfig.GRID_ALIGNMENT_ANGULAR_EPSILON.get());
    }

    private static boolean isSubLevelGridAligned(ServerSubLevel subLevel, double positionEpsilon, double angularEpsilon) {
        if (!isScaleApproximatelyIdentity(subLevel)) {
            return false;
        }
        if (!isOrientationApproximatelyIdentity(subLevel, angularEpsilon)) {
            return false;
        }

        boolean integerEdges = isNearInteger(subLevel.boundingBox().minX(), positionEpsilon)
                && isNearInteger(subLevel.boundingBox().minY(), positionEpsilon)
                && isNearInteger(subLevel.boundingBox().minZ(), positionEpsilon)
                && isNearInteger(subLevel.boundingBox().maxX(), positionEpsilon)
                && isNearInteger(subLevel.boundingBox().maxY(), positionEpsilon)
                && isNearInteger(subLevel.boundingBox().maxZ(), positionEpsilon);
        if (integerEdges) {
            return true;
        }

        // Sable 某些情况下的全局 AABB 会以方块中心为基准，边界表现为 N + 0.5。
        // 0.2.2 的还原坐标修正正是为这种表示做了中心对齐。这里也接受半格边界对齐，
        // 否则视觉上已经落回网格的物理体可能永远达不到快速还原条件。
        return isNearHalfInteger(subLevel.boundingBox().minX(), positionEpsilon)
                && isNearHalfInteger(subLevel.boundingBox().minY(), positionEpsilon)
                && isNearHalfInteger(subLevel.boundingBox().minZ(), positionEpsilon)
                && isNearHalfInteger(subLevel.boundingBox().maxX(), positionEpsilon)
                && isNearHalfInteger(subLevel.boundingBox().maxY(), positionEpsilon)
                && isNearHalfInteger(subLevel.boundingBox().maxZ(), positionEpsilon);
    }

    private static boolean isScaleApproximatelyIdentity(ServerSubLevel subLevel) {
        return Math.abs(subLevel.logicalPose().scale().x() - 1.0D) <= 1.0E-4D
                && Math.abs(subLevel.logicalPose().scale().y() - 1.0D) <= 1.0E-4D
                && Math.abs(subLevel.logicalPose().scale().z() - 1.0D) <= 1.0E-4D;
    }

    private static boolean isOrientationApproximatelyIdentity(ServerSubLevel subLevel, double angularEpsilon) {
        Quaterniondc q = subLevel.logicalPose().orientation();
        // q 与 -q 表示同一旋转，因此使用 abs(w)。
        double clampedW = Math.max(-1.0D, Math.min(1.0D, Math.abs(q.w())));
        double angle = 2.0D * Math.acos(clampedW);
        return angle <= angularEpsilon;
    }

    private static boolean isNearInteger(double value, double epsilon) {
        return Math.abs(value - Math.rint(value)) <= epsilon;
    }

    private static boolean isNearHalfInteger(double value, double epsilon) {
        return Math.abs((value - 0.5D) - Math.rint(value - 0.5D)) <= epsilon;
    }

    private static void maybeWarnPlayers(ServerLevel level, ArrayDeque<PendingAssembly> queue, int currentTick) {
        int warningAge = ASPServerConfig.PENDING_ASSEMBLY_WARNING_TICKS.get();
        int warningSize = ASPServerConfig.PENDING_ASSEMBLY_WARNING_SIZE.get();
        boolean shouldWarn = false;

        PendingAssembly oldest = queue.peekFirst();
        int oldestAge = oldest == null ? 0 : currentTick - oldest.queuedAtTick();
        if (warningAge > 0 && oldestAge >= warningAge) {
            shouldWarn = true;
        }
        if (warningSize > 0 && queue.size() >= warningSize) {
            shouldWarn = true;
        }
        if (!shouldWarn) {
            return;
        }

        int lastWarnTick = LAST_WARNING_TICK.getOrDefault(level.dimension(), Integer.MIN_VALUE);
        int interval = ASPServerConfig.PENDING_ASSEMBLY_WARNING_INTERVAL_TICKS.get();
        if (currentTick - lastWarnTick < interval) {
            return;
        }
        LAST_WARNING_TICK.put(level.dimension(), currentTick);

        Component message = Component.literal("[Auto Sable Physics] 待物理化队列已有 "
                + queue.size()
                + " 个组件，最老组件等待 "
                + oldestAge
                + " tick。Sable 同步尚未放行下一个物理体，请减少一次性创建的小型物理体数量，或等待队列消化。");
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void tickTrackedSubLevels(ServerLevel level) {
        if (!ASPServerConfig.TRACK_CREATED_SUB_LEVEL_MOTION.get() || TRACKED_SUB_LEVELS.isEmpty()) {
            return;
        }

        int currentTick = level.getServer().getTickCount();
        double linearThresholdSq = ASPServerConfig.RESTORE_LINEAR_SPEED_THRESHOLD.get() * ASPServerConfig.RESTORE_LINEAR_SPEED_THRESHOLD.get();
        double angularThresholdSq = ASPServerConfig.RESTORE_ANGULAR_SPEED_THRESHOLD.get() * ASPServerConfig.RESTORE_ANGULAR_SPEED_THRESHOLD.get();
        double positionThresholdSq = ASPServerConfig.RESTORE_POSITION_EPSILON.get() * ASPServerConfig.RESTORE_POSITION_EPSILON.get();
        int checkInterval = ASPServerConfig.RESTORE_POSITION_CHECK_INTERVAL_TICKS.get();
        int idleRequired = ASPServerConfig.RESTORE_IDLE_TICKS_REQUIRED.get();

        // 0.2.1：这里不能直接遍历 TRACKED_SUB_LEVELS.entrySet() 后在 restoreSubLevel 中调用 forgetSubLevel。
        // restoreSubLevel 成功时会从 TRACKED_SUB_LEVELS / LAST_ASSEMBLY / PINNED_SUB_LEVELS 中移除该 sub-level，
        // 如果此时仍使用 HashMap 的 live iterator，下一次 iterator.remove/hasNext 就可能抛 ConcurrentModificationException。
        // 因此先复制 UUID 快照，再按 key 读取当前记录。期间被移除的记录会自然跳过。
        List<UUID> trackedIds = new ArrayList<>(TRACKED_SUB_LEVELS.keySet());
        for (UUID trackedId : trackedIds) {
            TrackedSubLevel tracked = TRACKED_SUB_LEVELS.get(trackedId);
            if (tracked == null) {
                continue;
            }
            if (!tracked.dimension().equals(level.dimension())) {
                continue;
            }
            if (tracked.levelIdentity() != System.identityHashCode(level)) {
                clearBlockedNoCollisionZone(tracked);
                TRACKED_SUB_LEVELS.remove(trackedId);
                PINNED_SUB_LEVELS.remove(trackedId);
                continue;
            }

            ServerSubLevel subLevel = tracked.subLevel();
            if (subLevel == null || subLevel.isRemoved() || subLevel.getLevel() != level) {
                clearBlockedNoCollisionZone(tracked);
                TRACKED_SUB_LEVELS.remove(trackedId);
                PINNED_SUB_LEVELS.remove(trackedId);
                continue;
            }

            // 0.2.9：无碰撞物体只会在 sub-level 已静止后被扫描破坏；同时登记禁止放置区。
            // 被锤子保存的长期物理体不会自动还原，但仍可在静止后占用空间并禁止玩家往里面塞火把/拉杆等无碰撞方块。
            crushOverlappedNonSupportingBlocks(level, subLevel, tracked, currentTick);

            UUID uuid = subLevel.getUniqueId();
            if (isSubLevelPinned(uuid) || tracked.pinned) {
                continue;
            }
            if (!ASPServerConfig.ENABLE_AUTO_RESTORE_SUB_LEVELS.get()) {
                continue;
            }
            if (tracked.blockCount() > ASPServerConfig.MAX_BLOCKS_FOR_AUTO_RESTORE.get()) {
                continue;
            }

            if (currentTick - tracked.lastPositionCheckTick < checkInterval) {
                continue;
            }

            Vector3d currentPosition = new Vector3d(subLevel.logicalPose().position());
            boolean positionChanged = tracked.lastPosition == null || currentPosition.distanceSquared(tracked.lastPosition) > positionThresholdSq;
            boolean slow = subLevel.latestLinearVelocity.lengthSquared() <= linearThresholdSq
                    && subLevel.latestAngularVelocity.lengthSquared() <= angularThresholdSq;

            int elapsed = tracked.lastPositionCheckTick <= 0 ? 0 : currentTick - tracked.lastPositionCheckTick;
            tracked.lastPositionCheckTick = currentTick;
            tracked.lastPosition = currentPosition;

            if (positionChanged || !slow) {
                tracked.idleTicks = 0;
                continue;
            }

            tracked.idleTicks += Math.max(0, elapsed);
            boolean shouldRestore = tracked.idleTicks >= idleRequired || shouldFastGridRestore(subLevel, tracked);
            if (shouldRestore) {
                SubLevelRestorationService.RestoreResult result = SubLevelRestorationService.restoreSubLevel(level, subLevel, false, true);
                if (result.success()) {
                    // restoreSubLevel 成功时已经调用 forgetSubLevel；这里再移除一次只是幂等兜底，不使用 iterator。
                    TRACKED_SUB_LEVELS.remove(trackedId);
                    PINNED_SUB_LEVELS.remove(trackedId);
                    if (ASPServerConfig.LOG_DEBUG.get()) {
                        AutoSablePhysics.LOGGER.info("Auto-restored Sable sub-level {} after {} idle ticks; restored {} blocks.", uuid, tracked.idleTicks, result.blocksRestored());
                    }
                } else {
                    tracked.idleTicks = 0;
                    if (ASPServerConfig.LOG_DEBUG.get()) {
                        AutoSablePhysics.LOGGER.info("Auto restore skipped for Sable sub-level {}: {}", uuid, result.message());
                    }
                }
            }
        }
    }

    private static BoundingBox3i copyBounds(BoundingBox3i bounds) {
        return new BoundingBox3i(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private enum PendingValidation {
        READY,
        WAIT,
        CANCEL
    }

    private record LastAssemblyGate(@Nullable ServerSubLevel subLevel, int createdAtTick, int levelIdentity) {
    }

    private static final class PendingAssembly {
        private final ResourceKey<Level> dimension;
        private final int levelIdentity;
        private final BlockPos anchor;
        private final LinkedHashSet<BlockPos> blocks;
        private final BoundingBox3i bounds;
        private final int queuedAtTick;
        private final ScanReason reason;

        private PendingAssembly(
                ResourceKey<Level> dimension,
                int levelIdentity,
                BlockPos anchor,
                LinkedHashSet<BlockPos> blocks,
                BoundingBox3i bounds,
                int queuedAtTick,
                ScanReason reason
        ) {
            this.dimension = dimension;
            this.levelIdentity = levelIdentity;
            this.anchor = anchor;
            this.blocks = blocks;
            this.bounds = bounds;
            this.queuedAtTick = queuedAtTick;
            this.reason = reason;
        }

        public int levelIdentity() {
            return levelIdentity;
        }

        public BlockPos anchor() {
            return anchor;
        }

        public LinkedHashSet<BlockPos> blocks() {
            return blocks;
        }

        public BoundingBox3i bounds() {
            return bounds;
        }

        public int queuedAtTick() {
            return queuedAtTick;
        }

        public ScanReason reason() {
            return reason;
        }
    }

    private static final class TrackedSubLevel {
        private final ResourceKey<Level> dimension;
        private final int levelIdentity;
        private final ServerSubLevel subLevel;
        private final int createdAtTick;
        private final int blockCount;
        private final ScanReason reason;
        private int idleTicks;
        private int lastPositionCheckTick;
        private int lastCrushCheckTick;
        private int stationaryCrushTicks;
        private Vector3d lastPosition;
        private Vector3d lastCrushPosition;
        private boolean crushZoneScanned;
        private Set<Long> blockedNoCollisionZone = new HashSet<>();
        private boolean pinned;

        private TrackedSubLevel(ResourceKey<Level> dimension, int levelIdentity, ServerSubLevel subLevel, int createdAtTick, int blockCount, ScanReason reason) {
            this.dimension = dimension;
            this.levelIdentity = levelIdentity;
            this.subLevel = subLevel;
            this.createdAtTick = createdAtTick;
            this.blockCount = blockCount;
            this.reason = reason;
            this.lastPositionCheckTick = createdAtTick;
            this.lastCrushCheckTick = createdAtTick;
            this.lastPosition = new Vector3d(subLevel.logicalPose().position());
            this.lastCrushPosition = new Vector3d(this.lastPosition);
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public int levelIdentity() {
            return levelIdentity;
        }

        public ServerSubLevel subLevel() {
            return subLevel;
        }

        public int blockCount() {
            return blockCount;
        }
    }
}
