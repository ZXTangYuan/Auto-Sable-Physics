package com.zxtangyuan.autosablephysics.util;


/**
 * 自动扫描来源枚举。
 * Automatic scan reason enum.
 */
public enum ScanReason {
    BLOCK_BREAK,
    BLOCK_PLACE,
    EXPLOSION,
    PISTON,
    LIVING_DESTROY_BLOCK,
    ENDERMAN_CARRY,
    FALLING_BLOCK,
    FLUID_PLACE_BLOCK,
    FARMLAND_TRAMPLE,
    TOOL_MODIFICATION,
    NEIGHBOR_PHYSICS,
    /**
     * Sable 物理体还原为原版方块后触发的周边影响扫描。
     */
    SUBLEVEL_RESTORE,
    /**
     * 只执行小型孤立物体补偿扫描。
     * 用于 triggerOnPlace=false 时，对没有底部支撑的新放置小物体做低成本复查，
     * 避免开启完整放置扫描导致大结构误触发。
     */
    SMALL_OBJECT_CHECK,
    MANUAL_COMMAND
}
