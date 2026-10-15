package org.singularbots;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import org.singularbots.compat.cct.TeachingContent;

/** Common entrypoint intentionally has no browser or Minecraft client references. */
@Mod(SingularBotBlocks.MOD_ID)
public final class SingularBotBlocks {
    public static final String MOD_ID = "singularbots";
    public SingularBotBlocks(IEventBus bus) {
        TeachingContent.register(bus);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(org.singularbots.compat.cct.TeachingLua::register));
    }
}
