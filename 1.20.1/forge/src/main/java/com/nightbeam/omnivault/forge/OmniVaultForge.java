package com.nightbeam.omnivault.forge;

import com.nightbeam.omnivault.OmniVault;
import com.nightbeam.omnivault.OmniVaultCommon;
import com.nightbeam.omnivault.forge.client.OmniVaultForgeClient;
import com.nightbeam.omnivault.forge.drawer.DrawerForgeEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(OmniVault.MOD_ID)
public class OmniVaultForge {
    public OmniVaultForge(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        OmniVaultCommon.init();
        ForgeRegistryHelper.attach(modBus);
        ForgeNetworking.register();
        CapabilityExposure.register();
        LegacyMappings.register();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                OmniVaultCommon.serverTick(event.getServer());
            }
        });
        PhysicalForge.init();
        BackpackForgeEvents.register();
        DrawerForgeEvents.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            OmniVaultForgeClient.init(modBus);
        }
    }
}
