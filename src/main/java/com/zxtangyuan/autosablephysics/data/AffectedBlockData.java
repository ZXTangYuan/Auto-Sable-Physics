package com.zxtangyuan.autosablephysics.data;

import com.zxtangyuan.autosablephysics.AutoSablePhysics;
import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import com.zxtangyuan.autosablephysics.util.ScanReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 维度级受影响方块状态。
 *
 * <p>这里故意不把状态写进方块本身，也不创建方块实体，而是用 SavedData 按维度保存一组 BlockPos。
 * 自动物理化只把这些“玩家行为影响过”的位置作为候选基础；没有记录的位置默认视作稳定的世界生成地形/外部支撑。</p>
 */
public final class AffectedBlockData extends SavedData {
    private static final String DATA_NAME = AutoSablePhysics.MOD_ID + "_affected_blocks";
    private static final String TAG_AFFECTED = "Affected";

    private final Set<Long> affected = new HashSet<>();

    public static AffectedBlockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AffectedBlockData::new, AffectedBlockData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static AffectedBlockData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        AffectedBlockData data = new AffectedBlockData();
        long[] values = tag.getLongArray(TAG_AFFECTED);
        for (long value : values) {
            data.affected.add(value);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        synchronized (this) {
            long[] values = new long[affected.size()];
            int index = 0;
            for (long value : affected) {
                values[index++] = value;
            }
            tag.putLongArray(TAG_AFFECTED, values);
        }
        return tag;
    }

    /**
     * 根据破坏/放置事件标记受影响区域。
     *
     * <p>树木例外：如果变化方块或本次影响立方体内存在原木/去皮原木，则会把该原木周围半径 7
     * 默认范围内的树叶标记为受影响。原木识别使用原版 {@link BlockTags#LOGS}，通常包含去皮原木。</p>
     */
    public MarkResult markChangedArea(ServerLevel level, BlockPos center, BlockState changedState, ScanReason reason) {
        if (!ASPServerConfig.USE_AFFECTED_BLOCK_STATE.get()) {
            return MarkResult.EMPTY;
        }

        int radius = (reason == ScanReason.BLOCK_PLACE || reason == ScanReason.SUBLEVEL_RESTORE)
                ? ASPServerConfig.AFFECTED_RADIUS_ON_PLACE.get()
                : ASPServerConfig.AFFECTED_RADIUS_ON_BREAK.get();

        int newlyMarked = markCube(center, radius);

        int leavesMarked = 0;
        if (ASPServerConfig.LOG_LEAF_AFFECTED_RADIUS.get() > 0 && ASPServerConfig.MAX_LOG_LEAF_SOURCES_PER_CHANGE.get() > 0) {
            leavesMarked += markLeavesNearLogSources(level, center, radius, changedState);
        }

        if (newlyMarked > 0 || leavesMarked > 0) {
            setDirty();
        }
        return new MarkResult(newlyMarked, leavesMarked, affectedSize());
    }

    public synchronized boolean isAffected(BlockPos pos) {
        return affected.contains(pos.asLong());
    }

    public synchronized boolean isAffected(long packedPos) {
        return affected.contains(packedPos);
    }

    public synchronized int affectedSize() {
        return affected.size();
    }

    public synchronized Set<Long> snapshot() {
        return new HashSet<>(affected);
    }

    public synchronized void clearAll() {
        if (!affected.isEmpty()) {
            affected.clear();
            setDirty();
        }
    }

    public int markCube(BlockPos center, int radius) {
        int marked = 0;
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        synchronized (this) {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (affected.add(pos.asLong())) {
                    marked++;
                }
            }
        }
        return marked;
    }

    private int markLeavesNearLogSources(ServerLevel level, BlockPos center, int baseRadius, BlockState changedState) {
        Set<Long> logSources = new HashSet<>();
        int maxSources = ASPServerConfig.MAX_LOG_LEAF_SOURCES_PER_CHANGE.get();

        if (changedState.is(BlockTags.LOGS)) {
            logSources.add(center.asLong());
        }

        if (logSources.size() < maxSources) {
            BlockPos min = center.offset(-baseRadius, -baseRadius, -baseRadius);
            BlockPos max = center.offset(baseRadius, baseRadius, baseRadius);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (logSources.size() >= maxSources) {
                    break;
                }
                BlockPos immutable = pos.immutable();
                if (!level.isLoaded(immutable)) {
                    continue;
                }
                if (level.getBlockState(immutable).is(BlockTags.LOGS)) {
                    logSources.add(immutable.asLong());
                }
            }
        }

        // 0.2.5：砍树时不能只看本次破坏点附近的几根原木。
        // 玩家从树干底部开始砍时，树叶可能离破坏点超过 2 格；如果不沿着剩余原木向上扩展，
        // 树冠只有在玩家砍到“原木直接贴着树叶”的高度时才会被纳入受影响区域。
        // 这里从本次影响区内的原木/被破坏原木位置出发，沿 6 面连接的原木链扩展，再标记这些原木附近的树叶。
        logSources = expandConnectedLogSources(level, logSources, maxSources);

        int connectedLogsMarked = markPositionsAffected(logSources);
        int leavesMarked = 0;
        for (long packedLogPos : logSources) {
            leavesMarked += markLeavesAround(level, BlockPos.of(packedLogPos), ASPServerConfig.LOG_LEAF_AFFECTED_RADIUS.get());
        }
        return connectedLogsMarked + leavesMarked;
    }

    private int markPositionsAffected(Set<Long> positions) {
        int marked = 0;
        synchronized (this) {
            for (long packed : positions) {
                if (affected.add(packed)) {
                    marked++;
                }
            }
        }
        return marked;
    }

    private Set<Long> expandConnectedLogSources(ServerLevel level, Set<Long> initialSources, int maxSources) {
        if (initialSources.isEmpty() || maxSources <= 0) {
            return Set.of();
        }

        LinkedHashSet<Long> result = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        for (long packed : initialSources) {
            if (result.size() >= maxSources) {
                break;
            }
            if (result.add(packed)) {
                queue.addLast(BlockPos.of(packed));
            }
        }

        while (!queue.isEmpty() && result.size() < maxSources) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                if (result.size() >= maxSources) {
                    break;
                }

                BlockPos next = current.relative(direction).immutable();
                long packedNext = next.asLong();
                if (result.contains(packedNext) || !level.isLoaded(next)) {
                    continue;
                }

                if (level.getBlockState(next).is(BlockTags.LOGS)) {
                    result.add(packedNext);
                    queue.addLast(next);
                }
            }
        }

        return result;
    }

    private int markLeavesAround(ServerLevel level, BlockPos logPos, int radius) {
        int marked = 0;
        BlockPos min = logPos.offset(-radius, -radius, -radius);
        BlockPos max = logPos.offset(radius, radius, radius);
        synchronized (this) {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockPos immutable = pos.immutable();
                if (!level.isLoaded(immutable)) {
                    continue;
                }
                if (level.getBlockState(immutable).is(BlockTags.LEAVES) && affected.add(immutable.asLong())) {
                    marked++;
                }
            }
        }
        return marked;
    }

    public record MarkResult(int newlyMarkedBlocks, int newlyMarkedLeaves, int totalAffectedBlocks) {
        public static final MarkResult EMPTY = new MarkResult(0, 0, 0);
    }
}
