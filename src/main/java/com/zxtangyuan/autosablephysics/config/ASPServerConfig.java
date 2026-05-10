package com.zxtangyuan.autosablephysics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务器配置集中定义。
 * Server-side configuration definitions for Auto Sable Physics.
 */
public final class ASPServerConfig {
    private ASPServerConfig() {
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // 中文：下列配置按功能分组，完整中文/英文解释见 docs/AutoSablePhysics-模组介绍与配置说明.md。
    // EN: Config entries are grouped by feature; see docs/AutoSablePhysics-User-Config-Guide.md for bilingual explanations.

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("总开关。关闭后不会自动把方块组装成 Sable sub-level。")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue DELAY_TICKS = BUILDER
            .comment("方块变化后延迟多少 tick 再扫描。建议至少 1，让原版/其他模组先完成方块更新。")
            .defineInRange("delayTicks", 1, 0, 200);

    public static final ModConfigSpec.IntValue TRIGGER_RADIUS = BUILDER
            .comment("自动触发时，从变化中心向外寻找候选起点的半径。0.1.1 默认改为 2，避免一次破坏扫描过大的立方体。")
            .defineInRange("triggerRadius", 2, 1, 32);

    public static final ModConfigSpec.IntValue MANUAL_SCAN_RADIUS = BUILDER
            .comment("/autosablephysics scan_here 不带参数时使用的半径。手动命令仍会同步扫描，半径不要开太大。")
            .defineInRange("manualScanRadius", 4, 1, 64);

    public static final ModConfigSpec.IntValue MAX_EXPANSION_RADIUS = BUILDER
            .comment("单次连通搜索允许从候选起点向外扩展的最大切比雪夫半径。越大越容易识别大型物体，但越吃性能。")
            .defineInRange("maxExpansionRadius", 12, 4, 128);

    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_ASSEMBLY = BUILDER
            .comment("单个物理化物体最多允许包含多少方块。0.1.1 默认改为 512，避免误扫地形时主线程长时间卡死。")
            .defineInRange("maxBlocksPerAssembly", 512, 1, 256000);

    public static final ModConfigSpec.IntValue MIN_BLOCKS_PER_ASSEMBLY = BUILDER
            .comment("低于该方块数的连通结构不自动物理化。0.1.5 默认改为 1，使被玩家挖成悬空的单个方块也能物理化。")
            .defineInRange("minBlocksPerAssembly", 1, 1, 256000);

    public static final ModConfigSpec.IntValue AUTO_AFFECTED_MIN_BLOCKS_PER_ASSEMBLY = BUILDER
            .comment("受影响状态系统启用时，自动扫描使用的最小结构方块数上限。用于覆盖旧 serverconfig 中 minBlocksPerAssembly=2 导致单个悬空方块无法物理化的问题。")
            .defineInRange("autoAffectedMinBlocksPerAssembly", 1, 1, 256000);

    public static final ModConfigSpec.IntValue MAX_ORIGINS_PER_SCAN = BUILDER
            .comment("每个扫描任务最多尝试多少个候选起点。0.1.1 默认改为 12，避免一次破坏反复 BFS 扫描同一片地形。")
            .defineInRange("maxOriginsPerScan", 12, 1, 8192);

    public static final ModConfigSpec.IntValue MAX_JOBS_PER_LEVEL_TICK = BUILDER
            .comment("每个维度每 tick 最多处理多少个扫描任务。0.1.1 默认改为 1，让多个破坏事件分帧执行。")
            .defineInRange("maxJobsPerLevelTick", 1, 1, 256);

    public static final ModConfigSpec.IntValue AUTO_MAX_JOBS_PER_LEVEL_TICK = BUILDER
            .comment("自动队列每个维度每 tick 的安全任务上限。用于覆盖旧配置文件里过大的 maxJobsPerLevelTick。")
            .defineInRange("autoMaxJobsPerLevelTick", 1, 1, 256);

    public static final ModConfigSpec.IntValue SAME_POSITION_COOLDOWN_TICKS = BUILDER
            .comment("同一个方块位置重复入队的冷却时间。连锁方块更新较多时可减少重复扫描。")
            .defineInRange("samePositionCooldownTicks", 20, 0, 1200);

    public static final ModConfigSpec.IntValue AUTO_MAX_TRIGGER_RADIUS = BUILDER
            .comment("自动触发扫描的安全半径上限。用于覆盖旧配置文件里过大的 triggerRadius；手动命令不受影响。")
            .defineInRange("autoMaxTriggerRadius", 2, 1, 32);

    public static final ModConfigSpec.IntValue AUTO_MAX_EXPANSION_RADIUS = BUILDER
            .comment("自动触发扫描的连通扩展安全上限。用于覆盖旧配置文件里过大的 maxExpansionRadius；手动命令不受影响。")
            .defineInRange("autoMaxExpansionRadius", 12, 4, 128);

    public static final ModConfigSpec.IntValue AUTO_MAX_BLOCKS_PER_ASSEMBLY = BUILDER
            .comment("自动触发扫描的单结构方块数安全上限。用于覆盖旧配置文件里过大的 maxBlocksPerAssembly；手动命令不受影响。")
            .defineInRange("autoMaxBlocksPerAssembly", 512, 1, 256000);

    public static final ModConfigSpec.IntValue AUTO_MAX_ORIGINS_PER_SCAN = BUILDER
            .comment("自动触发扫描的候选起点安全上限。用于覆盖旧配置文件里过大的 maxOriginsPerScan；手动命令不受影响。")
            .defineInRange("autoMaxOriginsPerScan", 12, 1, 8192);

    public static final ModConfigSpec.BooleanValue REQUIRE_START_NEAR_AIR = BUILDER
            .comment("候选起点必须至少贴着一个空气方块。开启后可以大量减少地形内部扫描。")
            .define("requireStartNearAir", true);

    public static final ModConfigSpec.BooleanValue REQUIRE_START_WITHOUT_BOTTOM_SUPPORT = BUILDER
            .comment("候选起点底部如果仍有外部实心支撑，则跳过。开启后可以避免破坏地面方块时扫描整片地形。")
            .define("requireStartWithoutBottomSupport", true);

    public static final ModConfigSpec.IntValue TOO_LARGE_REJECT_RADIUS = BUILDER
            .comment("某个候选起点被判定为过大结构或触边后，跳过其附近多少格内的其他候选，避免重复扫描同一片山体/地面。")
            .defineInRange("tooLargeRejectRadius", 5, 0, 32);

    public static final ModConfigSpec.BooleanValue RELAX_START_SUPPORT_FOR_DIRECT_BREAK_NEIGHBORS = BUILDER
            .comment("破坏方块后，对被破坏方块的直接相邻方块放宽“起点底部不能有支撑”的预过滤。开启后可以识别先断下方支撑、再断上方连接导致的悬空结构；最终仍会执行整个结构的外部底部支撑检查。")
            .define("relaxStartSupportForDirectBreakNeighbors", true);

    public static final ModConfigSpec.IntValue MAX_RELAXED_BREAK_NEIGHBOR_ORIGINS = BUILDER
            .comment("每次破坏方块后，最多允许多少个直接邻位使用放宽起点过滤。降低可减少误扫地形，提高可补偿侧向/上方连接断裂。建议范围 2-6。")
            .defineInRange("maxRelaxedBreakNeighborOrigins", 6, 0, 6);

    public static final ModConfigSpec.BooleanValue REQUIRE_FACE_CONTACT_FOR_AUTO_ASSEMBLY = BUILDER
            .comment("自动扫描时是否只允许通过面接触连接方块。开启后会拒绝 Sable 默认允许的边接触斜向连接，避免柱子/树木在底部被破坏后仍通过旁边地面边缘斜连而被误判为有支撑。手动命令不受影响。")
            .define("requireFaceContactForAutoAssembly", true);

    public static final ModConfigSpec.BooleanValue NO_COLLISION_BLOCKS_CANNOT_SUPPORT = BUILDER
            .comment("通用支撑判断：无碰撞体方块默认不能提供底部支撑。开启后花草、火把、拉杆、按钮、红石线等无碰撞/近似无碰撞方块不会把上方结构判定为仍有支撑。可用 autosablephysics:force_supporting 白名单覆盖。")
            .define("noCollisionBlocksCannotSupport", true);

    public static final ModConfigSpec.BooleanValue REQUIRE_FULL_COLLISION_BLOCK_FOR_SUPPORT = BUILDER
            .comment("更严格的支撑判断：只有完整碰撞方块才默认提供支撑。默认关闭；开启后半砖、台阶、附魔台等非完整碰撞体会倾向于不承重。可用 autosablephysics:force_supporting 白名单覆盖。")
            .define("requireFullCollisionBlockForSupport", false);

    public static final ModConfigSpec.BooleanValue NO_COLLISION_BLOCKS_DO_NOT_CONNECT = BUILDER
            .comment("通用连接判断：无碰撞体方块默认不把自己与邻居连接为同一个物理体。开启后按钮、拉杆、花草、红石线等不会把结构粘连起来。可用 autosablephysics:force_connecting 白名单覆盖。")
            .define("noCollisionBlocksDoNotConnect", true);

    public static final ModConfigSpec.BooleanValue ENABLE_HORIZONTAL_CONNECTIVITY_LIMITS = BUILDER
            .comment("是否启用横向有限连接。开启后，有限连接标签内的方块不再横向无限延伸，但也不会像 0.2.3 那样完全断开。")
            .define("enableHorizontalConnectivityLimits", true);

    public static final ModConfigSpec.IntValue DEFAULT_HORIZONTAL_CONNECTIVITY_LIMIT = BUILDER
            .comment("横向有限连接的默认切比雪夫半径。适用于 autosablephysics:limited_horizontal_connecting 或旧版 no_horizontal_connecting 标签内、但没有更具体分类的方块。")
            .defineInRange("defaultHorizontalConnectivityLimit", 16, 0, 256);

    public static final ModConfigSpec.IntValue LEAF_HORIZONTAL_CONNECTIVITY_LIMIT = BUILDER
            .comment("树叶横向连接半径。默认 7，模拟树叶可围绕树干形成一定范围的树冠，但不会无限横向连成整片森林。")
            .defineInRange("leafHorizontalConnectivityLimit", 7, 0, 64);

    public static final ModConfigSpec.IntValue GRANULAR_HORIZONTAL_CONNECTIVITY_LIMIT = BUILDER
            .comment("沙子、沙砾等颗粒材料横向连接半径。默认 1，超出后手动放置的方块优先转为原版 FallingBlockEntity，而不是创建 Sable 物理体。")
            .defineInRange("granularHorizontalConnectivityLimit", 1, 0, 64);

    public static final ModConfigSpec.IntValue TERRAIN_HORIZONTAL_CONNECTIVITY_LIMIT = BUILDER
            .comment("泥土、草方块、圆石类等地形材料横向连接半径。默认 32，用于避免洞穴/地形边缘过早断裂，同时防止无限吞掉整片地形。")
            .defineInRange("terrainHorizontalConnectivityLimit", 32, 0, 512);

    public static final ModConfigSpec.BooleanValue CRUSH_NO_COLLISION_SUPPORTS = BUILDER
            .comment("是否允许下落/物理相关逻辑压坏无碰撞且不可支撑的方块。该总开关会影响原版 FallingBlockEntity 压坏火把等逻辑；Sable sub-level 主动压坏另有 experimental 开关控制。")
            .define("crushNoCollisionSupports", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SUB_LEVEL_CRUSH_NO_COLLISION_BLOCKS = BUILDER
            .comment("是否允许已物理化且已经静止的 Sable sub-level 扫描自身全局 AABB，压坏重叠的无碰撞不可支撑方块，并把该区域登记为禁止放置无碰撞方块区域。0.2.9 起只在静止后执行，避免运动中修改世界导致 Rapier native panic。")
            .define("enableSubLevelCrushNoCollisionBlocks", true);

    public static final ModConfigSpec.IntValue SUB_LEVEL_CRUSH_CHECK_INTERVAL_TICKS = BUILDER
            .comment("已物理化 Sable sub-level 静止压坏检测的间隔 tick。检测只用于判断是否已经静止；真正破坏和登记区域只会在一次静止周期内执行一次。")
            .defineInRange("subLevelCrushCheckIntervalTicks", 20, 1, 1200);

    public static final ModConfigSpec.IntValue STATIONARY_SUB_LEVEL_CRUSH_IDLE_TICKS = BUILDER
            .comment("Sable sub-level 需要连续静止多少 tick 后，才允许执行一次无碰撞方块压坏扫描。运动后会清除旧的禁止放置区并重新计时。")
            .defineInRange("stationarySubLevelCrushIdleTicks", 40, 1, 72000);

    public static final ModConfigSpec.IntValue SUB_LEVEL_CRUSH_PLAYER_RADIUS = BUILDER
            .comment("只扫描距离任意玩家该半径内的 Sable sub-level，用于避免远处物理体产生额外世界读写。按 sub-level 全局 AABB 中心到玩家距离估算。")
            .defineInRange("subLevelCrushPlayerRadius", 64, 1, 512);

    public static final ModConfigSpec.IntValue SUB_LEVEL_CRUSH_MAX_BLOCKS_PER_CHECK = BUILDER
            .comment("单个 Sable sub-level 每次压坏检测最多检查多少个普通世界方块。避免大型/旋转物理体的全局包围盒过大导致 TPS 抖动。")
            .defineInRange("subLevelCrushMaxBlocksPerCheck", 4096, 1, 262144);

    public static final ModConfigSpec.DoubleValue SUB_LEVEL_CRUSH_AABB_EXPANSION = BUILDER
            .comment("物理体压坏检测时对 Sable 全局 AABB 额外扩张多少格。小于 0.1 通常足够处理贴边接触。")
            .defineInRange("subLevelCrushAabbExpansion", 0.0625D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue MANUAL_PLACED_LIMITED_BLOCKS_USE_FALLING = BUILDER
            .comment("玩家手动放置横向有限连接方块时，如果超出稳定连接限制，是否转为原版 FallingBlockEntity 下落，而不是 Sable 物理化。")
            .define("manualPlacedLimitedBlocksUseFalling", true);

    public static final ModConfigSpec.BooleanValue VANILLA_FALLING_BLOCKS_CRUSH_NO_COLLISION = BUILDER
            .comment("是否让原版 FallingBlockEntity 在经过无碰撞且不可支撑方块时压坏它们。用于防止火把快速收集沙子/沙砾。")
            .define("vanillaFallingBlocksCrushNoCollision", true);

    public static final ModConfigSpec.BooleanValue SINGLE_BLOCK_COMPONENTS_USE_FALLING = BUILDER
            .comment("自动扫描检测到单个方块且该方块本应物理化时，是否优先使用原版 FallingBlockEntity 下落，而不是创建 Sable 单方块 sub-level。含方块实体的方块不会走该优化。")
            .define("singleBlockComponentsUseFalling", true);

    public static final ModConfigSpec.BooleanValue REQUIRE_NO_EXTERNAL_BOTTOM_SUPPORT = BUILDER
            .comment("为 true 时，只有整个连通结构底部没有外部实心支撑才会物理化。建议保持开启，否则地面上的小建筑/机器可能直接起飞。")
            .define("requireNoExternalBottomSupport", true);

    public static final ModConfigSpec.BooleanValue SKIP_IF_TOUCHES_SEARCH_BOUNDARY = BUILDER
            .comment("为 true 时，如果连通结构碰到 maxExpansionRadius 边界就跳过，避免只截取大型结构的一部分。")
            .define("skipIfTouchesSearchBoundary", true);

    public static final ModConfigSpec.BooleanValue ALLOW_BLOCK_ENTITIES = BUILDER
            .comment("是否允许自动移动含 BlockEntity 的方块。Sable 支持一部分方块实体，但大型整合包里建议谨慎测试。")
            .define("allowBlockEntities", true);

    public static final ModConfigSpec.BooleanValue TRIGGER_ON_PLACE = BUILDER
            .comment("放置方块后也扫描附近结构。一般用于桥梁、脚手架、临时支撑被改变后的补偿；关闭可减少误触发。")
            .define("triggerOnPlace", false);

    public static final ModConfigSpec.BooleanValue ENABLE_SMALL_OBJECT_FALLBACK = BUILDER
            .comment("是否启用小型孤立物体补偿扫描。开启后，破坏方块时会额外用极小预算检查本次影响范围内 1-2 个方块的小型悬空结构，避免小物体因候选入口不足而长期悬空。")
            .define("enableSmallObjectFallback", true);

    public static final ModConfigSpec.IntValue SMALL_OBJECT_MAX_BLOCKS = BUILDER
            .comment("小型孤立物体补偿扫描允许装配的最大方块数。默认 2，只覆盖单方块/双方块小物体；提高会增加误触发和扫描成本。")
            .defineInRange("smallObjectMaxBlocks", 2, 1, 64);

    public static final ModConfigSpec.IntValue SMALL_OBJECT_SCAN_RADIUS = BUILDER
            .comment("小型孤立物体补偿扫描的候选半径。默认 2，与破坏影响半径一致；这个半径只用于找起点，不会允许大型结构装配。")
            .defineInRange("smallObjectScanRadius", 2, 0, 16);

    public static final ModConfigSpec.IntValue MAX_SMALL_OBJECT_ORIGINS_PER_SCAN = BUILDER
            .comment("每次自动扫描最多尝试多少个小型孤立物体候选起点。每个候选最多搜索 smallObjectMaxBlocks+1 个方块，因此成本很低。")
            .defineInRange("maxSmallObjectOriginsPerScan", 48, 1, 1024);

    public static final ModConfigSpec.BooleanValue TRIGGER_SMALL_OBJECT_SCAN_ON_PLACE = BUILDER
            .comment("当 triggerOnPlace=false 时，是否仍对没有底部支撑的新放置方块执行小型孤立物体补偿扫描。该扫描只处理 smallObjectMaxBlocks 以内的小结构，不会触发完整放置扫描。")
            .define("triggerSmallObjectScanOnPlace", true);

    public static final ModConfigSpec.BooleanValue IGNORE_EVENTS_INSIDE_SABLE_SUB_LEVELS = BUILDER
            .comment("是否忽略 Sable sub-level / plot 内部的方块破坏和放置事件。建议保持开启，避免已经物理化的物体内部变动再次触发自动扫描，导致闪烁、重复装配或额外卡顿。")
            .define("ignoreEventsInsideSableSubLevels", true);


    public static final ModConfigSpec.BooleanValue USE_AFFECTED_BLOCK_STATE = BUILDER
            .comment("是否启用受影响方块状态系统。开启后，自动扫描只优先处理被玩家破坏/放置影响过的区域；未受影响的自然地形默认视为稳定支撑，不作为自动物理化候选。")
            .define("useAffectedBlockState", true);

    public static final ModConfigSpec.IntValue AFFECTED_RADIUS_ON_BREAK = BUILDER
            .comment("破坏方块时，标记受影响区域的切比雪夫半径。默认 2，即 5x5x5 立方体。")
            .defineInRange("affectedRadiusOnBreak", 2, 0, 16);

    public static final ModConfigSpec.IntValue AFFECTED_RADIUS_ON_PLACE = BUILDER
            .comment("放置方块时，标记受影响区域的切比雪夫半径。默认 1，即 3x3x3 立方体。")
            .defineInRange("affectedRadiusOnPlace", 1, 0, 16);

    public static final ModConfigSpec.IntValue LOG_LEAF_AFFECTED_RADIUS = BUILDER
            .comment("原木/去皮原木附近树叶的额外受影响半径。默认 7，即 15x15x15 立方体内的树叶会被标记为受影响。")
            .defineInRange("logLeafAffectedRadius", 7, 0, 32);

    public static final ModConfigSpec.IntValue MAX_LOG_LEAF_SOURCES_PER_CHANGE = BUILDER
            .comment("每次方块变化最多处理多少个原木作为树叶受影响源。用于防止原木墙/大型树一次触发过多 15x15x15 树叶扫描。")
            .defineInRange("maxLogLeafSourcesPerChange", 16, 0, 512);

    public static final ModConfigSpec.IntValue AFFECTED_BOUNDARY_VERTICAL_RANGE = BUILDER
            .comment("扫描受影响区域边界时，边界方块向上和向下额外纳入多少格作为候选。默认 15。")
            .defineInRange("affectedBoundaryVerticalRange", 15, 0, 128);

    public static final ModConfigSpec.IntValue MAX_AFFECTED_REGION_BLOCKS_PER_SCAN = BUILDER
            .comment("单次自动扫描最多分析多少个连通受影响位置。超过后会截断候选，避免超大受影响区域导致后台准备或主线程扫描过重。")
            .defineInRange("maxAffectedRegionBlocksPerScan", 8192, 1, 262144);

    public static final ModConfigSpec.IntValue MAX_AFFECTED_BOUNDARY_COLUMNS_PER_SCAN = BUILDER
            .comment("单次自动扫描最多处理多少个受影响区域边界列。边界列会向上/向下扩展 affectedBoundaryVerticalRange。")
            .defineInRange("maxAffectedBoundaryColumnsPerScan", 512, 1, 65536);

    public static final ModConfigSpec.IntValue MAX_PREPARED_CANDIDATES_PER_SCAN = BUILDER
            .comment("单次自动扫描最多准备多少个候选起点。候选会按距离本次变化中心递进排序；实际 Sable BFS 仍受 maxOriginsPerScan 限制。")
            .defineInRange("maxPreparedCandidatesPerScan", 2048, 1, 262144);

    public static final ModConfigSpec.BooleanValue ASYNC_PREPARE_AFFECTED_CANDIDATES = BUILDER
            .comment("是否异步准备受影响区域候选。异步线程只处理已保存的坐标集合，不读取/修改世界；真正读取方块和调用 Sable 仍在服务端主线程执行。")
            .define("asyncPrepareAffectedCandidates", true);



    public static final ModConfigSpec.BooleanValue ENABLE_EXPLOSION_EVENTS = BUILDER
            .comment("是否监听爆炸导致的方块破坏。覆盖苦力怕、TNT、末地水晶、床爆炸、火球等原版爆炸来源。")
            .define("enableExplosionEvents", true);

    public static final ModConfigSpec.IntValue MAX_EXPLOSION_BLOCKS_TO_MARK = BUILDER
            .comment("单次爆炸最多标记多少个受影响方块。爆炸列表超过该值时会优先选择靠近爆炸中心估算点的方块，避免大型爆炸一次写入过多 SavedData。")
            .defineInRange("maxExplosionBlocksToMark", 256, 1, 8192);

    public static final ModConfigSpec.BooleanValue ENABLE_PISTON_EVENTS = BUILDER
            .comment("是否监听活塞推动/拉回导致的方块移动。开启后会标记活塞头、被推动方块原位置、新位置以及被破坏方块。")
            .define("enablePistonEvents", true);

    public static final ModConfigSpec.IntValue MAX_PISTON_BLOCKS_TO_MARK = BUILDER
            .comment("单次活塞事件最多标记多少个相关方块。原版活塞通常最多推动 12 个方块，默认 32 留给特殊兼容情况。")
            .defineInRange("maxPistonBlocksToMark", 32, 1, 512);

    public static final ModConfigSpec.BooleanValue ENABLE_LIVING_DESTROY_BLOCK_EVENTS = BUILDER
            .comment("是否监听生物破坏方块事件。覆盖末影龙/凋灵破坏方块、僵尸破门等 NeoForge LivingDestroyBlockEvent。")
            .define("enableLivingDestroyBlockEvents", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ENDERMAN_CARRY_TRACKING = BUILDER
            .comment("是否用低成本实体 tick 追踪末影人拿起/放下方块。末影人搬方块未必总会走普通放置/破坏事件，因此这里通过携带方块状态变化做近似补偿。")
            .define("enableEndermanCarryTracking", true);

    public static final ModConfigSpec.BooleanValue ENABLE_FALLING_BLOCK_EVENTS = BUILDER
            .comment("是否监听下落方块实体生成。覆盖沙子、沙砾、混凝土粉末、铁砧等从方块变成 FallingBlockEntity 的情况。")
            .define("enableFallingBlockEvents", true);

    public static final ModConfigSpec.BooleanValue ENABLE_FLUID_PLACE_BLOCK_EVENTS = BUILDER
            .comment("是否监听流体放置方块事件。覆盖岩浆/水生成圆石、黑曜石、石头等情况。")
            .define("enableFluidPlaceBlockEvents", true);

    public static final ModConfigSpec.BooleanValue ENABLE_FARMLAND_TRAMPLE_EVENTS = BUILDER
            .comment("是否监听耕地被实体踩坏事件。该事件会把耕地变成泥土，属于实体导致的方块变化。")
            .define("enableFarmlandTrampleEvents", true);

    public static final ModConfigSpec.BooleanValue ENABLE_TOOL_MODIFICATION_EVENTS = BUILDER
            .comment("是否监听工具改变方块状态事件。覆盖斧头去皮、铲子造土径、锄头耕地等原版工具改方块行为。")
            .define("enableToolModificationEvents", true);

    public static final ModConfigSpec.BooleanValue ENABLE_NEIGHBOR_PHYSICS_EVENTS = BUILDER
            .comment("是否监听邻居物理更新事件作为兜底。该事件频率很高，红石/流体/沙子更新都会触发，默认关闭；只有发现某类方块变化无法被其它事件覆盖时再打开。")
            .define("enableNeighborPhysicsEvents", false);

    public static final ModConfigSpec.IntValue MAX_NEIGHBOR_EVENTS_PER_TICK = BUILDER
            .comment("邻居物理更新兜底每个维度每 tick 最多处理多少个中心。仅在 enableNeighborPhysicsEvents=true 时生效。")
            .defineInRange("maxNeighborEventsPerTick", 8, 1, 256);


    public static final ModConfigSpec.BooleanValue ENABLE_DELAYED_ASSEMBLY = BUILDER
            .comment("是否启用延迟物理化调度器。开启后，自动扫描只把候选组件加入待物理化队列，由调度器等待上一个 Sable sub-level 达到最低同步条件后再创建下一个，降低客户端/服务端 sub-level 同步失配风险。")
            .define("enableDelayedAssembly", true);

    public static final ModConfigSpec.IntValue MIN_TICKS_BETWEEN_ASSEMBLIES = BUILDER
            .comment("同一维度中两次自动创建 Sable sub-level 之间至少间隔多少 tick。用于避免短时间创建大量单方块 sub-level。")
            .defineInRange("minTicksBetweenAssemblies", 8, 0, 1200);

    public static final ModConfigSpec.IntValue MAX_ASSEMBLIES_PER_LEVEL_TICK = BUILDER
            .comment("延迟物理化调度器每个维度每 tick 最多真正创建多少个 Sable sub-level。建议保持 1。")
            .defineInRange("maxAssembliesPerLevelTick", 1, 1, 32);

    public static final ModConfigSpec.BooleanValue REQUIRE_SABLE_TRACKING_CONFIRMATION = BUILDER
            .comment("是否要求上一个由本模组创建的 Sable sub-level 被玩家 tracking 后，才允许创建下一个。若 Sable/客户端同步迟迟没有完成，后续物体会无限期悬空等待，并在聊天栏提醒。")
            .define("requireSableTrackingConfirmation", true);

    public static final ModConfigSpec.IntValue MIN_SABLE_SYNC_WAIT_TICKS = BUILDER
            .comment("即使已检测到 tracking，也至少等待多少 tick 再创建下一个 Sable sub-level。用于给 Sable 的创建包、plot 同步和首个运动包留出缓冲。")
            .defineInRange("minSableSyncWaitTicks", 10, 0, 1200);

    public static final ModConfigSpec.IntValue PENDING_ASSEMBLY_WARNING_TICKS = BUILDER
            .comment("待物理化队列最老任务等待超过多少 tick 后，在聊天栏提示玩家减少一次性创建的物理体数量。0 表示关闭按时间警告。")
            .defineInRange("pendingAssemblyWarningTicks", 200, 0, 72000);

    public static final ModConfigSpec.IntValue PENDING_ASSEMBLY_WARNING_SIZE = BUILDER
            .comment("待物理化队列长度达到多少时，在聊天栏提示玩家减少一次性创建的物理体数量。0 表示关闭按数量警告。")
            .defineInRange("pendingAssemblyWarningSize", 8, 0, 4096);

    public static final ModConfigSpec.IntValue PENDING_ASSEMBLY_WARNING_INTERVAL_TICKS = BUILDER
            .comment("聊天栏队列警告的最小间隔 tick。")
            .defineInRange("pendingAssemblyWarningIntervalTicks", 200, 20, 72000);

    public static final ModConfigSpec.BooleanValue CANCEL_PENDING_IF_CONNECTED = BUILDER
            .comment("待物理化组件在排队期间，如果被玩家重新连接到其他可移动方块，是否取消本次物理化。建议开启，这样临时悬空物可以被继续搭建或接回结构。")
            .define("cancelPendingIfConnected", true);

    public static final ModConfigSpec.BooleanValue CANCEL_PENDING_IF_SUPPORTED = BUILDER
            .comment("待物理化组件在排队期间，如果重新获得外部底部支撑，是否取消本次物理化。建议开启。")
            .define("cancelPendingIfSupported", true);

    public static final ModConfigSpec.IntValue NON_PLAYER_EVENT_STABILIZATION_DELAY_TICKS = BUILDER
            .comment("爆炸、活塞、生物破坏、下落方块、流体造块等非直接玩家破坏事件的额外稳定等待 tick。事件会先标记受影响区域，等原版事件基本结束后再扫描。")
            .defineInRange("nonPlayerEventStabilizationDelayTicks", 5, 0, 200);

    public static final ModConfigSpec.BooleanValue TRACK_CREATED_SUB_LEVEL_MOTION = BUILDER
            .comment("是否记录本模组创建的 Sable sub-level 的运动状态。用于延迟装配门控、静止压坏检测、网格嵌入快速还原和自动还原。 / Track Auto Sable Physics sub-level motion for delayed assembly gates, stationary crush checks, grid-aligned fast restore, and auto restore.")
            .define("trackCreatedSubLevelMotion", true);

    public static final ModConfigSpec.DoubleValue RESTORE_LINEAR_SPEED_THRESHOLD = BUILDER
            .comment("自动还原、网格快速还原和静止压坏检测使用的线速度阈值。低于该阈值才被视为静止。")
            .defineInRange("restoreLinearSpeedThreshold", 0.015D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue RESTORE_ANGULAR_SPEED_THRESHOLD = BUILDER
            .comment("自动还原、网格快速还原和静止压坏检测使用的角速度阈值。低于该阈值才被视为静止。")
            .defineInRange("restoreAngularSpeedThreshold", 0.015D, 0.0D, 10.0D);


    public static final ModConfigSpec.BooleanValue ENABLE_AUTO_RESTORE_SUB_LEVELS = BUILDER
            .comment("是否启用 Auto Sable Physics 创建的 Sable sub-level 自动还原。仅跟踪本模组创建的物理体；锤子面保存过的物理体不会自动还原。")
            .define("enableAutoRestoreSubLevels", true);

    public static final ModConfigSpec.IntValue RESTORE_POSITION_CHECK_INTERVAL_TICKS = BUILDER
            .comment("自动还原的位置检测间隔 tick。默认 600 tick，即约 30 秒检测一次物理体位置。")
            .defineInRange("restorePositionCheckIntervalTicks", 600, 20, 72000);

    public static final ModConfigSpec.IntValue RESTORE_IDLE_TICKS_REQUIRED = BUILDER
            .comment("物理体累计多久未移动后自动还原为原版方块。默认 6000 tick，即约 5 分钟。移动后会重新计时。")
            .defineInRange("restoreIdleTicksRequired", 6000, 20, 720000);

    public static final ModConfigSpec.DoubleValue RESTORE_POSITION_EPSILON = BUILDER
            .comment("两次位置检测之间，小于该距离视为位置不变。用于避免浮点微抖动让静止计时一直被清零。")
            .defineInRange("restorePositionEpsilon", 0.025D, 0.0D, 2.0D);

    public static final ModConfigSpec.IntValue MAX_BLOCKS_FOR_AUTO_RESTORE = BUILDER
            .comment("自动还原最多处理多少方块的物理体。大型长期物理体不应被自动还原；默认 512，与自动物理化默认上限一致。")
            .defineInRange("maxBlocksForAutoRestore", 512, 1, 256000);

    public static final ModConfigSpec.IntValue RESTORE_COLLISION_SEARCH_RADIUS = BUILDER
            .comment("物理体还原时，如果近似坐标被占用，在周围多少格内寻找可替代空位。默认 2。")
            .defineInRange("restoreCollisionSearchRadius", 2, 0, 16);

    public static final ModConfigSpec.BooleanValue ENABLE_GRID_ALIGNED_FAST_RESTORE = BUILDER
            .comment("是否启用网格嵌入快速还原。若本模组创建的 Sable sub-level 已经基本贴合原版方块网格，且一个检测周期内未移动，则可在 30 秒左右提前还原为普通方块。锤子保存的物理体仍不会自动还原。")
            .define("enableGridAlignedFastRestore", true);

    public static final ModConfigSpec.IntValue GRID_ALIGNED_RESTORE_IDLE_TICKS = BUILDER
            .comment("网格嵌入快速还原要求的未移动 tick。默认 600 tick，即约 30 秒。该值应不小于 restorePositionCheckIntervalTicks 才符合“两次检测位置不变”的语义。")
            .defineInRange("gridAlignedRestoreIdleTicks", 600, 20, 72000);

    public static final ModConfigSpec.DoubleValue GRID_ALIGNMENT_EPSILON = BUILDER
            .comment("判断 Sable sub-level 是否嵌入原版方块网格时允许的坐标偏差。会同时接受整数边界对齐与半格边界对齐，以适配 Sable AABB/方块中心的不同表示。")
            .defineInRange("gridAlignmentEpsilon", 0.0625D, 0.0D, 0.5D);

    public static final ModConfigSpec.DoubleValue GRID_ALIGNMENT_ANGULAR_EPSILON = BUILDER
            .comment("网格嵌入快速还原允许的旋转角偏差，单位弧度。默认约 2.86 度。明显旋转的物理体不会快速还原。")
            .defineInRange("gridAlignmentAngularEpsilon", 0.05D, 0.0D, 3.14159D);

    public static final ModConfigSpec.BooleanValue LOG_DEBUG = BUILDER
            .comment("输出调试日志。性能排查时再打开。")
            .define("logDebug", false);

    public static final ModConfigSpec.BooleanValue LOG_SLOW_SCANS = BUILDER
            .comment("输出耗时超过 slowScanLogMillis 的扫描日志。用于定位 TPS 卡顿来源。")
            .define("logSlowScans", true);

    public static final ModConfigSpec.IntValue SLOW_SCAN_LOG_MILLIS = BUILDER
            .comment("扫描耗时超过多少毫秒时输出警告日志。")
            .defineInRange("slowScanLogMillis", 50, 1, 10000);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
