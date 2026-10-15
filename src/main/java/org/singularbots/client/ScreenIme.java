package org.singularbots.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Lets the game's own text fields (chat, anvil, the robot name field) take macOS input-method text such as Zhuyin.
 * GLFW hands every key to the game before the input method sees it and never draws the word being composed, so the
 * Enter that picks a word also sent the empty chat and the student saw nothing while typing. The editor has its own
 * handling in {@link EditorScreen}.
 */
@EventBusSubscriber(modid="singularbots", value=Dist.CLIENT)
public final class ScreenIme {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SingularBotBlocks");
    private static boolean discardPending;
    private static String shown = "";

    private ScreenIme() {}

    /** The key that opens a screen (T for chat) also reaches the input method afterwards; drop what it starts. */
    @SubscribeEvent public static void opened(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof EditorScreen) return;
        discardPending = true;
        MacImeView.install();
    }

    @SubscribeEvent public static void closing(ScreenEvent.Closing event) { MacImeView.clearCaret(); }

    /** While a word is being composed, Enter, Backspace, arrows and Escape belong to the input method. */
    @SubscribeEvent public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (field(event.getScreen()) == null) return;
        String now = MacIme.composition();
        if (!now.isEmpty()) event.setCanceled(true);
        if (MacImeView.DEBUG) LOGGER.info("IME key {} pressed, composition '{}', held {}", event.getKeyCode(), now, !now.isEmpty());
    }

    @SubscribeEvent public static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (MacImeView.DEBUG && field(event.getScreen()) != null) LOGGER.info("IME key {} released, composition '{}'", event.getKeyCode(), MacIme.composition());
    }

    @SubscribeEvent public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof EditorScreen) { discardPending = false; return; }
        if (discardPending) { discardPending = false; MacIme.discard(); }
        if (MacImeView.DEBUG) { String now = MacIme.composition(); if (!now.equals(shown)) LOGGER.info("IME composition '{}' -> '{}'", shown, shown = now); }
    }

    /** Draws the word being composed at the caret, underlined, as macOS text fields do. */
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof EditorScreen) return;
        EditBox box = field(event.getScreen());
        if (box == null) { MacImeView.clearCaret(); return; }
        String text = MacIme.composition();
        var font = Minecraft.getInstance().font;
        int x = box.getScreenX(box.getCursorPosition()), y = box.getY() + (box.getHeight() - 8) / 2, width = font.width(text);
        x = Math.max(0, Math.min(x, event.getScreen().width - width - 2));
        // The input method opens its candidate list just below this line; GUI units map to window points.
        var window = Minecraft.getInstance().getWindow();
        double points = (double) window.getScreenWidth() / Math.max(1, window.getGuiScaledWidth());
        MacImeView.caret(x * points, (y - 2) * points, 12 * points);
        if (text.isEmpty()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x - 1, y - 2, x + width + 1, y + 10, 0xF0000000);
        graphics.drawString(font, text, x, y, 0xFFFFFF55, false);
        graphics.fill(x, y + 9, x + width, y + 10, 0xFFFFFF55);
        graphics.pose().popPose();
    }

    /** The focused, visible game text field of this screen, or null; the editor is excluded. */
    private static EditBox field(Screen screen) {
        if (screen == null || screen instanceof EditorScreen) return null;
        GuiEventListener focused = screen.getFocused();
        if (focused instanceof EditBox box && box.isVisible() && box.isFocused()) return box;
        for (GuiEventListener child : screen.children())
            if (child instanceof EditBox box && box.isVisible() && box.isFocused()) return box;
        return null;
    }
}
