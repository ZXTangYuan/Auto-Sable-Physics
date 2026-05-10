package com.zxtangyuan.autosablephysics.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;


/**
 * 创造模式物品栏注册。
 * Creative tab population.
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SABLE_HAMMER.get());
            event.accept(ModItems.SABLE_HAMMER_CLAW.get());
        }
    }
}
