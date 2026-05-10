# Auto Sable Physics 开发者兼容说明

## 1. 兼容目标

Auto Sable Physics 通过事件监听和数据包标签判断方块是否可以被自动物理化、是否可以支撑其它方块、是否可以与邻居连接。其它模组通常不需要直接调用本模组 API，只需要：

1. 正确提供碰撞形状。
2. 正确加入原版或本模组的数据包标签。
3. 在自定义方块发生非常规变化时触发标准 NeoForge 方块事件，或让玩家/服务器主动调用本模组命令调试。

## 2. 依赖关系

运行环境：

```text
Minecraft 1.21.1
NeoForge 21.1.x
Sable 1.2.2
Sable Companion 1.6.0
```

本模组直接调用 Sable 的 assembly API，并使用 Sable Companion 判断 sub-level/plot。Sable 版本建议锁在 `1.2.2 <= version < 1.3.0`。

## 3. 方块标签兼容

### 3.1 物理化排除

```text
autosablephysics:immobile
autosablephysics:ignored
```

- `immobile`：方块不能被自动物理化，但仍可能作为支撑参与判断。
- `ignored`：方块不能被自动物理化，也不会作为有效支撑。

建议加入：

- 管理类方块。
- 传送门、结构方块、边界方块。
- 特殊多方块控制器。
- 不应被 Sable 移动的装饰或逻辑方块。

### 3.2 支撑性

```text
autosablephysics:force_supporting
autosablephysics:non_supporting
```

- `force_supporting`：强制作为可靠底部支撑。
- `non_supporting`：强制不作为可靠底部支撑。

如果你的方块有碰撞但逻辑上不该承重，例如薄装饰、脆弱植物、悬挂件，应加入 `non_supporting`。如果你的模组方块碰撞形状特殊但确实能承重，应加入 `force_supporting`。

### 3.3 连接性

```text
autosablephysics:force_connecting
autosablephysics:non_connecting
autosablephysics:limited_horizontal_connecting
autosablephysics:leaf_horizontal_limited
autosablephysics:granular_horizontal_limited
autosablephysics:terrain_horizontal_limited
autosablephysics:no_horizontal_connecting
```

- `force_connecting`：强制与邻接方块连接为同一组件。
- `non_connecting`：强制不传播组件连接。
- `limited_horizontal_connecting`：横向有限连接。
- `leaf_horizontal_limited`：类似树叶，连接以附近原木为锚点。
- `granular_horizontal_limited`：类似沙子/沙砾，连接半径很小。
- `terrain_horizontal_limited`：类似泥土/石头地形，连接半径较大。
- `no_horizontal_connecting`：旧版本兼容入口，现在等价于有限连接材料。

## 4. BlockEntity 兼容

配置 `allowBlockEntities=true` 时，Auto Sable Physics 允许含 BlockEntity 的方块进入 Sable 物理体。还原时会尝试保存和恢复 BlockEntity NBT。

开发建议：

- BlockEntity 的 `saveAdditional` / `loadAdditional` 必须稳定。
- 不要依赖只在固定世界坐标有效的缓存。
- 如果方块被移动后不能安全恢复，应加入 `immobile` 或 `non_connecting`。

## 5. 事件兼容

Auto Sable Physics 已监听：

- `BlockEvent.BreakEvent`
- `BlockEvent.EntityPlaceEvent`
- `ExplosionEvent.Detonate`
- `PistonEvent.Post`
- `LivingDestroyBlockEvent`
- `EntityJoinLevelEvent` for `FallingBlockEntity`
- `BlockEvent.FluidPlaceBlockEvent`
- `BlockEvent.FarmlandTrampleEvent`
- `BlockEvent.BlockToolModificationEvent`
- `BlockEvent.NeighborNotifyEvent` as disabled fallback

如果你的模组以自定义方式修改世界方块，建议尽量触发标准 NeoForge 事件，或者在修改方块后让周围方块发生普通邻居更新。不要要求 Auto Sable Physics 扫全世界。

## 6. Sable sub-level 内部事件

本模组默认忽略 Sable sub-level / plot 内部的破坏和放置事件，避免重复物理化已物理化结构。

其它模组如果直接操作 Sable sub-level 内方块，应避免依赖 Auto Sable Physics 再次扫描这些变更。

## 7. 长期物理体

Auto Sable Physics 只跟踪自己创建的 Sable sub-level。Create Aeronautics、Valkyrien Skies 或其它长期物理体不应被本模组自动还原。

如果你的模组创建长期物理体，建议：

- 不要让它们通过 Auto Sable Physics 自动创建。
- 如果需要排除相关方块，使用 `immobile` / `force_connecting` / `force_supporting` 标签。
- 若使用 Sable，也应自行管理 sub-level 生命周期。

## 8. API 稳定性

当前没有正式公开 Java API。推荐通过数据包标签和配置兼容。

可作为未来 API 的候选能力：

- 标记某个区域为 affected。
- 查询某方块是否会被视为支撑。
- 查询某两个方块是否会连接。
- 注册自定义材料连接限制。
- 排除某个 sub-level 的自动还原。

## 9. 测试建议

开发者应测试：

1. 你的方块能否安全被 Sable 移动。
2. 是否含 BlockEntity，NBT 能否保存恢复。
3. 是否应承重。
4. 是否应横向连接。
5. 是否会被误判为树叶、沙砾、地形材料。
6. 是否会因无碰撞形状被压坏或禁止放置。
7. 是否会被自动还原影响长期结构。
