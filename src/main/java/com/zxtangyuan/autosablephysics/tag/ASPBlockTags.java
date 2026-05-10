package com.zxtangyuan.autosablephysics.tag;

import com.zxtangyuan.autosablephysics.AutoSablePhysics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;


/**
 * 方块标签键：用于可移动性、支撑性、连接性和兼容覆盖。
 * Block tag keys for movability, support, connectivity, and compatibility overrides.
 */
public final class ASPBlockTags {
    public static final TagKey<Block> IMMOBILE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "immobile")
    );

    public static final TagKey<Block> IGNORED = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "ignored")
    );

    /**
     * 强制判定为“可以提供底部支撑”的方块。
     * Force blocks to provide bottom support.
     * 白名单优先级高于通用碰撞体判断，但不会让空气/已忽略方块变成支撑。
     * This whitelist overrides generic collision checks, but never turns air/ignored blocks into support.
     */
    public static final TagKey<Block> FORCE_SUPPORTING = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "force_supporting")
    );

    /**
     * 强制判定为“不能提供底部支撑”的方块。
     * Force blocks to provide no bottom support.
     * 用于树叶、半砖、附魔台、活板门、装饰薄片等逻辑上不应承重的方块。
     * Use for leaves, slabs, enchanting tables, trapdoors, thin decorations, and other weak support blocks.
     */
    public static final TagKey<Block> NON_SUPPORTING = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "non_supporting")
    );

    /**
     * 强制判定为“可以与相邻可移动方块连接”的方块。
     * Force blocks to connect with adjacent movable blocks.
     * 适合后续给模组机器外壳、结构胶、框架方块等做白名单。
     * Useful for machine casings, structural glue, frame blocks, and similar modded blocks.
     */
    public static final TagKey<Block> FORCE_CONNECTING = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "force_connecting")
    );

    /**
     * 强制判定为“不会与相邻方块连接成同一个物理体”的方块。
     * 方块本身仍可移动，但不会把组件继续扩散到邻居。
     */
    public static final TagKey<Block> NON_CONNECTING = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "non_connecting")
    );

    /**
     * 横向连接受限方块。
     * 默认用于泥土、树叶、沙砾等不应在水平方向无限连成一整片物理体的材料。
     */
    public static final TagKey<Block> NO_HORIZONTAL_CONNECTING = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "no_horizontal_connecting")
    );



    /**
     * 横向连接有有限长度的方块。
     * 与 0.2.3 的 no_horizontal_connecting 兼容：不再表示“完全不能横向连接”，
     * 而是表示“横向连接不能无限延伸”。
     */
    public static final TagKey<Block> LIMITED_HORIZONTAL_CONNECTING = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "limited_horizontal_connecting")
    );

    /** 树叶类横向有限连接。Leaf-like bounded horizontal connectivity around logs. */
    public static final TagKey<Block> LEAF_HORIZONTAL_LIMITED = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "leaf_horizontal_limited")
    );

    /** 沙子、沙砾等颗粒方块横向有限连接。Granular bounded horizontal connectivity. */
    public static final TagKey<Block> GRANULAR_HORIZONTAL_LIMITED = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "granular_horizontal_limited")
    );

    /** 泥土、草方块、圆石类等地形材料横向有限连接。Terrain-like bounded horizontal connectivity. */
    public static final TagKey<Block> TERRAIN_HORIZONTAL_LIMITED = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AutoSablePhysics.MOD_ID, "terrain_horizontal_limited")
    );

    private ASPBlockTags() {
    }
}
