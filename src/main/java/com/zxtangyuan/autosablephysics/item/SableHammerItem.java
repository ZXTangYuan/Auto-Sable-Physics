package com.zxtangyuan.autosablephysics.item;

import com.zxtangyuan.autosablephysics.registry.ModItems;
import com.zxtangyuan.autosablephysics.util.DelayedAssemblyManager;
import com.zxtangyuan.autosablephysics.util.SableSubLevelAccess;
import com.zxtangyuan.autosablephysics.util.SubLevelRestorationService;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;


/**
 * Sable 锤子工具：锤面保存物理体，齿面还原物理体。
 * Sable hammer tool: hammer face pins sub-levels, claw face restores sub-levels.
 */
public class SableHammerItem extends Item {
    private final Mode mode;

    public SableHammerItem(Mode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                player.setItemInHand(hand, switchedCopy(stack));
                player.displayClientMessage(Component.translatable("item.autosablephysics.sable_hammer.mode", nextModeName()), true);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level rawLevel = context.getLevel();
        Player player = context.getPlayer();
        if (!(rawLevel instanceof ServerLevel level) || player == null) {
            return InteractionResult.sidedSuccess(rawLevel.isClientSide());
        }

        if (player.isShiftKeyDown()) {
            ItemStack switched = switchedCopy(context.getItemInHand());
            player.setItemInHand(context.getHand(), switched);
            player.displayClientMessage(Component.translatable("item.autosablephysics.sable_hammer.mode", nextModeName()), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        ServerSubLevel subLevel = SableSubLevelAccess.getContainingServerSubLevel(level, clicked);
        if (subLevel == null || subLevel.isRemoved()) {
            player.displayClientMessage(Component.translatable("item.autosablephysics.sable_hammer.no_sublevel"), true);
            return InteractionResult.SUCCESS;
        }

        if (mode == Mode.HAMMER) {
            DelayedAssemblyManager.setSubLevelPinned(subLevel.getUniqueId(), true);
            player.displayClientMessage(Component.translatable("item.autosablephysics.sable_hammer.pinned"), true);
            return InteractionResult.SUCCESS;
        }

        SubLevelRestorationService.RestoreResult result = SubLevelRestorationService.restoreSubLevel(level, subLevel, true, true);
        if (result.success()) {
            player.displayClientMessage(Component.translatable("item.autosablephysics.sable_hammer.restored", result.blocksRestored()), true);
        } else {
            player.displayClientMessage(Component.translatable("item.autosablephysics.sable_hammer.restore_failed", result.message()), true);
        }
        return InteractionResult.SUCCESS;
    }

    private ItemStack switchedCopy(ItemStack oldStack) {
        ItemStack newStack = new ItemStack(mode == Mode.HAMMER ? ModItems.SABLE_HAMMER_CLAW.get() : ModItems.SABLE_HAMMER.get());
        if (oldStack.isDamaged()) {
            newStack.setDamageValue(oldStack.getDamageValue());
        }
        return newStack;
    }

    private Component nextModeName() {
        return Component.translatable(mode == Mode.HAMMER
                ? "item.autosablephysics.sable_hammer.mode_claw"
                : "item.autosablephysics.sable_hammer.mode_hammer");
    }

    public enum Mode {
        HAMMER,
        CLAW
    }
}
