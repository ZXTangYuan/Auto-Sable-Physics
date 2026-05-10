package com.zxtangyuan.autosablephysics.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Sable sub-level 查询工具。
 *
 * <p>本类只做非常薄的一层封装，避免多个类直接散落调用 Sable.HELPER。</p>
 */
public final class SableSubLevelAccess {
    private SableSubLevelAccess() {
    }

    public static @Nullable ServerSubLevel getContainingServerSubLevel(ServerLevel level, BlockPos pos) {
        try {
            SubLevel containing = Sable.HELPER.getContaining(level, pos);
            if (containing instanceof ServerSubLevel serverSubLevel) {
                return serverSubLevel;
            }
        } catch (Throwable ignored) {
            // Sable/Companion 在某些边界状态下可能抛异常。查询失败时保守视为没有 sub-level。
        }
        return null;
    }
}
