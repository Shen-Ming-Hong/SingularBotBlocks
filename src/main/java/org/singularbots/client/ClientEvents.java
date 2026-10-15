package org.singularbots.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Standalone editor startup is reserved for explicitly enabled browser smoke runs. */
@EventBusSubscriber(modid="singularbots", value=Dist.CLIENT)
public final class ClientEvents {
    private static boolean smokeOpened;
    private static int smokeTicks;

    /** The opaque editor also hides the HUD, so skip drawing it. */
    @SubscribeEvent public static void hideHud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof EditorScreen) event.setCanceled(true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ccedu.browserSmoke")) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!smokeOpened) {
            smokeTicks++;
            if (smokeTicks % 100 == 0) org.slf4j.LoggerFactory.getLogger("SingularBotBlocks").info("Browser smoke waiting at {}", minecraft.screen == null ? "no screen" : minecraft.screen.getClass().getName());
            if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
                minecraft.setScreen(new TitleScreen());
            }
        }
        if (!smokeOpened && minecraft.screen instanceof TitleScreen) {
            smokeOpened = true; minecraft.setScreen(new EditorScreen(minecraft.screen, true));
        }
    }
}
