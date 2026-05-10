# Auto Sable Physics 模组介绍与配置说明

> 当前文档版本：0.3.1。

## 1. 模组简介

Auto Sable Physics 是一个基于 Sable 的自动物理化模组。它会监听玩家破坏、放置、爆炸、活塞、生物破坏、流体造块、下落方块等方块变化，并自动判断哪些结构已经失去支撑。符合条件的结构会被延迟转换为 Sable sub-level 物理体。

核心目标：

- 自动让失去支撑的结构物理化。
- 避免整片自然地形被误物理化。
- 避免短时间创建大量小型 Sable sub-level 导致同步失配。
- 允许物理体在静止后还原为原版方块。
- 提供锤子工具保存或手动还原物理体。

## 2. 主要功能

### 自动物理化

当方块变化导致某个结构失去外部底部支撑时，模组会把该结构加入延迟物理化队列。队列会等待 Sable 同步条件满足后，逐个创建物理体。

### 受影响区域

模组不会扫描全世界。它只记录被玩家或原版事件影响过的区域。默认：

- 破坏方块：标记 5×5×5。
- 放置方块：标记 3×3×3。
- 树木：原木附近 7 格内的树叶会额外标记为受影响。

### 组件级扫描

自动路径会把允许区域内的真实方块按 6 面连接拆成组件，然后逐个判断支撑性。这样可以稳定处理高柱、L 形结构、小组件和树木。

### 延迟物理化队列

通过 `DelayedAssemblyManager` 控制 Sable sub-level 创建速度，避免一次生成太多物理体。

### 自动还原

本模组创建的物理体静止足够久后，可以近似还原为原版世界方块。箱子等 BlockEntity 会尝试保存和恢复 NBT 数据。

### 网格嵌入快速还原

如果物理体已经基本嵌入 Minecraft 原版方块网格，并且约 30 秒未移动，可以提前还原，不必等待默认 5 分钟。

### Sable 锤子

- 锤面模式：保存/钉住物理体，禁止自动还原。
- 齿面模式：手动把物理体还原为原版方块。
- 潜行右键或 `/autosablephysics hammer_toggle` 可切换模式。


#### 锤子合成配方

Sable 锤子可以通过两种左右镜像配方合成。

材料：

- `A`：铁锭
- `B`：铁粒
- `C`：木棍

配方一：

```text
A A B
  C
  C
```

配方二：

```text
B A A
  C
  C
```

合成结果为 `autosablephysics:sable_hammer`。锤子也会出现在创造模式的“工具与实用物品”标签页中。

### 单方块 FallingBlock 优化

普通单方块组件默认优先使用原版 `FallingBlockEntity`，减少 Sable 单方块 sub-level 数量。含 BlockEntity 的单方块不会走该优化。

### 无碰撞方块处理

- 花草、火把、按钮、拉杆等无碰撞方块默认不支撑物体。
- 原版 FallingBlockEntity 可压坏这些方块。
- 静止的 Sable 物理体会扫描一次自身占用区，破坏重叠的无碰撞方块，并禁止玩家在该区域继续放置这类方块。

## 3. 命令

| 命令 | 作用 |
|---|---|
| `/autosablephysics scan_here [radius]` | 以玩家当前位置手动扫描。 |
| `/autosablephysics scan_pos <pos> [radius]` | 以指定坐标手动扫描。 |
| `/autosablephysics remove_all_sable` | 请求 Sable 移除所有匹配物理体。 |
| `/autosablephysics hammer_toggle` | 切换主手 Sable 锤子模式。 |

## 4. 配置项总览

> SERVER 配置位于世界 `serverconfig`。单人开发环境一般在 `run/saves/<world>/serverconfig`。

### 基础开关

| 配置 | 默认 | 说明 |
|---|---:|---|
| `enabled` | true | 总开关。 |
| `delayTicks` | 1 | 方块变化后延迟扫描 tick。 |
| `triggerOnPlace` | false | 放置方块后是否完整扫描。 |
| `manualScanRadius` | 4 | 手动扫描默认半径。 |
| `logDebug` | false | 调试日志。 |
| `logSlowScans` | true | 慢扫描日志。 |
| `slowScanLogMillis` | 50 | 慢扫描阈值毫秒。 |

### 扫描预算

| 配置 | 默认 | 说明 |
|---|---:|---|
| `maxBlocksPerAssembly` | 512 | 单个物理体最大方块数。 |
| `autoMaxBlocksPerAssembly` | 512 | 自动扫描安全上限。 |
| `minBlocksPerAssembly` | 1 | 最小物理化方块数。 |
| `maxJobsPerLevelTick` | 1 | 每维度每 tick 扫描任务数。 |
| `autoMaxJobsPerLevelTick` | 1 | 自动扫描任务安全上限。 |
| `samePositionCooldownTicks` | 20 | 同位置入队冷却。 |

### 受影响状态

| 配置 | 默认 | 说明 |
|---|---:|---|
| `useAffectedBlockState` | true | 启用受影响状态系统。 |
| `affectedRadiusOnBreak` | 2 | 破坏影响半径，2 即 5×5×5。 |
| `affectedRadiusOnPlace` | 1 | 放置影响半径，1 即 3×3×3。 |
| `affectedBoundaryVerticalRange` | 15 | 边界上下延伸距离。 |
| `maxAffectedRegionBlocksPerScan` | 8192 | 单次分析 affected 坐标上限。 |
| `maxAffectedBoundaryColumnsPerScan` | 512 | 边界列上限。 |
| `maxPreparedCandidatesPerScan` | 2048 | 准备候选上限。 |
| `asyncPrepareAffectedCandidates` | true | 异步准备坐标计划。 |

### 树木处理

| 配置 | 默认 | 说明 |
|---|---:|---|
| `logLeafAffectedRadius` | 7 | 原木附近树叶受影响半径。 |
| `maxLogLeafSourcesPerChange` | 16 | 每次变化最多处理的原木源数量。 |
| `leafHorizontalConnectivityLimit` | 7 | 树叶横向连接半径。 |

### 支撑性与连接性

| 配置 | 默认 | 说明 |
|---|---:|---|
| `noCollisionBlocksCannotSupport` | true | 无碰撞方块不支撑。 |
| `requireFullCollisionBlockForSupport` | false | 只允许完整碰撞方块支撑。 |
| `noCollisionBlocksDoNotConnect` | true | 无碰撞方块不连接组件。 |
| `enableHorizontalConnectivityLimits` | true | 启用横向有限连接。 |
| `defaultHorizontalConnectivityLimit` | 16 | 默认有限连接半径。 |
| `granularHorizontalConnectivityLimit` | 1 | 沙子/沙砾类横向连接半径。 |
| `terrainHorizontalConnectivityLimit` | 32 | 泥土/草方块/圆石类横向连接半径。 |
| `requireNoExternalBottomSupport` | true | 要求组件无外部底部支撑。 |
| `requireFaceContactForAutoAssembly` | true | 自动扫描只允许面接触。 |
| `allowBlockEntities` | true | 是否允许含方块实体的方块物理化。 |

### 原版下落与无碰撞方块

| 配置 | 默认 | 说明 |
|---|---:|---|
| `singleBlockComponentsUseFalling` | true | 单方块优先走 FallingBlockEntity。 |
| `manualPlacedLimitedBlocksUseFalling` | true | 手动放置超限有限连接方块时走原版下落。 |
| `crushNoCollisionSupports` | true | 允许相关逻辑压坏无碰撞不可支撑方块。 |
| `vanillaFallingBlocksCrushNoCollision` | true | 原版 FallingBlockEntity 压坏无碰撞方块。 |
| `enableSubLevelCrushNoCollisionBlocks` | true | 静止 sub-level 扫描并压坏重叠无碰撞方块。 |
| `stationarySubLevelCrushIdleTicks` | 40 | 静止多久后执行压坏扫描。 |
| `subLevelCrushPlayerRadius` | 64 | 只处理玩家附近物理体。 |

### 原版事件覆盖

| 配置 | 默认 | 说明 |
|---|---:|---|
| `enableExplosionEvents` | true | 爆炸事件。 |
| `maxExplosionBlocksToMark` | 256 | 爆炸标记方块上限。 |
| `enablePistonEvents` | true | 活塞事件。 |
| `maxPistonBlocksToMark` | 32 | 活塞标记方块上限。 |
| `enableLivingDestroyBlockEvents` | true | 生物破坏方块。 |
| `enableEndermanCarryTracking` | true | 末影人搬方块近似追踪。 |
| `enableFallingBlockEvents` | true | 下落方块实体生成。 |
| `enableFluidPlaceBlockEvents` | true | 流体放置方块。 |
| `enableFarmlandTrampleEvents` | true | 耕地踩踏。 |
| `enableToolModificationEvents` | true | 工具改方块。 |
| `enableNeighborPhysicsEvents` | false | 邻居更新兜底，默认关闭。 |
| `maxNeighborEventsPerTick` | 8 | 邻居更新预算。 |
| `nonPlayerEventStabilizationDelayTicks` | 5 | 非玩家事件稳定等待。 |

### 延迟物理化

| 配置 | 默认 | 说明 |
|---|---:|---|
| `enableDelayedAssembly` | true | 启用延迟物理化。 |
| `minTicksBetweenAssemblies` | 8 | 两次创建间隔。 |
| `maxAssembliesPerLevelTick` | 1 | 每维度每 tick 最多创建。 |
| `requireSableTrackingConfirmation` | true | 要求上一个物理体被 tracking 后再创建下一个。 |
| `minSableSyncWaitTicks` | 10 | 最小 Sable 同步等待。 |
| `cancelPendingIfConnected` | true | 排队期间重新连接则取消。 |
| `cancelPendingIfSupported` | true | 排队期间重新获得支撑则取消。 |
| `pendingAssemblyWarningTicks` | 200 | 等待过久警告。 |
| `pendingAssemblyWarningSize` | 8 | 队列过长警告。 |
| `pendingAssemblyWarningIntervalTicks` | 200 | 警告间隔。 |

### 自动还原

| 配置 | 默认 | 说明 |
|---|---:|---|
| `enableAutoRestoreSubLevels` | true | 启用自动还原。 |
| `restorePositionCheckIntervalTicks` | 600 | 位置检测间隔。 |
| `restoreIdleTicksRequired` | 6000 | 默认静止还原时间。 |
| `restorePositionEpsilon` | 0.025 | 位置不变容差。 |
| `maxBlocksForAutoRestore` | 512 | 自动还原方块上限。 |
| `restoreCollisionSearchRadius` | 2 | 还原碰撞避让半径。 |
| `enableGridAlignedFastRestore` | true | 网格嵌入快速还原。 |
| `gridAlignedRestoreIdleTicks` | 600 | 快速还原静止时间。 |
| `gridAlignmentEpsilon` | 0.0625 | 网格对齐容差。 |
| `gridAlignmentAngularEpsilon` | 0.05 | 旋转容差。 |

## 5. 标签配置

| 标签 | 作用 |
|---|---|
| `autosablephysics:immobile` | 永远不物理化。 |
| `autosablephysics:ignored` | 忽略，不移动且不提供支撑。 |
| `autosablephysics:force_supporting` | 强制可支撑。 |
| `autosablephysics:non_supporting` | 强制不可支撑。 |
| `autosablephysics:force_connecting` | 强制可连接。 |
| `autosablephysics:non_connecting` | 强制不可连接。 |
| `autosablephysics:limited_horizontal_connecting` | 横向有限连接。 |
| `autosablephysics:leaf_horizontal_limited` | 树叶类有限连接。 |
| `autosablephysics:granular_horizontal_limited` | 沙/砾/粉末类有限连接。 |
| `autosablephysics:terrain_horizontal_limited` | 泥土/草方块/圆石/石头类有限连接。 |
| `autosablephysics:no_horizontal_connecting` | 旧兼容入口，语义已改为横向有限连接。 |

## 6. 推荐调参

### 更稳定

```toml
minTicksBetweenAssemblies = 20
minSableSyncWaitTicks = 20
requireSableTrackingConfirmation = true
maxAssembliesPerLevelTick = 1
```

### 更快

```toml
minTicksBetweenAssemblies = 4
minSableSyncWaitTicks = 6
requireSableTrackingConfirmation = false
```

### 大型树木/模组树

```toml
maxLogLeafSourcesPerChange = 64
leafHorizontalConnectivityLimit = 10
```


## 7. 完整配置索引

以下表格按源码 `ASPServerConfig.java` 自动整理，覆盖当前 0.3.0 的全部 SERVER 配置项。

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enabled` | `true` | 总开关。关闭后不会自动把方块组装成 Sable sub-level。 |
| `delayTicks` | `1` | 方块变化后延迟多少 tick 再扫描。建议至少 1，让原版/其他模组先完成方块更新。 |
| `triggerRadius` | `2` | 自动触发时，从变化中心向外寻找候选起点的半径。0.1.1 默认改为 2，避免一次破坏扫描过大的立方体。 |
| `manualScanRadius` | `4` | /autosablephysics scan_here 不带参数时使用的半径。手动命令仍会同步扫描，半径不要开太大。 |
| `maxExpansionRadius` | `12` | 单次连通搜索允许从候选起点向外扩展的最大切比雪夫半径。越大越容易识别大型物体，但越吃性能。 |
| `maxBlocksPerAssembly` | `512` | 单个物理化物体最多允许包含多少方块。0.1.1 默认改为 512，避免误扫地形时主线程长时间卡死。 |
| `minBlocksPerAssembly` | `1` | 低于该方块数的连通结构不自动物理化。0.1.5 默认改为 1，使被玩家挖成悬空的单个方块也能物理化。 |
| `autoAffectedMinBlocksPerAssembly` | `1` | 受影响状态系统启用时，自动扫描使用的最小结构方块数上限。用于覆盖旧 serverconfig 中 minBlocksPerAssembly=2 导致单个悬空方块无法物理化的问题。 |
| `maxOriginsPerScan` | `12` | 每个扫描任务最多尝试多少个候选起点。0.1.1 默认改为 12，避免一次破坏反复 BFS 扫描同一片地形。 |
| `maxJobsPerLevelTick` | `1` | 每个维度每 tick 最多处理多少个扫描任务。0.1.1 默认改为 1，让多个破坏事件分帧执行。 |
| `autoMaxJobsPerLevelTick` | `1` | 自动队列每个维度每 tick 的安全任务上限。用于覆盖旧配置文件里过大的 maxJobsPerLevelTick。 |
| `samePositionCooldownTicks` | `20` | 同一个方块位置重复入队的冷却时间。连锁方块更新较多时可减少重复扫描。 |
| `autoMaxTriggerRadius` | `2` | 自动触发扫描的安全半径上限。用于覆盖旧配置文件里过大的 triggerRadius；手动命令不受影响。 |
| `autoMaxExpansionRadius` | `12` | 自动触发扫描的连通扩展安全上限。用于覆盖旧配置文件里过大的 maxExpansionRadius；手动命令不受影响。 |
| `autoMaxBlocksPerAssembly` | `512` | 自动触发扫描的单结构方块数安全上限。用于覆盖旧配置文件里过大的 maxBlocksPerAssembly；手动命令不受影响。 |
| `autoMaxOriginsPerScan` | `12` | 自动触发扫描的候选起点安全上限。用于覆盖旧配置文件里过大的 maxOriginsPerScan；手动命令不受影响。 |
| `requireStartNearAir` | `true` | 候选起点必须至少贴着一个空气方块。开启后可以大量减少地形内部扫描。 |
| `requireStartWithoutBottomSupport` | `true` | 候选起点底部如果仍有外部实心支撑，则跳过。开启后可以避免破坏地面方块时扫描整片地形。 |
| `tooLargeRejectRadius` | `5` | 某个候选起点被判定为过大结构或触边后，跳过其附近多少格内的其他候选，避免重复扫描同一片山体/地面。 |
| `relaxStartSupportForDirectBreakNeighbors` | `true` | 破坏方块后，对被破坏方块的直接相邻方块放宽“起点底部不能有支撑”的预过滤。开启后可以识别先断下方支撑、再断上方连接导致的悬空结构；最终仍会执行整个结构的外部底部支撑检查。 |
| `maxRelaxedBreakNeighborOrigins` | `6` | 每次破坏方块后，最多允许多少个直接邻位使用放宽起点过滤。降低可减少误扫地形，提高可补偿侧向/上方连接断裂。建议范围 2-6。 |
| `requireFaceContactForAutoAssembly` | `true` | 自动扫描时是否只允许通过面接触连接方块。开启后会拒绝 Sable 默认允许的边接触斜向连接，避免柱子/树木在底部被破坏后仍通过旁边地面边缘斜连而被误判为有支撑。手动命令不受影响。 |
| `noCollisionBlocksCannotSupport` | `true` | 通用支撑判断：无碰撞体方块默认不能提供底部支撑。开启后花草、火把、拉杆、按钮、红石线等无碰撞/近似无碰撞方块不会把上方结构判定为仍有支撑。可用 autosablephysics:force_supporting 白名单覆盖。 |
| `requireFullCollisionBlockForSupport` | `false` | 更严格的支撑判断：只有完整碰撞方块才默认提供支撑。默认关闭；开启后半砖、台阶、附魔台等非完整碰撞体会倾向于不承重。可用 autosablephysics:force_supporting 白名单覆盖。 |
| `noCollisionBlocksDoNotConnect` | `true` | 通用连接判断：无碰撞体方块默认不把自己与邻居连接为同一个物理体。开启后按钮、拉杆、花草、红石线等不会把结构粘连起来。可用 autosablephysics:force_connecting 白名单覆盖。 |
| `enableHorizontalConnectivityLimits` | `true` | 是否启用横向有限连接。开启后，有限连接标签内的方块不再横向无限延伸，但也不会像 0.2.3 那样完全断开。 |
| `defaultHorizontalConnectivityLimit` | `16` | 横向有限连接的默认切比雪夫半径。适用于 autosablephysics:limited_horizontal_connecting 或旧版 no_horizontal_connecting 标签内、但没有更具体分类的方块。 |
| `leafHorizontalConnectivityLimit` | `7` | 树叶横向连接半径。默认 7，模拟树叶可围绕树干形成一定范围的树冠，但不会无限横向连成整片森林。 |
| `granularHorizontalConnectivityLimit` | `1` | 沙子、沙砾等颗粒材料横向连接半径。默认 1，超出后手动放置的方块优先转为原版 FallingBlockEntity，而不是创建 Sable 物理体。 |
| `terrainHorizontalConnectivityLimit` | `32` | 泥土、草方块、圆石类等地形材料横向连接半径。默认 32，用于避免洞穴/地形边缘过早断裂，同时防止无限吞掉整片地形。 |
| `crushNoCollisionSupports` | `true` | 是否允许下落/物理相关逻辑压坏无碰撞且不可支撑的方块。该总开关会影响原版 FallingBlockEntity 压坏火把等逻辑；Sable sub-level 主动压坏另有 experimental 开关控制。 |
| `enableSubLevelCrushNoCollisionBlocks` | `true` | 是否允许已物理化且已经静止的 Sable sub-level 扫描自身全局 AABB，压坏重叠的无碰撞不可支撑方块，并把该区域登记为禁止放置无碰撞方块区域。0.2.9 起只在静止后执行，避免运动中修改世界导致 Rapier native panic。 |
| `subLevelCrushCheckIntervalTicks` | `20` | 已物理化 Sable sub-level 静止压坏检测的间隔 tick。检测只用于判断是否已经静止；真正破坏和登记区域只会在一次静止周期内执行一次。 |
| `stationarySubLevelCrushIdleTicks` | `40` | Sable sub-level 需要连续静止多少 tick 后，才允许执行一次无碰撞方块压坏扫描。运动后会清除旧的禁止放置区并重新计时。 |
| `subLevelCrushPlayerRadius` | `64` | 只扫描距离任意玩家该半径内的 Sable sub-level，用于避免远处物理体产生额外世界读写。按 sub-level 全局 AABB 中心到玩家距离估算。 |
| `subLevelCrushMaxBlocksPerCheck` | `4096` | 单个 Sable sub-level 每次压坏检测最多检查多少个普通世界方块。避免大型/旋转物理体的全局包围盒过大导致 TPS 抖动。 |
| `subLevelCrushAabbExpansion` | `0.0625D` | 物理体压坏检测时对 Sable 全局 AABB 额外扩张多少格。小于 0.1 通常足够处理贴边接触。 |
| `manualPlacedLimitedBlocksUseFalling` | `true` | 玩家手动放置横向有限连接方块时，如果超出稳定连接限制，是否转为原版 FallingBlockEntity 下落，而不是 Sable 物理化。 |
| `vanillaFallingBlocksCrushNoCollision` | `true` | 是否让原版 FallingBlockEntity 在经过无碰撞且不可支撑方块时压坏它们。用于防止火把快速收集沙子/沙砾。 |
| `singleBlockComponentsUseFalling` | `true` | 自动扫描检测到单个方块且该方块本应物理化时，是否优先使用原版 FallingBlockEntity 下落，而不是创建 Sable 单方块 sub-level。含方块实体的方块不会走该优化。 |
| `requireNoExternalBottomSupport` | `true` | 为 true 时，只有整个连通结构底部没有外部实心支撑才会物理化。建议保持开启，否则地面上的小建筑/机器可能直接起飞。 |
| `skipIfTouchesSearchBoundary` | `true` | 为 true 时，如果连通结构碰到 maxExpansionRadius 边界就跳过，避免只截取大型结构的一部分。 |
| `allowBlockEntities` | `true` | 是否允许自动移动含 BlockEntity 的方块。Sable 支持一部分方块实体，但大型整合包里建议谨慎测试。 |
| `triggerOnPlace` | `false` | 放置方块后也扫描附近结构。一般用于桥梁、脚手架、临时支撑被改变后的补偿；关闭可减少误触发。 |
| `enableSmallObjectFallback` | `true` | 是否启用小型孤立物体补偿扫描。开启后，破坏方块时会额外用极小预算检查本次影响范围内 1-2 个方块的小型悬空结构，避免小物体因候选入口不足而长期悬空。 |
| `smallObjectMaxBlocks` | `2` | 小型孤立物体补偿扫描允许装配的最大方块数。默认 2，只覆盖单方块/双方块小物体；提高会增加误触发和扫描成本。 |
| `smallObjectScanRadius` | `2` | 小型孤立物体补偿扫描的候选半径。默认 2，与破坏影响半径一致；这个半径只用于找起点，不会允许大型结构装配。 |
| `maxSmallObjectOriginsPerScan` | `48` | 每次自动扫描最多尝试多少个小型孤立物体候选起点。每个候选最多搜索 smallObjectMaxBlocks+1 个方块，因此成本很低。 |
| `triggerSmallObjectScanOnPlace` | `true` | 当 triggerOnPlace=false 时，是否仍对没有底部支撑的新放置方块执行小型孤立物体补偿扫描。该扫描只处理 smallObjectMaxBlocks 以内的小结构，不会触发完整放置扫描。 |
| `ignoreEventsInsideSableSubLevels` | `true` | 是否忽略 Sable sub-level / plot 内部的方块破坏和放置事件。建议保持开启，避免已经物理化的物体内部变动再次触发自动扫描，导致闪烁、重复装配或额外卡顿。 |
| `useAffectedBlockState` | `true` | 是否启用受影响方块状态系统。开启后，自动扫描只优先处理被玩家破坏/放置影响过的区域；未受影响的自然地形默认视为稳定支撑，不作为自动物理化候选。 |
| `affectedRadiusOnBreak` | `2` | 破坏方块时，标记受影响区域的切比雪夫半径。默认 2，即 5x5x5 立方体。 |
| `affectedRadiusOnPlace` | `1` | 放置方块时，标记受影响区域的切比雪夫半径。默认 1，即 3x3x3 立方体。 |
| `logLeafAffectedRadius` | `7` | 原木/去皮原木附近树叶的额外受影响半径。默认 7，即 15x15x15 立方体内的树叶会被标记为受影响。 |
| `maxLogLeafSourcesPerChange` | `16` | 每次方块变化最多处理多少个原木作为树叶受影响源。用于防止原木墙/大型树一次触发过多 15x15x15 树叶扫描。 |
| `affectedBoundaryVerticalRange` | `15` | 扫描受影响区域边界时，边界方块向上和向下额外纳入多少格作为候选。默认 15。 |
| `maxAffectedRegionBlocksPerScan` | `8192` | 单次自动扫描最多分析多少个连通受影响位置。超过后会截断候选，避免超大受影响区域导致后台准备或主线程扫描过重。 |
| `maxAffectedBoundaryColumnsPerScan` | `512` | 单次自动扫描最多处理多少个受影响区域边界列。边界列会向上/向下扩展 affectedBoundaryVerticalRange。 |
| `maxPreparedCandidatesPerScan` | `2048` | 单次自动扫描最多准备多少个候选起点。候选会按距离本次变化中心递进排序；实际 Sable BFS 仍受 maxOriginsPerScan 限制。 |
| `asyncPrepareAffectedCandidates` | `true` | 是否异步准备受影响区域候选。异步线程只处理已保存的坐标集合，不读取/修改世界；真正读取方块和调用 Sable 仍在服务端主线程执行。 |
| `enableExplosionEvents` | `true` | 是否监听爆炸导致的方块破坏。覆盖苦力怕、TNT、末地水晶、床爆炸、火球等原版爆炸来源。 |
| `maxExplosionBlocksToMark` | `256` | 单次爆炸最多标记多少个受影响方块。爆炸列表超过该值时会优先选择靠近爆炸中心估算点的方块，避免大型爆炸一次写入过多 SavedData。 |
| `enablePistonEvents` | `true` | 是否监听活塞推动/拉回导致的方块移动。开启后会标记活塞头、被推动方块原位置、新位置以及被破坏方块。 |
| `maxPistonBlocksToMark` | `32` | 单次活塞事件最多标记多少个相关方块。原版活塞通常最多推动 12 个方块，默认 32 留给特殊兼容情况。 |
| `enableLivingDestroyBlockEvents` | `true` | 是否监听生物破坏方块事件。覆盖末影龙/凋灵破坏方块、僵尸破门等 NeoForge LivingDestroyBlockEvent。 |
| `enableEndermanCarryTracking` | `true` | 是否用低成本实体 tick 追踪末影人拿起/放下方块。末影人搬方块未必总会走普通放置/破坏事件，因此这里通过携带方块状态变化做近似补偿。 |
| `enableFallingBlockEvents` | `true` | 是否监听下落方块实体生成。覆盖沙子、沙砾、混凝土粉末、铁砧等从方块变成 FallingBlockEntity 的情况。 |
| `enableFluidPlaceBlockEvents` | `true` | 是否监听流体放置方块事件。覆盖岩浆/水生成圆石、黑曜石、石头等情况。 |
| `enableFarmlandTrampleEvents` | `true` | 是否监听耕地被实体踩坏事件。该事件会把耕地变成泥土，属于实体导致的方块变化。 |
| `enableToolModificationEvents` | `true` | 是否监听工具改变方块状态事件。覆盖斧头去皮、铲子造土径、锄头耕地等原版工具改方块行为。 |
| `enableNeighborPhysicsEvents` | `false` | 是否监听邻居物理更新事件作为兜底。该事件频率很高，红石/流体/沙子更新都会触发，默认关闭；只有发现某类方块变化无法被其它事件覆盖时再打开。 |
| `maxNeighborEventsPerTick` | `8` | 邻居物理更新兜底每个维度每 tick 最多处理多少个中心。仅在 enableNeighborPhysicsEvents=true 时生效。 |
| `enableDelayedAssembly` | `true` | 是否启用延迟物理化调度器。开启后，自动扫描只把候选组件加入待物理化队列，由调度器等待上一个 Sable sub-level 达到最低同步条件后再创建下一个，降低客户端/服务端 sub-level 同步失配风险。 |
| `minTicksBetweenAssemblies` | `8` | 同一维度中两次自动创建 Sable sub-level 之间至少间隔多少 tick。用于避免短时间创建大量单方块 sub-level。 |
| `maxAssembliesPerLevelTick` | `1` | 延迟物理化调度器每个维度每 tick 最多真正创建多少个 Sable sub-level。建议保持 1。 |
| `requireSableTrackingConfirmation` | `true` | 是否要求上一个由本模组创建的 Sable sub-level 被玩家 tracking 后，才允许创建下一个。若 Sable/客户端同步迟迟没有完成，后续物体会无限期悬空等待，并在聊天栏提醒。 |
| `minSableSyncWaitTicks` | `10` | 即使已检测到 tracking，也至少等待多少 tick 再创建下一个 Sable sub-level。用于给 Sable 的创建包、plot 同步和首个运动包留出缓冲。 |
| `pendingAssemblyWarningTicks` | `200` | 待物理化队列最老任务等待超过多少 tick 后，在聊天栏提示玩家减少一次性创建的物理体数量。0 表示关闭按时间警告。 |
| `pendingAssemblyWarningSize` | `8` | 待物理化队列长度达到多少时，在聊天栏提示玩家减少一次性创建的物理体数量。0 表示关闭按数量警告。 |
| `pendingAssemblyWarningIntervalTicks` | `200` | 聊天栏队列警告的最小间隔 tick。 |
| `cancelPendingIfConnected` | `true` | 待物理化组件在排队期间，如果被玩家重新连接到其他可移动方块，是否取消本次物理化。建议开启，这样临时悬空物可以被继续搭建或接回结构。 |
| `cancelPendingIfSupported` | `true` | 待物理化组件在排队期间，如果重新获得外部底部支撑，是否取消本次物理化。建议开启。 |
| `nonPlayerEventStabilizationDelayTicks` | `5` | 爆炸、活塞、生物破坏、下落方块、流体造块等非直接玩家破坏事件的额外稳定等待 tick。事件会先标记受影响区域，等原版事件基本结束后再扫描。 |
| `trackCreatedSubLevelMotion` | `true` | 是否记录本模组创建的 Sable sub-level 的运动状态。用于延迟装配门控、静止压坏检测、网格嵌入快速还原和自动还原。 / Track Auto Sable Physics sub-level motion for delayed assembly gates, stationary crush checks, grid-aligned fast restore, and auto restore. |
| `restoreLinearSpeedThreshold` | `0.015D` | 自动还原、网格快速还原和静止压坏检测使用的线速度阈值。低于该阈值才被视为静止。 |
| `restoreAngularSpeedThreshold` | `0.015D` | 自动还原、网格快速还原和静止压坏检测使用的角速度阈值。低于该阈值才被视为静止。 |
| `enableAutoRestoreSubLevels` | `true` | 是否启用 Auto Sable Physics 创建的 Sable sub-level 自动还原。仅跟踪本模组创建的物理体；锤子面保存过的物理体不会自动还原。 |
| `restorePositionCheckIntervalTicks` | `600` | 自动还原的位置检测间隔 tick。默认 600 tick，即约 30 秒检测一次物理体位置。 |
| `restoreIdleTicksRequired` | `6000` | 物理体累计多久未移动后自动还原为原版方块。默认 6000 tick，即约 5 分钟。移动后会重新计时。 |
| `restorePositionEpsilon` | `0.025D` | 两次位置检测之间，小于该距离视为位置不变。用于避免浮点微抖动让静止计时一直被清零。 |
| `maxBlocksForAutoRestore` | `512` | 自动还原最多处理多少方块的物理体。大型长期物理体不应被自动还原；默认 512，与自动物理化默认上限一致。 |
| `restoreCollisionSearchRadius` | `2` | 物理体还原时，如果近似坐标被占用，在周围多少格内寻找可替代空位。默认 2。 |
| `enableGridAlignedFastRestore` | `true` | 是否启用网格嵌入快速还原。若本模组创建的 Sable sub-level 已经基本贴合原版方块网格，且一个检测周期内未移动，则可在 30 秒左右提前还原为普通方块。锤子保存的物理体仍不会自动还原。 |
| `gridAlignedRestoreIdleTicks` | `600` | 网格嵌入快速还原要求的未移动 tick。默认 600 tick，即约 30 秒。该值应不小于 restorePositionCheckIntervalTicks 才符合“两次检测位置不变”的语义。 |
| `gridAlignmentEpsilon` | `0.0625D` | 判断 Sable sub-level 是否嵌入原版方块网格时允许的坐标偏差。会同时接受整数边界对齐与半格边界对齐，以适配 Sable AABB/方块中心的不同表示。 |
| `gridAlignmentAngularEpsilon` | `0.05D` | 网格嵌入快速还原允许的旋转角偏差，单位弧度。默认约 2.86 度。明显旋转的物理体不会快速还原。 |
| `logDebug` | `false` | 输出调试日志。性能排查时再打开。 |
| `logSlowScans` | `true` | 输出耗时超过 slowScanLogMillis 的扫描日志。用于定位 TPS 卡顿来源。 |
| `slowScanLogMillis` | `50` | 扫描耗时超过多少毫秒时输出警告日志。 |


---

## 0.3.1 更新说明

- 新增 Sable 锤子合成配方：支持 `AAB /  C  /  C ` 与 `BAA /  C  /  C ` 两种左右镜像配方。
- 合成材料为铁锭、铁粒和木棍。
- 确认 Sable 锤子与齿面模式物品加入创造模式“工具与实用物品”标签页。
- 更新模组介绍文档，补充锤子合成方式。
