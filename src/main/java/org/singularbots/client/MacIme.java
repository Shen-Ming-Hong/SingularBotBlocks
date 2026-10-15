package org.singularbots.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.LibFFI;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.slf4j.LoggerFactory;

/**
 * Reads the macOS input-method composition (marked text) that GLFW keeps in its content view but never
 * draws or reports, so the editor can show it. Must run on the main (AppKit) thread.
 */
final class MacIme {
    private static final boolean MAC = Platform.get() == Platform.MACOSX;
    private static boolean failed;
    private static long msgSend, contentView, length, string, utf8, inputContext, discardMarkedText, unmarkText, viewClass, offset = -1;

    private MacIme() {}

    /** The text being composed, or "" when nothing is being composed or this is not macOS. */
    static String composition() {
        if (!MAC || failed) return "";
        try {
            if (msgSend == 0) {
                msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
                contentView = ObjCRuntime.sel_getUid("contentView"); length = ObjCRuntime.sel_getUid("length");
                string = ObjCRuntime.sel_getUid("string"); utf8 = ObjCRuntime.sel_getUid("UTF8String");
                inputContext = ObjCRuntime.sel_getUid("inputContext"); discardMarkedText = ObjCRuntime.sel_getUid("discardMarkedText");
                unmarkText = ObjCRuntime.sel_getUid("unmarkText");
            }
            long view = view();
            if (view == 0) return "";
            long cls = ObjCRuntime.object_getClass(view);
            if (cls != viewClass) {
                long ivar = ObjCRuntime.class_getInstanceVariable(cls, "markedText");
                offset = ivar == 0 ? -1 : ObjCRuntime.ivar_getOffset(ivar);
                viewClass = cls;
            }
            if (offset < 0) return "";
            long marked = MemoryUtil.memGetAddress(view + offset);
            if (marked == 0 || JNI.invokePPP(marked, length, msgSend) == 0) return "";
            long text = JNI.invokePPP(marked, string, msgSend);
            long bytes = text == 0 ? 0 : JNI.invokePPP(text, utf8, msgSend);
            return bytes == 0 ? "" : MemoryUtil.memUTF8(bytes);
        } catch (Throwable e) {
            failed = true;
            LoggerFactory.getLogger("SingularBotBlocks").warn("macOS input-method composition is unavailable", e);
            return "";
        }
    }

    /**
     * Cancels a composition. GLFW passes every in-world key press through the input method, so movement keys
     * leave stale marked text (for example a Zhuyin syllable) that must not follow the player into the editor.
     */
    static void discard() {
        if (!MAC || failed || composition().isEmpty()) return;
        try {
            long view = view();
            long context = JNI.invokePPP(view, inputContext, msgSend);
            if (context != 0) JNI.invokePPP(context, discardMarkedText, msgSend);
            JNI.invokePPP(view, unmarkText, msgSend);
        } catch (Throwable e) {
            failed = true;
            LoggerFactory.getLogger("SingularBotBlocks").warn("macOS input-method composition is unavailable", e);
        }
    }

    /**
     * Where the editor's caret is, in window points from the top-left of the game window, or null outside a text field.
     * The candidate list of the input method opens just below it.
     */
    private static volatile double[] caret;
    private static final String RECT_TYPES = "{CGRect={CGPoint=dd}{CGSize=dd}}@:{_NSRange=QQ}^{_NSRange=QQ}", INSERT_TYPES = "v@:@{_NSRange=QQ}";
    // The replacements stay referenced for the whole game: AppKit may still hold their addresses after they are removed.
    private static Callback firstRect, insertText;
    private static long patchedClass, originalRect, originalInsert;

    static void caret(double x, double y, double height) {
        caret = new double[]{x, y, height};
    }
    static void clearCaret() { caret = null; }

    /**
     * Fixes two answers GLFW's view gives the input method while the editor is open. GLFW says the text is at the
     * bottom-left corner of the screen, so the candidate list opens there, far from the field. And GLFW keeps the
     * marked text when the input method ends a composition by inserting text, for example an empty string after the
     * last Zhuyin symbol is deleted, so the editor would keep showing that symbol and treat every later key as part
     * of a composition. {@link #uninstall()} restores GLFW's own methods for the rest of the game, such as the chat.
     */
    static void install() {
        if (!MAC || failed || patchedClass != 0) return;
        try {
            if (msgSend == 0) composition();
            long view = view();
            if (view == 0) return;
            long cls = ObjCRuntime.object_getClass(view);
            if (firstRect == null) firstRect = new FirstRect();
            long rect = ObjCRuntime.sel_getUid("firstRectForCharacterRange:actualRange:"), insert = ObjCRuntime.sel_getUid("insertText:replacementRange:");
            long rectMethod = ObjCRuntime.class_getInstanceMethod(cls, rect), insertMethod = ObjCRuntime.class_getInstanceMethod(cls, insert);
            if (rectMethod == 0 || insertMethod == 0) return;
            originalRect = ObjCRuntime.method_getImplementation(rectMethod);
            originalInsert = ObjCRuntime.method_getImplementation(insertMethod);
            if (insertText == null) insertText = new InsertText();
            ObjCRuntime.class_replaceMethod(cls, rect, firstRect.address(), RECT_TYPES);
            ObjCRuntime.class_replaceMethod(cls, insert, insertText.address(), INSERT_TYPES);
            patchedClass = cls;
        } catch (Throwable e) {
            failed = true;
            LoggerFactory.getLogger("SingularBotBlocks").warn("macOS input-method placement is unavailable", e);
        }
    }

    static void uninstall() {
        if (patchedClass == 0) return;
        try {
            ObjCRuntime.class_replaceMethod(patchedClass, ObjCRuntime.sel_getUid("firstRectForCharacterRange:actualRange:"), originalRect, RECT_TYPES);
            ObjCRuntime.class_replaceMethod(patchedClass, ObjCRuntime.sel_getUid("insertText:replacementRange:"), originalInsert, INSERT_TYPES);
        } catch (Throwable e) {
            failed = true;
            LoggerFactory.getLogger("SingularBotBlocks").warn("macOS input-method placement is unavailable", e);
        }
        patchedClass = 0;
    }

    /** The rectangle in macOS screen coordinates (origin at the bottom-left of the main screen, y up). */
    private static double[] screenRect() {
        long handle = Minecraft.getInstance().getWindow().getWindow();
        int[] wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1];
        GLFW.glfwGetWindowPos(handle, wx, wy);
        GLFW.glfwGetWindowSize(handle, ww, wh);
        var mode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        int[] mx = new int[1], my = new int[1];
        GLFW.glfwGetMonitorPos(GLFW.glfwGetPrimaryMonitor(), mx, my);
        double screenHeight = mode == null ? 0 : my[0] + mode.height();
        double[] at = caret;
        // Outside a text field the candidate list opens at the bottom-left of the game window.
        double x = at == null ? 8 : at[0], top = at == null ? wh[0] - 24 : at[1], height = at == null ? 16 : at[2];
        return new double[]{wx[0] + x, screenHeight - (wy[0] + top + height), 1, height};
    }

    private static final class FirstRect extends Callback implements CallbackI {
        private static final FFICIF CIF = APIUtil.apiCreateCIF(LibFFI.FFI_DEFAULT_ABI,
            APIUtil.apiCreateStruct(LibFFI.ffi_type_double, LibFFI.ffi_type_double, LibFFI.ffi_type_double, LibFFI.ffi_type_double),
            LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer,
            APIUtil.apiCreateStruct(LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64), LibFFI.ffi_type_pointer);
        FirstRect() { super(CIF); }
        @Override public FFICIF getCallInterface() { return CIF; }
        @Override public void callback(long ret, long args) {
            double[] rect;
            try { rect = screenRect(); } catch (Throwable e) { rect = new double[4]; }
            for (int i = 0; i < 4; i++) MemoryUtil.memPutDouble(ret + i * 8L, rect[i]);
        }
    }

    /** Runs GLFW's own insertText (which types the characters), then ends the composition as NSTextInputClient requires. */
    private static final class InsertText extends Callback implements CallbackI {
        private static final FFICIF CIF = APIUtil.apiCreateCIF(LibFFI.FFI_DEFAULT_ABI, LibFFI.ffi_type_void,
            LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer,
            APIUtil.apiCreateStruct(LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64));
        InsertText() { super(CIF); }
        @Override public FFICIF getCallInterface() { return CIF; }
        @Override public void callback(long ret, long args) {
            LibFFI.nffi_call(CIF.address(), originalInsert, ret, args);
            try {
                long self = MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args));
                long marked = offset < 0 ? 0 : MemoryUtil.memGetAddress(self + offset);
                if (marked != 0 && JNI.invokePPP(marked, length, msgSend) != 0) JNI.invokePPP(self, unmarkText, msgSend);
            } catch (Throwable ignored) {}
        }
    }

    private static long view() {
        long window = GLFWNativeCocoa.glfwGetCocoaWindow(Minecraft.getInstance().getWindow().getWindow());
        return window == 0 ? 0 : JNI.invokePPP(window, contentView, msgSend);
    }
}
