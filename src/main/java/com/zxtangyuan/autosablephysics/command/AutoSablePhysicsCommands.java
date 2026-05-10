package com.zxtangyuan.autosablephysics.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import com.zxtangyuan.autosablephysics.registry.ModItems;
import com.zxtangyuan.autosablephysics.util.AssemblyStats;
import com.zxtangyuan.autosablephysics.util.ScanReason;
import com.zxtangyuan.autosablephysics.util.SableAssemblyService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;


/**
 * 调试命令入口：提供手动扫描、清理 Sable、锤子模式切换等命令。
 * Debug command entry: provides manual scan, Sable cleanup, and hammer mode commands.
 */
public final class AutoSablePhysicsCommands {
    private AutoSablePhysicsCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("autosablephysics")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("scan_here")
                                .executes(ctx -> scan(ctx, BlockPos.containing(ctx.getSource().getPosition()), ASPServerConfig.MANUAL_SCAN_RADIUS.get()))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> scan(ctx, BlockPos.containing(ctx.getSource().getPosition()), IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("scan_pos")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> scan(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), ASPServerConfig.MANUAL_SCAN_RADIUS.get()))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> scan(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("remove_all_sable")
                                .executes(AutoSablePhysicsCommands::removeAllSableByCommand))
                        .then(Commands.literal("hammer_toggle")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(AutoSablePhysicsCommands::toggleHammerMode))
        );
    }

    private static int scan(CommandContext<CommandSourceStack> ctx, BlockPos center, int radius) {
        ServerLevel level = ctx.getSource().getLevel();
        AssemblyStats stats = SableAssemblyService.scanAndAssemble(level, center, ScanReason.MANUAL_COMMAND, radius);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.autosablephysics.scan.success",
                stats.assembledObjects(),
                stats.assembledBlocks(),
                stats.candidateOrigins(),
                stats.skippedTooManyBlocks(),
                stats.skippedBoundary(),
                stats.skippedSupported(),
                stats.skippedTooSmall()
        ), true);
        return stats.assembledObjects();
    }


    private static int toggleHammerMode(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.is(ModItems.SABLE_HAMMER.get())) {
            ItemStack next = new ItemStack(ModItems.SABLE_HAMMER_CLAW.get());
            next.setDamageValue(held.getDamageValue());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, next);
            ctx.getSource().sendSuccess(() -> Component.translatable("item.autosablephysics.sable_hammer.mode", Component.translatable("item.autosablephysics.sable_hammer.mode_claw")), false);
            return 1;
        }
        if (held.is(ModItems.SABLE_HAMMER_CLAW.get())) {
            ItemStack next = new ItemStack(ModItems.SABLE_HAMMER.get());
            next.setDamageValue(held.getDamageValue());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, next);
            ctx.getSource().sendSuccess(() -> Component.translatable("item.autosablephysics.sable_hammer.mode", Component.translatable("item.autosablephysics.sable_hammer.mode_hammer")), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("item.autosablephysics.sable_hammer.no_hammer"));
        return 0;
    }

    /**
     * Sable 暂未把 remove 命令对应能力暴露为稳定公共 API；这里保留命令转发，等同于原 MCreator 物品的 /sable remove @e。
     */
    private static int removeAllSableByCommand(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getServer().getCommands().performPrefixedCommand(
                ctx.getSource().withSuppressedOutput().withPermission(4),
                "sable remove @e"
        );
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.autosablephysics.remove_all_sable.success"), true);
        return 1;
    }
}
