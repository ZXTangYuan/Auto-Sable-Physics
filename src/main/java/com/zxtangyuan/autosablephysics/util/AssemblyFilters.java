package com.zxtangyuan.autosablephysics.util;

import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import com.zxtangyuan.autosablephysics.tag.ASPBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;


/**
 * 自动物理化过滤器：集中处理支撑性、连接性、可移动性和 Sable sub-level 检测。
 * Assembly filters: central support, connectivity, movability, and Sable sub-level checks.
 */
public final class AssemblyFilters {
    private AssemblyFilters() {
    }


    /**
     * 判断给定方块位置是否位于 Sable 的 sub-level plot 内。
     *
     * <p>Sable 会把已经物理化的结构放到独立的 plot/sub-level 坐标区域中。
     * 玩家在已物理化物体内部破坏或放置方块时，也可能触发普通的方块事件。
     * Auto Sable Physics 只应该处理主世界中的静态结构变化，不应该对已经物理化的
     * sub-level 再次执行自动扫描，否则容易造成闪烁、重复装配或额外主线程开销。</p>
     *
     * <p>这里通过反射调用 Sable Companion，避免 Companion 小版本方法签名/返回类型变化时
     * 直接产生编译错误。优先检查 getContaining，再用 isInPlotGrid 兜底。
     * 如果 Companion/Sable 在某个小版本中抛出异常，则保守返回 false，避免把普通世界事件全部吞掉。</p>
     */
    public static boolean isInsideSableSubLevel(ServerLevel level, BlockPos pos) {
        try {
            Object containing = invokeSableCompanion("getContaining", level, pos);
            if (containing instanceof Optional<?> optional) {
                if (optional.isPresent()) {
                    return true;
                }
            } else if (containing != null) {
                return true;
            }

            Object inPlotGrid = invokeSableCompanion("isInPlotGrid", level, pos);
            return Boolean.TRUE.equals(inPlotGrid);
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static Object invokeSableCompanion(String methodName, ServerLevel level, BlockPos pos) throws ReflectiveOperationException {
        Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        Object instance = companionClass.getField("INSTANCE").get(null);
        if (instance == null) {
            return null;
        }

        for (Method method : instance.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 2) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[0].isAssignableFrom(level.getClass())
                    && parameterTypes[1].isAssignableFrom(pos.getClass())) {
                return method.invoke(instance, level, pos);
            }
        }

        return null;
    }

    public static boolean canStartFrom(ServerLevel level, BlockPos pos, BlockState state) {
        return canStartFrom(level, pos, state, false);
    }

    /**
     * 判断某个方块能否作为 Sable 连通搜索的起点。
     *
     * @param ignoreBottomSupportPrecheck 为 true 时，跳过“起点底部不能有支撑”的便宜预过滤。
     *                                    这只允许用于被破坏方块的直接邻位，用来处理“结构靠内部方块互相支撑，
     *                                    但整体已经悬空”的情况。最终装配前仍会检查整个结构是否存在外部底部支撑。
     */
    public static boolean canStartFrom(ServerLevel level, BlockPos pos, BlockState state, boolean ignoreBottomSupportPrecheck) {
        if (!canMove(level, pos, state)) {
            return false;
        }

        // 先做非常便宜的起点过滤，避免每次破坏普通地面方块都把整片地形丢给 Sable BFS。
        if (ASPServerConfig.REQUIRE_START_NEAR_AIR.get() && !isNearAir(level, pos)) {
            return false;
        }

        if (!ignoreBottomSupportPrecheck
                && ASPServerConfig.REQUIRE_START_WITHOUT_BOTTOM_SUPPORT.get()
                && hasBottomSupport(level, pos)) {
            return false;
        }

        return true;
    }

    public static boolean canConnect(
            ServerLevel level,
            BlockPos originPos,
            BlockState originState,
            BlockPos candidatePos,
            BlockState candidateState,
            @Nullable Direction directionFrom,
            BlockPos scanOrigin,
            int maxExpansionRadius,
            @Nullable Set<Long> allowedPositions,
            boolean requireFaceContact
    ) {
        if (requireFaceContact && directionFrom == null) {
            return false;
        }

        if (allowedPositions != null && !allowedPositions.contains(candidatePos.asLong())) {
            return false;
        }

        // 使用受影响状态系统时，allowedPositions 已经是本次允许连接的有限坐标集合。
        // 此时继续套用以起点为中心的切比雪夫半径，会错误截断高柱、树木、长桥等玩家结构。
        if (allowedPositions == null && !insideChebyshevRadius(candidatePos, scanOrigin, maxExpansionRadius)) {
            return false;
        }

        if (!canMove(level, candidatePos, candidateState)) {
            return false;
        }

        if (directionFrom != null) {
            return canComponentConnect(level, originPos, originState, candidatePos, candidateState, directionFrom, scanOrigin);
        }

        return true;
    }

    public static boolean canMove(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }

        if (!ASPServerConfig.ALLOW_BLOCK_ENTITIES.get() && state.hasBlockEntity()) {
            return false;
        }

        if (state.is(ASPBlockTags.IGNORED) || state.is(ASPBlockTags.IMMOBILE)) {
            return false;
        }

        if (state.getBlock() == Blocks.BEDROCK
                || state.getBlock() == Blocks.BARRIER
                || state.getBlock() == Blocks.COMMAND_BLOCK
                || state.getBlock() == Blocks.CHAIN_COMMAND_BLOCK
                || state.getBlock() == Blocks.REPEATING_COMMAND_BLOCK
                || state.getBlock() == Blocks.STRUCTURE_BLOCK
                || state.getBlock() == Blocks.STRUCTURE_VOID
                || state.getBlock() == Blocks.JIGSAW) {
            return false;
        }

        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        // 液体/含流体状态在 Sable 装配里更容易产生怪异结果，暂时排除。
        return state.getFluidState().isEmpty();
    }

    public static boolean hasExternalBottomSupport(ServerLevel level, Set<BlockPos> blocks) {
        for (BlockPos pos : blocks) {
            BlockPos below = pos.below();
            if (blocks.contains(below)) {
                continue;
            }

            if (!level.isLoaded(below)) {
                return true;
            }

            BlockState belowState = level.getBlockState(below);
            if (canProvideBottomSupport(level, below, belowState)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasBottomSupport(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.isLoaded(below)) {
            return true;
        }

        BlockState belowState = level.getBlockState(below);
        return canProvideBottomSupport(level, below, belowState);
    }

    /**
     * 判断某个外部方块是否能从下方支撑一个待物理化组件。
     *
     * <p>判定顺序：
     * 1. 空气 / ignored 永远不支撑。
     * 2. force_supporting 白名单强制支撑。
     * 3. non_supporting 黑名单强制不支撑。
     * 4. 无碰撞体默认不支撑。
     * 5. 可选要求完整碰撞体。
     * 6. 最后使用原版 isFaceSturdy(UP) 作为通用支撑判断。</p>
     */
    public static boolean canProvideBottomSupport(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.is(ASPBlockTags.IGNORED)) {
            return false;
        }

        if (state.is(ASPBlockTags.FORCE_SUPPORTING)) {
            return true;
        }

        if (state.is(ASPBlockTags.NON_SUPPORTING)) {
            return false;
        }

        if (ASPServerConfig.NO_COLLISION_BLOCKS_CANNOT_SUPPORT.get()
                && state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }

        if (ASPServerConfig.REQUIRE_FULL_COLLISION_BLOCK_FOR_SUPPORT.get()
                && !state.isCollisionShapeFullBlock(level, pos)) {
            return false;
        }

        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    /**
     * 自动组件扫描用的连接判断。
     *
     * <p>该方法只处理“两个可移动方块是否属于同一物理体”。
     * 它不会决定某个方块能否被移动；移动资格仍由 canMove 决定。</p>
     */
    public static boolean canComponentConnect(
            ServerLevel level,
            BlockPos fromPos,
            BlockState fromState,
            BlockPos toPos,
            BlockState toState,
            Direction direction
    ) {
        return canComponentConnect(level, fromPos, fromState, toPos, toState, direction, null);
    }

    /**
     * 自动组件扫描用的连接判断，带可选组件起点。
     *
     * <p>0.2.4 起，横向连接限制不再表示“完全不能横向连接”。
     * 它表示某些材料只能在一定半径内横向延伸：树叶默认 7 格，泥土/草方块等默认更长，
     * 沙子/沙砾等颗粒材料默认很短。这样能避免无限横向吞地形，同时保留自然树冠、洞穴边缘、
     * 泥土层等合理连接。</p>
     */
    public static boolean canComponentConnect(
            ServerLevel level,
            BlockPos fromPos,
            BlockState fromState,
            BlockPos toPos,
            BlockState toState,
            Direction direction,
            @Nullable BlockPos componentOrigin
    ) {
        if (!canMove(level, fromPos, fromState) || !canMove(level, toPos, toState)) {
            return false;
        }

        if (fromState.is(ASPBlockTags.FORCE_CONNECTING) || toState.is(ASPBlockTags.FORCE_CONNECTING)) {
            return true;
        }

        if (fromState.is(ASPBlockTags.NON_CONNECTING) || toState.is(ASPBlockTags.NON_CONNECTING)) {
            return false;
        }

        if (ASPServerConfig.NO_COLLISION_BLOCKS_DO_NOT_CONNECT.get()
                && (fromState.getCollisionShape(level, fromPos).isEmpty()
                || toState.getCollisionShape(level, toPos).isEmpty())) {
            return false;
        }

        if (componentOrigin != null
                && ASPServerConfig.ENABLE_HORIZONTAL_CONNECTIVITY_LIMITS.get()
                && direction != Direction.UP && direction != Direction.DOWN) {
            // 树叶的横向有限连接不能以“组件起点”为唯一锚点。
            // 砍树时组件起点经常是树干底部；如果直接用底部起点限制树叶，
            // 分枝树冠会被错误切断，表现为必须砍到原木贴近树叶时才触发。
            // 正确语义是：树叶可以围绕附近原木形成有限树冠。
            if (isLeafLimited(fromState) || isLeafLimited(toState)) {
                return canConnectLeafLimitedBlock(level, fromPos, fromState, toPos, toState, componentOrigin);
            }

            int limit = Math.min(horizontalConnectivityLimit(fromState), horizontalConnectivityLimit(toState));
            if (limit != Integer.MAX_VALUE && horizontalChebyshevDistance(toPos, componentOrigin) > limit) {
                return false;
            }
        }

        return true;
    }

    private static boolean canConnectLeafLimitedBlock(
            ServerLevel level,
            BlockPos fromPos,
            BlockState fromState,
            BlockPos toPos,
            BlockState toState,
            BlockPos componentOrigin
    ) {
        int limit = ASPServerConfig.LEAF_HORIZONTAL_CONNECTIVITY_LIMIT.get();
        if (limit <= 0) {
            return false;
        }

        // 原木与树叶直接接触时永远允许连接。
        // 这覆盖“树干/分枝支撑树冠”的核心情况，不受组件起点限制影响。
        if ((fromState.is(BlockTags.LOGS) && isLeafLimited(toState))
                || (toState.is(BlockTags.LOGS) && isLeafLimited(fromState))) {
            return true;
        }

        // 树叶与树叶横向连接时，以附近原木为锚点，而不是以扫描起点为锚点。
        // 只要任一端树叶在配置半径内能找到原木，就认为它属于某棵树的有限树冠。
        if (isLeafLimited(fromState) || isLeafLimited(toState)) {
            if (hasNearbyLog(level, fromPos, limit) || hasNearbyLog(level, toPos, limit)) {
                return true;
            }
        }

        // 没有附近原木的孤立树叶，不允许无限横向扩散；退回组件起点半径限制。
        return horizontalChebyshevDistance(toPos, componentOrigin) <= limit;
    }

    private static boolean isLeafLimited(BlockState state) {
        return state.is(ASPBlockTags.LEAF_HORIZONTAL_LIMITED) || state.is(BlockTags.LEAVES);
    }

    private static boolean hasNearbyLog(ServerLevel level, BlockPos center, int radius) {
        if (radius <= 0) {
            return false;
        }
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos immutable = pos.immutable();
            if (!level.isLoaded(immutable)) {
                continue;
            }
            if (level.getBlockState(immutable).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHorizontalLimitedMaterial(BlockState state) {
        return horizontalConnectivityLimit(state) != Integer.MAX_VALUE;
    }

    public static int horizontalConnectivityLimit(BlockState state) {
        if (!ASPServerConfig.ENABLE_HORIZONTAL_CONNECTIVITY_LIMITS.get()) {
            return Integer.MAX_VALUE;
        }
        if (state.is(ASPBlockTags.FORCE_CONNECTING)) {
            return Integer.MAX_VALUE;
        }
        if (state.is(ASPBlockTags.GRANULAR_HORIZONTAL_LIMITED)) {
            return ASPServerConfig.GRANULAR_HORIZONTAL_CONNECTIVITY_LIMIT.get();
        }
        if (state.is(ASPBlockTags.LEAF_HORIZONTAL_LIMITED)) {
            return ASPServerConfig.LEAF_HORIZONTAL_CONNECTIVITY_LIMIT.get();
        }
        if (state.is(ASPBlockTags.TERRAIN_HORIZONTAL_LIMITED)) {
            return ASPServerConfig.TERRAIN_HORIZONTAL_CONNECTIVITY_LIMIT.get();
        }
        if (state.is(ASPBlockTags.LIMITED_HORIZONTAL_CONNECTING) || state.is(ASPBlockTags.NO_HORIZONTAL_CONNECTING)) {
            return ASPServerConfig.DEFAULT_HORIZONTAL_CONNECTIVITY_LIMIT.get();
        }
        return Integer.MAX_VALUE;
    }

    public static int horizontalChebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    public static boolean isCrushableNonSupportingBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!ASPServerConfig.CRUSH_NO_COLLISION_SUPPORTS.get()) {
            return false;
        }
        if (state.isAir() || state.is(ASPBlockTags.IGNORED) || state.is(ASPBlockTags.IMMOBILE)) {
            return false;
        }
        if (state.is(ASPBlockTags.FORCE_SUPPORTING)) {
            return false;
        }
        return state.getCollisionShape(level, pos).isEmpty() && !canProvideBottomSupport(level, pos, state);
    }

    public static void crushExternalNonSupportingBlocks(ServerLevel level, Set<BlockPos> movingBlocks) {
        if (!ASPServerConfig.CRUSH_NO_COLLISION_SUPPORTS.get() || movingBlocks.isEmpty()) {
            return;
        }
        Set<BlockPos> crushed = new java.util.HashSet<>();
        for (BlockPos block : movingBlocks) {
            BlockPos below = block.below().immutable();
            if (movingBlocks.contains(below) || crushed.contains(below) || !level.isLoaded(below)) {
                continue;
            }
            BlockState belowState = level.getBlockState(below);
            if (isCrushableNonSupportingBlock(level, below, belowState)) {
                level.destroyBlock(below, true);
                crushed.add(below);
            }
        }
    }

    public static boolean isNearAir(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor)) {
                continue;
            }
            if (level.getBlockState(neighbor).isAir()) {
                return true;
            }
        }
        return false;
    }

    public static boolean insideChebyshevRadius(BlockPos pos, BlockPos center, int radius) {
        return Math.abs(pos.getX() - center.getX()) <= radius
                && Math.abs(pos.getY() - center.getY()) <= radius
                && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    public static boolean touchesChebyshevBoundary(Set<BlockPos> blocks, BlockPos center, int radius) {
        for (BlockPos pos : blocks) {
            if (Math.abs(pos.getX() - center.getX()) >= radius
                    || Math.abs(pos.getY() - center.getY()) >= radius
                    || Math.abs(pos.getZ() - center.getZ()) >= radius) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWithinChebyshevRadius(BlockPos pos, BlockPos center, int radius) {
        return Math.abs(pos.getX() - center.getX()) <= radius
                && Math.abs(pos.getY() - center.getY()) <= radius
                && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }
}
