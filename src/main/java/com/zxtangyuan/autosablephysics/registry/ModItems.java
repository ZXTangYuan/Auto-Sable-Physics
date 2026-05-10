package com.zxtangyuan.autosablephysics.registry;

import com.zxtangyuan.autosablephysics.AutoSablePhysics;
import com.zxtangyuan.autosablephysics.item.SableHammerItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;


/**
 * 物品注册表。
 * Item registry.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AutoSablePhysics.MOD_ID);

    public static final Supplier<Item> SABLE_HAMMER = ITEMS.register("sable_hammer", () ->
            new SableHammerItem(SableHammerItem.Mode.HAMMER, new Item.Properties().stacksTo(1).durability(256))
    );

    public static final Supplier<Item> SABLE_HAMMER_CLAW = ITEMS.register("sable_hammer_claw", () ->
            new SableHammerItem(SableHammerItem.Mode.CLAW, new Item.Properties().stacksTo(1).durability(256))
    );

    private ModItems() {
    }
}
