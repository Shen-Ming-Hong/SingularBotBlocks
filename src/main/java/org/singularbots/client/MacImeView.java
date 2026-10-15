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
 * Fixes answers GLFW's view gives the macOS input method in the game's own text fields. GLFW says the text is
 * at the bottom-left corner of the main screen, so the candidate list opened there, outside the game window. And
 * GLFW keeps the marked text when the input method ends a composition by inserting text, for example an empty
 * string after Backspace removes the last Zhuyin symbol, so that symbol stayed on screen and held back later keys.
 * Installed once; the editor may replace the same methods while it is open and puts these back when it closes.
 */
final class MacImeView {
    private static final boolean MAC = Platform.get() == Platform.MACOSX;
    /** {@code -Dccedu.imeDebug=true} logs what the input method sends, for diagnosing a specific input method. */
    static final boolean DEBUG = MAC && Boolean.getBoolean("ccedu.imeDebug");
    private static final String RECT_TYPES = "{CGRect={CGPoint=dd}{CGSize=dd}}@:{_NSRange=QQ}^{_NSRange=QQ}", INSERT_TYPES = "v@:@{_NSRange=QQ}";
    // Stay referenced for the whole game: AppKit keeps their addresses.
    private static Callback firstRect, insertText, markedRange, setMarkedText;
    private static long msgSend, unmarkText, length, originalInsert, originalSetMarked, markedTextOffset;
    private static boolean installed, failed;
    /** Caret in window points from the top-left of the game window: x, top, height; null outside a text field. */
    private static volatile double[] caret;

    private MacImeView() {}

    static void caret(double x, double top, double height) { caret = new double[]{x, top, height}; }
    static void clearCaret() { caret = null; }

    static void install() {
        if (!MAC || failed || installed) return;
        try {
            long window = GLFWNativeCocoa.glfwGetCocoaWindow(Minecraft.getInstance().getWindow().getWindow());
            if (window == 0) return;
            msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            unmarkText = ObjCRuntime.sel_getUid("unmarkText");
            length = ObjCRuntime.sel_getUid("length");
            long view = JNI.invokePPP(window, ObjCRuntime.sel_getUid("contentView"), msgSend);
            if (view == 0) return;
            long cls = ObjCRuntime.object_getClass(view), rect = ObjCRuntime.sel_getUid("firstRectForCharacterRange:actualRange:"),
                insert = ObjCRuntime.sel_getUid("insertText:replacementRange:");
            long insertMethod = ObjCRuntime.class_getInstanceMethod(cls, insert);
            if (ObjCRuntime.class_getInstanceMethod(cls, rect) == 0 || insertMethod == 0) { failed = true; return; }
            originalInsert = ObjCRuntime.method_getImplementation(insertMethod);
            firstRect = new FirstRect();
            insertText = new InsertText();
            ObjCRuntime.class_replaceMethod(cls, rect, firstRect.address(), RECT_TYPES);
            ObjCRuntime.class_replaceMethod(cls, insert, insertText.address(), INSERT_TYPES);
            long setMarked = ObjCRuntime.sel_getUid("setMarkedText:selectedRange:replacementRange:"), setMarkedMethod = ObjCRuntime.class_getInstanceMethod(cls, setMarked);
            long ivar = ObjCRuntime.class_getInstanceVariable(cls, "markedText"), range = ObjCRuntime.sel_getUid("markedRange");
            if (ivar != 0 && ObjCRuntime.class_getInstanceMethod(cls, range) != 0) {
                markedTextOffset = ObjCRuntime.ivar_getOffset(ivar);
                markedRange = new MarkedRange();
                ObjCRuntime.class_replaceMethod(cls, range, markedRange.address(), "{_NSRange=QQ}@:");
            }
            if (DEBUG && setMarkedMethod != 0) {
                originalSetMarked = ObjCRuntime.method_getImplementation(setMarkedMethod);
                setMarkedText = new SetMarkedText();
                ObjCRuntime.class_replaceMethod(cls, setMarked, setMarkedText.address(), "v@:@{_NSRange=QQ}{_NSRange=QQ}");
            }
            installed = true;
        } catch (Throwable e) {
            failed = true;
            LoggerFactory.getLogger("SingularBotBlocks").warn("macOS input-method placement is unavailable", e);
        }
    }

    /** The caret rectangle in macOS screen coordinates (origin at the bottom-left of the main screen, y up). */
    private static double[] screenRect() {
        long handle = Minecraft.getInstance().getWindow().getWindow();
        int[] wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1];
        GLFW.glfwGetWindowPos(handle, wx, wy);
        GLFW.glfwGetWindowSize(handle, ww, wh);
        long monitor = GLFW.glfwGetPrimaryMonitor();
        var mode = GLFW.glfwGetVideoMode(monitor);
        int[] mx = new int[1], my = new int[1];
        GLFW.glfwGetMonitorPos(monitor, mx, my);
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
            if (DEBUG) try {
                long string = MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args + 2L * Long.BYTES));
                LoggerFactory.getLogger("SingularBotBlocks").info("IME insertText length {}, composition before '{}'",
                    string == 0 ? -1 : JNI.invokePPP(string, ObjCRuntime.sel_getUid("length"), msgSend), MacIme.composition());
            } catch (Throwable ignored) {}
            LibFFI.nffi_call(CIF.address(), originalInsert, ret, args);
            try { JNI.invokePPP(MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args)), unmarkText, msgSend); } catch (Throwable ignored) {}
        }
    }

    /**
     * GLFW reports the marked range one character short, so a composition of a single symbol or word looked empty
     * to the input method and the first Backspace only resynchronised it instead of deleting.
     */
    private static final class MarkedRange extends Callback implements CallbackI {
        private static final FFICIF CIF = APIUtil.apiCreateCIF(LibFFI.FFI_DEFAULT_ABI,
            APIUtil.apiCreateStruct(LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64), LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer);
        MarkedRange() { super(CIF); }
        @Override public FFICIF getCallInterface() { return CIF; }
        @Override public void callback(long ret, long args) {
            long count = 0;
            try {
                long marked = MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args)) + markedTextOffset);
                count = marked == 0 ? 0 : JNI.invokePPP(marked, length, msgSend);
            } catch (Throwable ignored) {}
            // NSNotFound with length 0 is AppKit's "no marked text".
            MemoryUtil.memPutLong(ret, count > 0 ? 0 : Long.MAX_VALUE);
            MemoryUtil.memPutLong(ret + 8, count);
        }
    }

    /** Diagnostics only: logs each composition update before GLFW stores it. */
    private static final class SetMarkedText extends Callback implements CallbackI {
        private static final FFICIF CIF = APIUtil.apiCreateCIF(LibFFI.FFI_DEFAULT_ABI, LibFFI.ffi_type_void,
            LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer, LibFFI.ffi_type_pointer,
            APIUtil.apiCreateStruct(LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64), APIUtil.apiCreateStruct(LibFFI.ffi_type_uint64, LibFFI.ffi_type_uint64));
        SetMarkedText() { super(CIF); }
        @Override public FFICIF getCallInterface() { return CIF; }
        @Override public void callback(long ret, long args) {
            try {
                long string = MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args + 2L * Long.BYTES));
                long selected = MemoryUtil.memGetAddress(args + 3L * Long.BYTES);
                LoggerFactory.getLogger("SingularBotBlocks").info("IME setMarkedText length {}, selected {}+{}, composition before '{}'",
                    string == 0 ? -1 : JNI.invokePPP(string, ObjCRuntime.sel_getUid("length"), msgSend),
                    MemoryUtil.memGetLong(selected), MemoryUtil.memGetLong(selected + 8), MacIme.composition());
            } catch (Throwable ignored) {}
            LibFFI.nffi_call(CIF.address(), originalSetMarked, ret, args);
        }
    }
}
