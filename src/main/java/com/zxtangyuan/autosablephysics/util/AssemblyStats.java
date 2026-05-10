package com.zxtangyuan.autosablephysics.util;


/**
 * 扫描统计结果。
 * Scan result statistics.
 */
public record AssemblyStats(
        int candidateOrigins,
        int assembledObjects,
        int assembledBlocks,
        int skippedTooManyBlocks,
        int skippedBoundary,
        int skippedSupported,
        int skippedTooSmall
) {
    public static final AssemblyStats EMPTY = new AssemblyStats(0, 0, 0, 0, 0, 0, 0);
}
