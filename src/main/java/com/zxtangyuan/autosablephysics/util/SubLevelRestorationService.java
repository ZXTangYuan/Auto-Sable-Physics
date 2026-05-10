package com.zxtangyuan.autosablephysics.util;

import com.zxtangyuan.autosablephysics.AutoSablePhysics;
import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import com.zxtangyuan.autosablephysics.data.AffectedBlockData;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将 Auto Sable Physics 创建的 Sable sub-level 近似还原为原版世界方块。
 *
 * <p>还原策略是保守的：先从 plot 坐标中保存方块状态与方块实体 NBT，再将 sub-level 标记移除，最后把保存的方块
 * 放置到原版世界近似网格位置。由于 Sable 1.2.2 没有稳定公开的 disassemble API，本类不尝试保持旋转后的精确形状，
 * 而是按 sub-level 当前全局 AABB 的最小角与 plot 内局部偏移近似落格。</p>
 */
public final class SubLevelRestorationService {
    private SubLevelRestorationService() {
    }

    public static RestoreResult restoreSubLevel(ServerLevel level, ServerSubLevel subLevel, boolean force, boolean markAffected) {
        if (subLevel == null || subLevel.isRemoved()) {
            return RestoreResult.failure("sub-level is removed");
        }
        if (!force && DelayedAssemblyManager.isSubLevelPinned(subLevel.getUniqueId())) {
            return RestoreResult.failure("sub-level is pinned");
        }
        if (!force && !ASPServerConfig.ENABLE_AUTO_RESTORE_SUB_LEVELS.get()) {
            return RestoreResult.failure("auto restore disabled");
        }

        try {
            BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
            if (plotBounds == null || plotBounds.volume() <= 0) {
                return RestoreResult.failure("empty plot bounds");
            }

            List<SavedBlock> savedBlocks = captureBlocks(level, plotBounds);
            if (savedBlocks.isEmpty()) {
                subLevel.markRemoved();
                DelayedAssemblyManager.forgetSubLevel(subLevel.getUniqueId());
                return RestoreResult.success(0);
            }

            BoundingBox3dc globalBounds = subLevel.boundingBox();
            PlacementBasis placementBasis = computePlacementBasis(savedBlocks, globalBounds);
            List<PlacedBlock> placedBlocks = placeBlocks(level, savedBlocks, placementBasis);

            // 先把 plot 内原方块清掉，再移除 sub-level，降低重复实体/重复方块风险。
            clearPlotBlocks(level, savedBlocks);
            subLevel.deleteAllEntities();
            subLevel.markRemoved();
            DelayedAssemblyManager.forgetSubLevel(subLevel.getUniqueId());

            if (markAffected) {
                markRestoredArea(level, placedBlocks);
            }
            return RestoreResult.success(placedBlocks.size());
        } catch (Throwable throwable) {
            AutoSablePhysics.LOGGER.error("Failed to restore Sable sub-level {} in {}", subLevel.getUniqueId(), level.dimension().location(), throwable);
            return RestoreResult.failure(throwable.getClass().getSimpleName());
        }
    }

    private static List<SavedBlock> captureBlocks(ServerLevel level, BoundingBox3ic plotBounds) {
        List<SavedBlock> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(), plotBounds.maxX(), plotBounds.maxY(), plotBounds.maxZ())) {
            BlockPos immutable = pos.immutable();
            if (!level.isLoaded(immutable)) {
                continue;
            }
            BlockState state = level.getBlockState(immutable);
            if (state.isAir()) {
                continue;
            }
            CompoundTag blockEntityTag = null;
            BlockEntity blockEntity = level.getBlockEntity(immutable);
            if (blockEntity != null) {
                blockEntityTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                if (blockEntity instanceof RandomizableContainer container) {
                    container.setLootTable(null);
                }
                if (blockEntity instanceof Clearable clearable) {
                    clearable.clearContent();
                }
            }
            blocks.add(new SavedBlock(immutable, state, blockEntityTag));
        }
        return blocks;
    }

    private static PlacementBasis computePlacementBasis(List<SavedBlock> savedBlocks, BoundingBox3dc globalBounds) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (SavedBlock saved : savedBlocks) {
            BlockPos pos = saved.plotPos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 中文：0.2.2 起，不能用 globalBounds.min 的 floor 作为还原原点。
        // EN: Since 0.2.2, do not use floor(globalBounds.min) as the restore origin.
        // Sable 的 global AABB 通常以方块中心/碰撞盒为基准，min 可能落在 block - 0.5。
        // 直接 floor 会让还原整体向负 X/Y/Z 偏移一格，例如 57,72,12 -> 56,71,11。
        // 因此这里用“全局 AABB 中心”对齐“plot 内保存方块集合的体素中心”，再四舍五入到原版方块网格。
        double globalCenterX = (globalBounds.minX() + globalBounds.maxX()) * 0.5D;
        double globalCenterY = (globalBounds.minY() + globalBounds.maxY()) * 0.5D;
        double globalCenterZ = (globalBounds.minZ() + globalBounds.maxZ()) * 0.5D;

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        int targetMinX = roundToBlockGrid(globalCenterX - sizeX * 0.5D);
        int targetMinY = roundToBlockGrid(globalCenterY - sizeY * 0.5D);
        int targetMinZ = roundToBlockGrid(globalCenterZ - sizeZ * 0.5D);

        return new PlacementBasis(minX, minY, minZ, new BlockPos(targetMinX, targetMinY, targetMinZ));
    }

    private static int roundToBlockGrid(double value) {
        return (int) Math.floor(value + 0.5D);
    }

    private static List<PlacedBlock> placeBlocks(ServerLevel level, List<SavedBlock> savedBlocks, PlacementBasis basis) {
        List<PlacedBlock> placed = new ArrayList<>();
        Set<Long> reserved = new LinkedHashSet<>();
        for (SavedBlock saved : savedBlocks) {
            BlockPos localOffset = saved.plotPos().offset(-basis.plotMinX(), -basis.plotMinY(), -basis.plotMinZ());
            BlockPos desired = basis.targetMin().offset(localOffset.getX(), localOffset.getY(), localOffset.getZ());
            BlockPos target = findPlacementTarget(level, desired, reserved);
            if (target == null) {
                continue;
            }
            reserved.add(target.asLong());

            level.setBlock(target, saved.state(), Block.UPDATE_ALL);
            if (saved.blockEntityTag() != null) {
                BlockEntity newBlockEntity = level.getBlockEntity(target);
                if (newBlockEntity != null) {
                    CompoundTag tag = saved.blockEntityTag().copy();
                    tag.putInt("x", target.getX());
                    tag.putInt("y", target.getY());
                    tag.putInt("z", target.getZ());
                    newBlockEntity.loadWithComponents(tag, level.registryAccess());
                    newBlockEntity.setChanged();
                }
            }
            placed.add(new PlacedBlock(target.immutable(), saved.state()));
        }
        return placed;
    }

    private static @Nullable BlockPos findPlacementTarget(ServerLevel level, BlockPos desired, Set<Long> reserved) {
        if (canPlaceAt(level, desired, reserved)) {
            return desired.immutable();
        }

        int radius = ASPServerConfig.RESTORE_COLLISION_SEARCH_RADIUS.get();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(desired.offset(-radius, -radius, -radius), desired.offset(radius, radius, radius))) {
            candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingLong(pos -> distanceSquared(pos, desired)));
        for (BlockPos candidate : candidates) {
            if (canPlaceAt(level, candidate, reserved)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean canPlaceAt(ServerLevel level, BlockPos pos, Set<Long> reserved) {
        if (reserved.contains(pos.asLong()) || !level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).canBeReplaced();
    }

    private static void clearPlotBlocks(ServerLevel level, List<SavedBlock> savedBlocks) {
        for (SavedBlock saved : savedBlocks) {
            if (!level.isLoaded(saved.plotPos())) {
                continue;
            }
            level.setBlock(saved.plotPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void markRestoredArea(ServerLevel level, List<PlacedBlock> placedBlocks) {
        if (placedBlocks.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos center = new BlockPos.MutableBlockPos();
        long x = 0;
        long y = 0;
        long z = 0;
        for (PlacedBlock placed : placedBlocks) {
            BlockPos pos = placed.pos();
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
            AffectedBlockData.get(level).markChangedArea(level, pos, placed.state(), ScanReason.SUBLEVEL_RESTORE);
        }
        int size = placedBlocks.size();
        center.set((int) (x / size), (int) (y / size), (int) (z / size));
        AssemblyQueue.enqueue(level, center.immutable(), ScanReason.SUBLEVEL_RESTORE);
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record PlacementBasis(int plotMinX, int plotMinY, int plotMinZ, BlockPos targetMin) {
    }

    private record SavedBlock(BlockPos plotPos, BlockState state, @Nullable CompoundTag blockEntityTag) {
    }

    private record PlacedBlock(BlockPos pos, BlockState state) {
    }

    public record RestoreResult(boolean success, int blocksRestored, String message) {
        public static RestoreResult success(int blocksRestored) {
            return new RestoreResult(true, blocksRestored, "ok");
        }

        public static RestoreResult failure(String message) {
            return new RestoreResult(false, 0, message);
        }
    }
}
