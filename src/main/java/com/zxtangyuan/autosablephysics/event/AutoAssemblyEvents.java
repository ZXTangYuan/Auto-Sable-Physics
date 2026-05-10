package com.zxtangyuan.autosablephysics.event;

import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import com.zxtangyuan.autosablephysics.data.AffectedBlockData;
import com.zxtangyuan.autosablephysics.util.AssemblyFilters;
import com.zxtangyuan.autosablephysics.util.AssemblyQueue;
import com.zxtangyuan.autosablephysics.util.DelayedAssemblyManager;
import com.zxtangyuan.autosablephysics.util.ScanReason;
import com.zxtangyuan.autosablephysics.util.NaturalFallingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * 运行期事件入口：把各种方块变化统一转为受影响标记和扫描任务。
 * Runtime event entry: converts block-change events into affected markers and scan jobs.
 */
public final class AutoAssemblyEvents {
    private static final Map<UUID, Boolean> ENDERMAN_CARRIED_STATE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> NEIGHBOR_EVENT_TICK = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> NEIGHBOR_EVENT_COUNT = new HashMap<>();

    private AutoAssemblyEvents() {
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        processChangedPosition(level, event.getPos(), event.getState(), ScanReason.BLOCK_BREAK, true);
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState placedState = level.getBlockState(pos);

        // 0.2.9：静止物理体会把自身占用的普通世界网格登记为“禁止放置无碰撞方块区域”。
        // 玩家在这些区域内放置火把、拉杆、按钮、花草等无碰撞不可支撑方块时，立即破坏并掉落，
        // 等价于该位置仍被静止 sub-level 占据。
        if (event.getEntity() instanceof Player
                && DelayedAssemblyManager.destroyForbiddenNoCollisionPlacement(level, pos, placedState)) {
            return;
        }

        if (!processChangedPosition(level, pos, placedState, ScanReason.BLOCK_PLACE, ASPServerConfig.TRIGGER_ON_PLACE.get())) {
            return;
        }

        // 玩家手动放置横向有限连接方块且超出稳定连接限制时，优先走原版 FallingBlockEntity，
        // 不创建 Sable 物理体。这样保留“自然掉落”玩法，同时减少大量小型 sub-level。
        if (event.getEntity() instanceof Player
                && NaturalFallingService.tryConvertManualPlacementToFalling(level, pos, placedState)) {
            return;
        }

        // 完整放置扫描默认关闭，但小型悬空物体如果完全没有连接点，后续不会再自然产生破坏事件。
        // 因此这里为“没有底部支撑的新放置方块”提供一个极小预算的小物体复查队列；
        // 该队列只会尝试 smallObjectMaxBlocks 以内的小结构，不会触发完整受影响区域扫描。
        if (!ASPServerConfig.TRIGGER_ON_PLACE.get()
                && ASPServerConfig.TRIGGER_SMALL_OBJECT_SCAN_ON_PLACE.get()
                && ASPServerConfig.ENABLE_SMALL_OBJECT_FALLBACK.get()
                && !AssemblyFilters.hasBottomSupport(level, pos)) {
            AssemblyQueue.enqueue(level, pos, ScanReason.SMALL_OBJECT_CHECK);
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!ASPServerConfig.ENABLE_EXPLOSION_EVENTS.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        List<BlockPos> affected = new ArrayList<>(event.getAffectedBlocks());
        if (affected.isEmpty()) {
            return;
        }

        BlockPos estimatedCenter = estimateCenter(affected);
        affected.sort(Comparator
                .comparingLong((BlockPos pos) -> distanceSquared(pos, estimatedCenter))
                .thenComparingLong(BlockPos::asLong));

        int markLimit = Math.min(affected.size(), ASPServerConfig.MAX_EXPLOSION_BLOCKS_TO_MARK.get());

        LinkedHashSet<BlockPos> marked = new LinkedHashSet<>();
        for (int i = 0; i < markLimit; i++) {
            BlockPos pos = affected.get(i).immutable();
            if (shouldIgnoreSableSubLevelEvent(level, pos) || !level.isLoaded(pos)) {
                continue;
            }
            AffectedBlockData.get(level).markChangedArea(level, pos, level.getBlockState(pos), ScanReason.EXPLOSION);
            marked.add(pos);
        }

        // 0.1.10：爆炸只入队一个区域扫描中心。爆炸事件本身可能包含大量方块，
        // 多中心同时扫描会制造重复组件和过早 sub-level 创建。
        if (!marked.isEmpty()) {
            AssemblyQueue.enqueue(level, estimateCenter(new ArrayList<>(marked)), ScanReason.EXPLOSION);
        }
    }

    public static void onPistonPost(PistonEvent.Post event) {
        if (!ASPServerConfig.ENABLE_PISTON_EVENTS.get()) {
            return;
        }
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        BlockPos pistonPos = event.getPos().immutable();
        Direction direction = event.getDirection();
        positions.add(pistonPos);
        positions.add(event.getFaceOffsetPos().immutable());

        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver != null) {
            for (BlockPos pushed : resolver.getToPush()) {
                BlockPos original = pushed.immutable();
                positions.add(original);
                positions.add(original.relative(direction).immutable());
            }
            for (BlockPos destroyed : resolver.getToDestroy()) {
                positions.add(destroyed.immutable());
            }
        }

        processChangedPositions(level, positions, ScanReason.PISTON, ASPServerConfig.MAX_PISTON_BLOCKS_TO_MARK.get(), true);
    }

    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (!ASPServerConfig.ENABLE_LIVING_DESTROY_BLOCK_EVENTS.get()) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        processChangedPosition(level, event.getPos(), event.getState(), ScanReason.LIVING_DESTROY_BLOCK, true);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!ASPServerConfig.ENABLE_FALLING_BLOCK_EVENTS.get()) {
            return;
        }
        if (!(event.getEntity() instanceof FallingBlockEntity fallingBlock)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = fallingBlock.blockPosition().immutable();
        processChangedPosition(level, pos, level.getBlockState(pos), ScanReason.FALLING_BLOCK, true);
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof FallingBlockEntity fallingBlock && entity.level() instanceof ServerLevel level) {
            NaturalFallingService.tickFallingBlock(level, fallingBlock);
        }
        if (ASPServerConfig.ENABLE_ENDERMAN_CARRY_TRACKING.get() && entity instanceof EnderMan enderMan) {
            handleEndermanCarryTick(enderMan);
        }
    }

    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!ASPServerConfig.ENABLE_FLUID_PLACE_BLOCK_EVENTS.get()) {
            return;
        }
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        positions.add(event.getPos().immutable());
        positions.add(event.getLiquidPos().immutable());
        processChangedPositions(level, positions, ScanReason.FLUID_PLACE_BLOCK, 2, true);
    }

    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!ASPServerConfig.ENABLE_FARMLAND_TRAMPLE_EVENTS.get()) {
            return;
        }
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        processChangedPosition(level, event.getPos(), event.getState(), ScanReason.FARMLAND_TRAMPLE, true);
    }

    public static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (!ASPServerConfig.ENABLE_TOOL_MODIFICATION_EVENTS.get() || event.isSimulated()) {
            return;
        }
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null) {
            return;
        }

        BlockState finalState = event.getFinalState();
        if (finalState == null) {
            finalState = event.getState();
        }
        processChangedPosition(level, event.getPos(), finalState, ScanReason.TOOL_MODIFICATION, true);
    }

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!ASPServerConfig.ENABLE_NEIGHBOR_PHYSICS_EVENTS.get()) {
            return;
        }
        ServerLevel level = asServerLevel(event.getLevel());
        if (level == null || !consumeNeighborBudget(level)) {
            return;
        }

        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        BlockPos center = event.getPos().immutable();
        positions.add(center);
        for (Direction direction : event.getNotifiedSides()) {
            positions.add(center.relative(direction).immutable());
        }
        processChangedPositions(level, positions, ScanReason.NEIGHBOR_PHYSICS, 1 + event.getNotifiedSides().size(), true);
    }

    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        AssemblyQueue.tick(level);
        DelayedAssemblyManager.tick(level);
    }


    public static void onServerStopped(ServerStoppedEvent event) {
        AssemblyQueue.resetAll();
        DelayedAssemblyManager.resetAll();
        ENDERMAN_CARRIED_STATE.clear();
        NEIGHBOR_EVENT_TICK.clear();
        NEIGHBOR_EVENT_COUNT.clear();
    }

    private static void handleEndermanCarryTick(EnderMan enderMan) {
        if (!(enderMan.level() instanceof ServerLevel level)) {
            return;
        }

        UUID uuid = enderMan.getUUID();
        boolean carrying = enderMan.getCarriedBlock() != null;
        Boolean previous = ENDERMAN_CARRIED_STATE.put(uuid, carrying);
        if (previous == null) {
            return;
        }
        if (previous == carrying) {
            return;
        }

        BlockPos pos = enderMan.blockPosition().immutable();
        processChangedPosition(level, pos, level.getBlockState(pos), ScanReason.ENDERMAN_CARRY, true);
    }

    private static boolean processChangedPosition(ServerLevel level, BlockPos pos, BlockState changedState, ScanReason reason, boolean enqueue) {
        BlockPos immutable = pos.immutable();
        if (shouldIgnoreSableSubLevelEvent(level, immutable)) {
            return false;
        }

        AffectedBlockData.get(level).markChangedArea(level, immutable, changedState, reason);
        if (enqueue) {
            AssemblyQueue.enqueue(level, immutable, reason);
        }
        return true;
    }

    private static void processChangedPositions(ServerLevel level, Iterable<BlockPos> positions, ScanReason reason, int maxPositions, boolean enqueue) {
        int processed = 0;
        List<BlockPos> marked = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (processed >= maxPositions) {
                break;
            }
            BlockPos immutable = pos.immutable();
            if (!level.isLoaded(immutable) || shouldIgnoreSableSubLevelEvent(level, immutable)) {
                continue;
            }
            AffectedBlockData.get(level).markChangedArea(level, immutable, level.getBlockState(immutable), reason);
            marked.add(immutable);
            processed++;
        }

        // 多方块事件合并为一次区域扫描。这样活塞、流体、邻居通知等事件先完成标记，
        // 再由队列延迟到事件稳定后扫描，避免同一事件制造多个重复 Sable 装配任务。
        if (enqueue && !marked.isEmpty()) {
            AssemblyQueue.enqueue(level, estimateCenter(marked), reason);
        }
    }

    private static ServerLevel asServerLevel(LevelAccessor accessor) {
        return accessor instanceof ServerLevel level ? level : null;
    }

    private static boolean shouldIgnoreSableSubLevelEvent(ServerLevel level, BlockPos pos) {
        return ASPServerConfig.IGNORE_EVENTS_INSIDE_SABLE_SUB_LEVELS.get()
                && AssemblyFilters.isInsideSableSubLevel(level, pos);
    }

    private static boolean consumeNeighborBudget(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        int tick = level.getServer().getTickCount();
        int lastTick = NEIGHBOR_EVENT_TICK.getOrDefault(dimension, Integer.MIN_VALUE);
        if (lastTick != tick) {
            NEIGHBOR_EVENT_TICK.put(dimension, tick);
            NEIGHBOR_EVENT_COUNT.put(dimension, 0);
        }

        int count = NEIGHBOR_EVENT_COUNT.getOrDefault(dimension, 0);
        if (count >= ASPServerConfig.MAX_NEIGHBOR_EVENTS_PER_TICK.get()) {
            return false;
        }
        NEIGHBOR_EVENT_COUNT.put(dimension, count + 1);
        return true;
    }

    private static BlockPos estimateCenter(List<BlockPos> positions) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockPos pos : positions) {
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }
        int size = Math.max(1, positions.size());
        return new BlockPos((int) (x / size), (int) (y / size), (int) (z / size));
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
