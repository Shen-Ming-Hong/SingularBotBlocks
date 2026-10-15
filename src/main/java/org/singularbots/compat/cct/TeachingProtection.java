package org.singularbots.compat.cct;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid="singularbots")
public final class TeachingProtection {
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if(event.getState().getBlock() instanceof PermissionBlock&&!event.getPlayer().hasPermissions(2))event.setCanceled(true);
        var access=TeachingPermissions.CURRENT.get();if(access!=null&&TeachingPermissions.junior(access)&&!TeachingPermissions.editable(access.getLevel(),event.getPos()))event.setCanceled(true);
        // Physical destruction is allowed for other students; private code lives outside the block/item.
    }
    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent event){
        if(event.getPlacedBlock().getBlock() instanceof PermissionBlock&&(!(event.getEntity() instanceof ServerPlayer p)||!p.hasPermissions(2)))event.setCanceled(true);
        var access=TeachingPermissions.CURRENT.get();if(access!=null&&TeachingPermissions.junior(access)&&!TeachingPermissions.editable(access.getLevel(),event.getPos()))event.setCanceled(true);
    }
}
