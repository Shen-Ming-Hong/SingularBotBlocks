package org.singularbots.client;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Closes editor browsers in a normal client tick before MCEF's macOS quit shutdown.
 * MCEF disposes CEF inside the native terminate callback (App menu Quit / Cmd+Q). Force-closing
 * a still-open editor browser there never returns, so the game never saves or exits. Deferring the
 * quit one tick lets the editors close like a normal screen close; CEF then finds them closing and
 * skips the blocking close.
 */
@EventBusSubscriber(modid="singularbots", value=Dist.CLIENT)
public final class BrowserShutdown {
    /** Upper bound for CEF to confirm the closes; MCEF's quit is safe either way. */
    private static final int MAX_WAIT_TICKS = 40;
    private static final Set<EditorScreen> editors = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicInteger openBrowsers = new AtomicInteger();
    private static Runnable mcefQuit;
    private static boolean quitting;
    private static int waited;

    private BrowserShutdown() {}

    /** Wraps MCEF's macOS quit handler once; it only runs on macOS. */
    static void install() {
        var app = MCEF.getApp().getHandle();
        if (!(app.macOSTerminationRequestRunnable instanceof Guard)) app.macOSTerminationRequestRunnable = new Guard(app.macOSTerminationRequestRunnable);
    }
    private record Guard(Runnable quit) implements Runnable {
        @Override public void run() {
            if (quitting) return;
            org.slf4j.LoggerFactory.getLogger("SingularBotBlocks").info("Quit requested; closing {} editor browser(s) before MCEF shutdown", editors.size());
            mcefQuit = quit; quitting = true;
        }
    }

    /** True once the player asked to quit; no new editor browser may start. */
    static boolean quitting() { return quitting; }
    static void opened(EditorScreen editor) { editors.add(editor); openBrowsers.incrementAndGet(); }
    static void disposed(EditorScreen editor) { editors.remove(editor); }
    /** Called from CEF's onBeforeClose for each editor browser. */
    static void closed() { openBrowsers.decrementAndGet(); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (mcefQuit == null) return;
        if (waited++ == 0) for (var editor : List.copyOf(editors)) editor.dispose();
        if (openBrowsers.get() > 0 && waited < MAX_WAIT_TICKS) return;
        var quit = mcefQuit; mcefQuit = null;
        org.slf4j.LoggerFactory.getLogger("SingularBotBlocks").info("Running MCEF quit after {} tick(s), {} editor browser(s) still closing", waited, openBrowsers.get());
        try { quit.run(); } finally { Minecraft.getInstance().stop(); }
    }
}
