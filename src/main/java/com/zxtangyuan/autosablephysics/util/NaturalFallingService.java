package com.zxtangyuan.autosablephysics.util;

import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 原版式自然下落辅助。
 * Vanilla-style falling block helper.
 *
 * <p>0.2.4 版本不再实现复杂“沙堆”。当玩家手动放置横向有限连接材料，且该方块
 * 无底部支撑、也无法在配置半径内连接到稳定锚点时，本服务把它转成原版 FallingBlockEntity，
 * 而不是创建 Sable sub-level。这样可以减少单方块/小方块物理体数量，并更接近沙子/沙砾的原版体验。</p>
 */
public final class NaturalFallingService {
    private static final int MAX_STABILITY_SEARCH_NODES = 512;

    private NaturalFallingService() {
    }

    /**
     * 玩家手动放置横向有限连接方块时，如果超出稳定连接限制，则转为原版下落实体。
     *
     * @return true 表示该方块已经交给 FallingBlockEntity 处理，调用方不应再对它执行完整物理化扫描。
     */
    public static boolean tryConvertManualPlacementToFalling(ServerLevel level, BlockPos pos, BlockState state) {
        if (!ASPServerConfig.MANUAL_PLACED_LIMITED_BLOCKS_USE_FALLING.get()) {
            return false;
        }
        if (state.isAir() || !AssemblyFilters.isHorizontalLimitedMaterial(state)) {
            return false;
        }
        if (!level.isLoaded(pos) || !AssemblyFilters.canMove(level, pos, state)) {
            return false;
        }
        if (AssemblyFilters.hasBottomSupport(level, pos)) {
            return false;
        }
        if (hasStableHorizontalConnection(level, pos, state)) {
            return false;
        }

        crushAtAndBelow(level, pos);
        FallingBlockEntity.fall(level, pos, state);
        return true;
    }

    /**
     * 自动扫描发现单方块悬空组件时，优先转为原版 FallingBlockEntity。
     *
     * <p>这用于替代“为每个单方块都创建一个 Sable sub-level”的重方案，
     * 能明显降低大量小物体带来的 Sable 同步压力。含方块实体的方块不会走该路径，
     * 避免箱子、机器等 NBT 数据在 FallingBlockEntity 中丢失。</p>
     */
    public static boolean tryConvertSingleBlockComponentToFalling(ServerLevel level, BlockPos pos, BlockState state) {
        if (!ASPServerConfig.SINGLE_BLOCK_COMPONENTS_USE_FALLING.get()) {
            return false;
        }
        if (!level.isLoaded(pos) || state.isAir() || !AssemblyFilters.canMove(level, pos, state)) {
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        if (AssemblyFilters.hasBottomSupport(level, pos)) {
            return false;
        }

        crushAtAndBelow(level, pos);
        FallingBlockEntity.fall(level, pos, state);
        return true;
    }

    /**
     * 让原版 FallingBlockEntity 经过无碰撞不可支撑方块时压坏它们。
     * Crush no-collision non-supporting blocks touched by vanilla FallingBlockEntity.
     */
    public static void tickFallingBlock(ServerLevel level, FallingBlockEntity entity) {
        if (!ASPServerConfig.VANILLA_FALLING_BLOCKS_CRUSH_NO_COLLISION.get()) {
            return;
        }
        BlockPos current = entity.blockPosition();
        crushIfNeeded(level, current);
        crushIfNeeded(level, current.below());
    }

    private static void crushAtAndBelow(ServerLevel level, BlockPos pos) {
        crushIfNeeded(level, pos.below());
    }

    private static void crushIfNeeded(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (AssemblyFilters.isCrushableNonSupportingBlock(level, pos, state)) {
            level.destroyBlock(pos, true);
        }
    }

    private static boolean hasStableHorizontalConnection(ServerLevel level, BlockPos origin, BlockState originState) {
        int limit = AssemblyFilters.horizontalConnectivityLimit(originState);
        if (limit == Integer.MAX_VALUE) {
            return true;
        }
        if (limit <= 0) {
            return false;
        }

        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin.asLong());
        queue.add(origin.immutable());

        while (!queue.isEmpty() && visited.size() <= MAX_STABILITY_SEARCH_NODES) {
            BlockPos current = queue.removeFirst();
            BlockState currentState = level.getBlockState(current);

            if (!current.equals(origin)) {
                // 非有限材料（例如原木、石头、机器框架）视为稳定锚点；有底部支撑的有限材料也视为稳定锚点。
                if (!AssemblyFilters.isHorizontalLimitedMaterial(currentState) || AssemblyFilters.hasBottomSupport(level, current)) {
                    return true;
                }
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (visited.contains(next.asLong())) {
                    continue;
                }
                if (AssemblyFilters.horizontalChebyshevDistance(next, origin) > limit) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (!AssemblyFilters.canMove(level, next, nextState)) {
                    continue;
                }
                if (!AssemblyFilters.canComponentConnect(level, current, currentState, next, nextState, direction, origin)) {
                    continue;
                }
                visited.add(next.asLong());
                queue.addLast(next);
            }
        }

        return false;
    }
}
