package com.zxtangyuan.autosablephysics;

import com.zxtangyuan.autosablephysics.command.AutoSablePhysicsCommands;
import com.zxtangyuan.autosablephysics.config.ASPServerConfig;
import com.zxtangyuan.autosablephysics.event.AutoAssemblyEvents;
import com.zxtangyuan.autosablephysics.registry.ModCreativeTabs;
import com.zxtangyuan.autosablephysics.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组入口：注册配置、物品、事件和命令。
 * Mod entry point: registers configs, items, events, and commands.
 */
@Mod(AutoSablePhysics.MOD_ID)
public final class AutoSablePhysics {
    public static final String MOD_ID = "autosablephysics";
    public static final Logger LOGGER = LoggerFactory.getLogger("Auto Sable Physics");

    public AutoSablePhysics(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        modBus.addListener(ModCreativeTabs::buildCreativeTab);

        container.registerConfig(ModConfig.Type.SERVER, ASPServerConfig.SPEC);

        // 中文：全部逻辑只挂到运行期事件总线；客户端专用输入不在本版本实现，保证 dedicated server 可加载。
        // EN: All gameplay logic is attached to the runtime event bus; no client-only input code is loaded on dedicated servers.
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onPistonPost);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onLivingDestroyBlock);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onFluidPlaceBlock);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onFarmlandTrample);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onBlockToolModification);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener(AutoAssemblyEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(AutoSablePhysicsCommands::onRegisterCommands);
    }
}
