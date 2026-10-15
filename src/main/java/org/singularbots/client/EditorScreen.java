package org.singularbots.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFClient;
import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.cef.CefClient;
import org.cef.browser.*;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.singularbots.editor.DraftStore;
import org.singularbots.editor.EditorOrigin;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only browser with a bounded bridge to the current server-authorized turtle menu. */
public final class EditorScreen extends Screen {
    private final Screen parent;
    private final boolean smoke;
    private final DraftStore drafts;
    private final DraftStore recovery;
    private final org.singularbots.editor.EditorTheme theme;
    private final java.util.UUID turtle;
    private MCEFBrowser browser;
    private CefClient client;
    private CefMessageRouter router;
    private String error;
    private int ticks, resultTick = -1;
    private boolean ready, smokePassed, fontLoaded, reported;
    private String blocklyVersion;
    private JsonObject nativeManifest, nativeInstallation;
    private int browserX,browserY,browserWidth,browserHeight,surfaceWidth,surfaceHeight,browserMouseButton=-1;
    private final boolean connectedRemote;
    private long openedAt=System.nanoTime();
    /** When the last editor reported its workbench usable (System.nanoTime), for the QA timing probe. */
    static volatile long usableAt;
    private String locale;
    private String pageTheme;
    private boolean attached;
    private String pendingReady;
    private char pendingHighSurrogate;
    private String composition = "";
    /** A native select list (for example the program library) is open over the page. */
    private volatile boolean nativePopup;
    private CefQueryCallback pendingReadyCallback;

    public EditorScreen(Screen parent, boolean smoke) {
        this(parent,smoke,null);
    }
    public EditorScreen(Screen parent, boolean smoke, java.util.UUID turtle) {
        super(Component.translatable("screen.singularbots.editor"));
        this.parent = parent; this.smoke = smoke; this.turtle=turtle;
        connectedRemote=turtle!=null&&parent instanceof org.singularbots.compat.cct.client.RemoteScreen;
        drafts = new DraftStore(Minecraft.getInstance().gameDirectory.toPath().resolve("singularbots"));
        theme = new org.singularbots.editor.EditorTheme(Minecraft.getInstance().gameDirectory.toPath().resolve("singularbots"));
        var minecraft=Minecraft.getInstance();
        String server=minecraft.getCurrentServer()!=null?minecraft.getCurrentServer().ip:
            minecraft.getSingleplayerServer()!=null?minecraft.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString():"local";
        recovery=turtle==null||minecraft.player==null?null:new DraftStore(minecraft.gameDirectory.toPath().resolve("singularbots/recovery")
            .resolve(java.util.UUID.nameUUIDFromBytes(server.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()).resolve(minecraft.player.getUUID().toString()));
    }

    /** Starts loading the page before the student opens the programming tab; init() attaches it. */
    public static EditorScreen prewarm(Screen parent, java.util.UUID turtle) {
        var editor=new EditorScreen(parent,false,turtle);var window=Minecraft.getInstance().getWindow();
        editor.browserWidth=Math.max(1,window.getGuiScaledWidth());editor.browserHeight=Math.max(1,window.getGuiScaledHeight());
        editor.createBrowser();return editor;
    }
    public boolean reusable() {
        return !attached&&browser!=null&&error==null&&currentLocale().equals(locale)&&theme.load().equals(pageTheme);
    }
    /** Releases a prewarmed browser that was never shown. */
    public void dispose() {
        BrowserShutdown.disposed(this);
        if (client != null && router != null) { client.removeMessageRouter(router); router.dispose(); router = null; }
        if (browser != null) { browser.close(); browser = null; }
        if (client != null) { client.dispose(); client = null; }
    }
    private static String currentLocale() {
        return Minecraft.getInstance().getLanguageManager().getSelected().equals("zh_tw") ? "zh_tw" : "en_us";
    }

    @Override protected void init() {
        browserX=browserY=0;browserWidth=width;browserHeight=height;
        if(!attached){attached=true;openedAt=System.nanoTime();MacIme.discard();}
        MacIme.install();
        if (error != null) { fail(error); return; }
        if (browser != null) {
            resizeBrowser();browser.setFocus(true);
            if(pendingReady!=null){String text=pendingReady;var callback=pendingReadyCallback;pendingReady=null;pendingReadyCallback=null;
                Minecraft.getInstance().execute(()->handleQuery(text,callback));}
            return;
        }
        createBrowser();
    }

    private void createBrowser() {
        if (BrowserShutdown.quitting()) return;
        try {
            if (!MCEF.isInitialized()) throw new IllegalStateException("MCEF 2.1.6 is not initialized; install the pinned native runtime and restart");
            Path runtime = Path.of(System.getProperty("jcef.path"));
            nativeManifest = JsonParser.parseString(Files.readString(runtime.resolve("computercraftedu-native.json"))).getAsJsonObject();
            nativeInstallation = JsonParser.parseString(Files.readString(runtime.resolve("computercraftedu-install.json"))).getAsJsonObject();
            if (!"a78e832f9f13c2c688caea3d04d8b84fcd238d94".equals(nativeManifest.get("jcef_commit").getAsString())
                || !"116.0.27+gd8c85ac+chromium-116.0.5845.190".equals(nativeManifest.get("cef_version").getAsString()))
                throw new IllegalStateException("Browser native runtime does not match the pinned build");
            client = MCEF.getApp().getHandle().createClient();
            client.addRequestHandler(new CefRequestHandlerAdapter() {
                @Override public boolean onBeforeBrowse(CefBrowser b, CefFrame f, CefRequest r, boolean gesture, boolean redirect) {
                    return !EditorOrigin.isEditor(r.getURL());
                }
                @Override public boolean onOpenURLFromTab(CefBrowser b, CefFrame f, String url, boolean gesture) { return true; }
                @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b, CefFrame f, CefRequest r,
                    boolean navigation, boolean download, String origin, BoolRef disableDefault) {
                    disableDefault.set(true);
                    return new CefResourceRequestHandlerAdapter() {
                        @Override public boolean onBeforeResourceLoad(CefBrowser b, CefFrame f, CefRequest r) {
                            return EditorOrigin.assetPath(r.getURL()) == null;
                        }
                        @Override public CefResourceHandler getResourceHandler(CefBrowser b, CefFrame f, CefRequest r) {
                            return new LocalEditorResource();
                        }
                    };
                }
                @Override public void onRenderProcessTerminated(CefBrowser b, CefRequestHandler.TerminationStatus status) {
                    Minecraft.getInstance().execute(() -> fail("Browser renderer terminated: " + status));
                }
            });
            client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
                @Override public boolean onBeforePopup(CefBrowser b, CefFrame f, String url, String name) { return true; }
                @Override public void onBeforeClose(CefBrowser b) { BrowserShutdown.closed(); }
            });
            router = CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig("cceduQuery", "cceduCancel"),
                new CefMessageRouterHandlerAdapter() {
                    @Override public boolean onQuery(CefBrowser b, CefFrame f, long id, String request, boolean persistent, CefQueryCallback callback) {
                        if (b != browser || !f.isMain() || !EditorOrigin.isEditor(f.getURL()) || persistent
                            || request.length() > DraftStore.MAX_BYTES + 4096) {
                            callback.failure(403, "Invalid editor origin or message"); return true;
                        }
                        Minecraft.getInstance().execute(() -> handleQuery(request, callback));
                        return true;
                    }
                });
            client.addMessageRouter(router);
            locale = currentLocale();
            pageTheme = theme.load();
            browser = new MCEFBrowser(new MCEFClient(client), EditorOrigin.URL + "?lang=" + locale + "&theme=" + pageTheme, false) {
                @Override public void sendKeyPress(int key, long scan, int mods) {
                    super.sendKeyPress(key, scan, mods);
                    // Minecraft's char callback excludes Return. CEF also needs
                    // its character event to insert a newline into a textarea.
                    if (org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.MACOSX
                        && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER))
                        sendKeyEvent(new org.cef.event.CefKeyEvent(org.cef.event.CefKeyEvent.KEY_TYPE,key,'\r',mods));
                }
                @Override public void onPopupShow(org.cef.browser.CefBrowser b, boolean show) {
                    super.onPopupShow(b, show);
                    nativePopup = show;
                    // When a select list closes, CEF does not repaint the page under it and MCEF only stops drawing the list, so its
                    // last picture stays on screen until that area changes. Ask CEF to repaint the whole page.
                    if (!show) invalidate();
                }
                @Override public void setCursor(org.cef.misc.CefCursorType cursor) {
                    // POINTER also has shape 0 in this MCEF version. Restore GLFW's
                    // default cursor directly instead of creating an invalid standard shape.
                    if(cursor != org.cef.misc.CefCursorType.NONE && cursor.glfwId == 0){
                        long window=Minecraft.getInstance().getWindow().getWindow();
                        GLFW.glfwSetInputMode(window,GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
                        GLFW.glfwSetCursor(window,0L);
                    }else super.setCursor(cursor);
                }
            };
            browser.useBrowserControls(false);
            browser.setCloseAllowed(); browser.createImmediately(); BrowserShutdown.install(); BrowserShutdown.opened(this);
            resizeBrowser(); browser.setFocus(true);
        } catch (Exception | LinkageError e) { fail(e.toString()); }
    }

    private void resizeBrowser() {
        var window=Minecraft.getInstance().getWindow();
        // CEF paints in window pixels; using Retina framebuffer pixels quadruples
        // the software paint/upload work and mismatches fixed layout dimensions.
        surfaceWidth=Math.max(1,window.getScreenWidth());
        surfaceHeight=Math.max(1,window.getScreenHeight());
        browser.resize(surfaceWidth,surfaceHeight);
        browser.setZoomLevel(0);
    }
    private int pixelX(double value) { return (int)Math.round(value * surfaceWidth / browserWidth); }
    private int pixelY(double value) { return (int)Math.round(value * surfaceHeight / browserHeight); }
    private boolean inBrowser(double x,double y){return x>=browserX&&x<browserX+browserWidth&&y>=browserY&&y<browserY+browserHeight;}
    private void navigate(int page){if(browser!=null&&ready&&error==null)browser.executeJavaScript("window.cceduNavigate && window.cceduNavigate("+page+")",EditorOrigin.URL,0);else returnToPage(page);}
    private void returnToPage(int page){if(parent instanceof org.singularbots.compat.cct.client.RemoteScreen remote){remote.selectPage(page);Minecraft.getInstance().setScreen(parent);}}
    public void receiveRemote(org.singularbots.network.RemoteNetwork.State state){if(parent instanceof org.singularbots.compat.cct.client.RemoteScreen remote)remote.receive(state);}

    private void handleQuery(String text, CefQueryCallback callback) {
        try {
            JsonObject message = JsonParser.parseString(text).getAsJsonObject();
            // A prewarmed page asks for its session before it is shown; answer once it is attached.
            if (browser != null && !attached && pendingReady == null && "ready".equals(message.get("op").getAsString())) {
                pendingReady=text;pendingReadyCallback=callback;return;
            }
            if (browser == null || Minecraft.getInstance().screen != this) throw new IllegalStateException("Editor has closed");
            JsonObject reply = new JsonObject();
            if(message.get("op").getAsString().startsWith("program")) {
                if(turtle==null||!ready)throw new IllegalStateException("No turtle editor session");
                org.singularbots.compat.cct.client.ProgramClient.request(turtle,message,callback::success,error->callback.failure(400,error));
                return;
            }
            switch (message.get("op").getAsString()) {
                case "ready" -> {
                    blocklyVersion = message.get("blocklyVersion").getAsString();
                    if (!"11.2.2".equals(blocklyVersion)) throw new IllegalArgumentException("Unexpected Blockly version");
                    JsonObject draft = turtle==null?drafts.load():null;
                    if (draft != null) reply.add("draft", draft);
                    if(recovery!=null){var local=recovery.load();if(local!=null)reply.add("recovery",local);}
                    reply.addProperty("theme", theme.load());
                    reply.addProperty("smoke", smoke);reply.addProperty("turtle",turtle!=null); ready = true;
                    LoggerFactory.getLogger("SingularBotBlocks").info("Editor page ready after {} ms",(System.nanoTime()-openedAt)/1_000_000);
                    resizeBrowser();
                }
                case "setTheme" -> { if (!ready) throw new IllegalStateException("Editor is not ready"); String chosen = message.get("theme").getAsString(); theme.save(chosen); pageTheme = chosen; }
                case "saveDraft" -> { if (!ready||turtle!=null) throw new IllegalStateException("Local drafts require a local editor"); drafts.save(message.getAsJsonObject("draft")); }
                case "saveRecovery" -> {if(!ready||recovery==null)throw new IllegalStateException("No turtle recovery session");recovery.save(message.getAsJsonObject("draft"));}
                case "navigate" -> {if(!ready||!connectedRemote)throw new IllegalStateException("No remote editor session");int page=message.get("page").getAsInt();if(page<1||page>3)throw new IllegalArgumentException("Unknown remote page");callback.success("{}");Minecraft.getInstance().execute(()->{if(message.has("recovered")&&message.get("recovered").getAsBoolean()&&Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.singularbots.local_recovery"),false);returnToPage(page);});return;}
                case "remoteState" -> {if(!ready||!connectedRemote)throw new IllegalStateException("No remote editor session");reply=((org.singularbots.compat.cct.client.RemoteScreen)parent).editorState();}
                case "remoteAction" -> {if(!ready||!connectedRemote)throw new IllegalStateException("No remote editor session");String action=message.get("action").getAsString();if(!java.util.Set.of("snapshot","restore","select","lock").contains(action))throw new IllegalArgumentException("Unknown editor remote action");int value=message.has("value")?message.get("value").getAsInt():0;net.neoforged.neoforge.network.PacketDistributor.sendToServer(new org.singularbots.network.RemoteNetwork.Action(turtle,action,value,""));}
                case "close" -> { callback.success("{}"); Minecraft.getInstance().execute(() -> {if(message.has("recovered")&&message.get("recovered").getAsBoolean()&&Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.singularbots.local_recovery"),false);Minecraft.getInstance().setScreen(parent);}); return; }
                case "itemCatalog" -> {if(!ready)throw new IllegalStateException("Editor is not ready");
                    ItemCatalog.request(catalog->{if(browser!=null)callback.success(catalog.toString());},error->{if(browser!=null)callback.failure(500,error);});return;}
                case "imeCaret" -> {
                    // CSS pixels of the page map to window points through the surface the page is painted on.
                    var window=Minecraft.getInstance().getWindow();double scale=(double)window.getScreenWidth()/Math.max(1,surfaceWidth);
                    if(message.has("x"))MacIme.caret(message.get("x").getAsDouble()*scale,message.get("y").getAsDouble()*scale,message.get("height").getAsDouble()*scale);else MacIme.clearCaret();
                }
                case "opened" -> {usableAt=System.nanoTime();LoggerFactory.getLogger("SingularBotBlocks").info("Editor workbench usable after {} ms",(usableAt-openedAt)/1_000_000);}
                case "smokeResult" -> {
                    if (!smoke || !ready) throw new IllegalStateException("Smoke test not enabled");
                    fontLoaded = message.get("fontLoaded").getAsBoolean();
                    smokePassed = fontLoaded && message.get("passed").getAsBoolean() && message.get("blockCount").getAsInt() == 2
                        && "11.2.2".equals(message.get("blocklyVersion").getAsString());
                    resultTick = ticks;
                }
                default -> throw new IllegalArgumentException("Unknown editor operation");
            }
            callback.success(reply.toString());
        } catch (Exception e) {
            LoggerFactory.getLogger("SingularBotBlocks").warn("Editor request failed", e);
            callback.failure(400, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private String modVersion(String id) {
        return net.neoforged.fml.ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion().toString();
    }

    private void fail(String diagnostic) {
        error = diagnostic;
        LoggerFactory.getLogger("SingularBotBlocks").error("Browser gate failed: {}", diagnostic);
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> Minecraft.getInstance().setScreen(parent))
            .bounds(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The full-screen editor is opaque; a world blur behind it is wasted work.
        graphics.fill(0,0,width,height,0xff171f23);
        if (error != null) {
            graphics.drawWordWrap(font, Component.translatable("screen.singularbots.browser_error"), 20, 25, width - 40, 0xffffff);
            graphics.drawWordWrap(font, Component.literal(error), 20, 90, width - 40, 0xffaaaa);
            for(var widget:renderables)widget.render(graphics,mouseX,mouseY,partialTick); return;
        }
        if (browser == null || browser.getRenderer().getTextureID() == 0) return;
        showComposition();
        graphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        var matrix = graphics.pose().last().pose();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, browserX, browserY+browserHeight, 0).setUv(0, 1).setColor(255,255,255,255);
        buffer.addVertex(matrix, browserX+browserWidth, browserY+browserHeight, 0).setUv(1, 1).setColor(255,255,255,255);
        buffer.addVertex(matrix, browserX+browserWidth, browserY, 0).setUv(1, 0).setColor(255,255,255,255);
        buffer.addVertex(matrix, browserX, browserY, 0).setUv(0, 0).setColor(255,255,255,255);
        BufferUploader.drawWithShader(buffer.buildOrThrow()); RenderSystem.setShaderTexture(0, 0); RenderSystem.enableDepthTest();
        for(var widget:renderables)widget.render(graphics,mouseX,mouseY,partialTick);
    }

    @Override public void tick() {
        org.singularbots.compat.cct.client.ProgramClient.tick();
        ticks++;
        if(connectedRemote&&ticks%10==0)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new org.singularbots.network.RemoteNetwork.Action(turtle,"state",0,""));
        if (smoke && !reported && (error != null || ticks > 1200 || (resultTick >= 0 && ticks > resultTick + 40))) {
            boolean rendered = browser != null && browser.hasDocument() && browser.getRenderer().getTextureID() > 0;
            try {
                Path root = Minecraft.getInstance().gameDirectory.toPath();
                JsonObject report = new JsonObject();
                report.addProperty("passed", error == null && smokePassed && rendered);
                report.addProperty("initialized", MCEF.isInitialized()); report.addProperty("rendered", rendered);
                report.addProperty("fontLoaded", fontLoaded);
                report.addProperty("workspaceRoundtrip", smokePassed); report.addProperty("blockly", blocklyVersion);
                report.addProperty("os", System.getProperty("os.name")); report.addProperty("arch", System.getProperty("os.arch"));
                report.addProperty("java", System.getProperty("java.version"));
                report.addProperty("minecraft", net.minecraft.SharedConstants.getCurrentVersion().getName());
                report.addProperty("neoforge", modVersion("neoforge"));
                report.addProperty("computercraft", modVersion("computercraft"));
                report.addProperty("mcef", modVersion("mcef"));
                report.addProperty("runtimeCertified", false);
                report.addProperty("sourceCommit", System.getProperty("ccedu.sourceCommit", "working-tree"));
                report.addProperty("observedAt", java.time.Instant.now().toString());
                report.addProperty("nativePath", System.getProperty("jcef.path"));
                report.add("nativeBuild", nativeManifest); report.add("nativeInstallation", nativeInstallation);
                report.addProperty("limitations", "Automated startup/bridge/render/roundtrip only; drag, zoom, IME, offline and classroom acceptance remain pending");
                report.addProperty("diagnostic", error == null ? (smokePassed ? "" : "Browser smoke timed out") : error);
                Files.writeString(root.resolve("browser-smoke.json"), report.toString());
                Screenshot.grab(root.toFile(), "browser-smoke.png", Minecraft.getInstance().getMainRenderTarget(), c -> {});
            } catch (Exception e) { LoggerFactory.getLogger("SingularBotBlocks").error("Cannot save browser evidence", e); }
            reported = true;
        }
        if (smoke && reported && (ticks > resultTick + 60)) Minecraft.getInstance().stop();
    }

    @Override public void onClose() {
        if (browser != null && error == null && ready) browser.executeJavaScript("window.cceduSaveAndClose && window.cceduSaveAndClose()", EditorOrigin.URL, 0);
        else Minecraft.getInstance().setScreen(parent);
    }
    @Override public void removed() {
        MacIme.clearCaret();MacIme.uninstall();
        if(turtle!=null)org.singularbots.compat.cct.client.ProgramClient.close(turtle);
        dispose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (error != null || browser == null) return super.mouseClicked(x,y,button);
        if(super.mouseClicked(x,y,button))return true;
        if(!inBrowser(x,y))return false;
        // Clicking elsewhere ends a composition, as in native text fields.
        MacIme.discard();
        browserMouseButton=button;browser.sendMousePress(pixelX(x-browserX),pixelY(y-browserY),button); browser.setFocus(true); return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (browser == null || error != null) return super.mouseReleased(x,y,button);
        if(browserMouseButton!=button)return super.mouseReleased(x,y,button);
        browserMouseButton=-1;browser.sendMouseRelease(pixelX(x-browserX),pixelY(y-browserY),button); return true;
    }
    @Override public void mouseMoved(double x, double y) { if (browser != null) browser.sendMouseMove(pixelX(x-browserX),pixelY(y-browserY)); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (browser != null&&browserMouseButton==button) { browser.sendMouseMove(pixelX(x-browserX),pixelY(y-browserY)); return true; } return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (browser != null&&inBrowser(x,y)) { browser.sendMouseWheel(pixelX(x-browserX),pixelY(y-browserY),vertical,0); return true; } return false;
    }
    /** GLFW never draws the macOS input-method text being composed; the page shows it next to the focused field. */
    private void showComposition() {
        String now = MacIme.composition();
        if (now.equals(composition)) return;
        composition = now;
        if (ready && error == null) browser.executeJavaScript("window.cceduComposition && window.cceduComposition(" + new com.google.gson.Gson().toJson(now) + ")", EditorOrigin.URL, 0);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        // While a word is being composed, Backspace, Enter, arrows and Escape belong to the input method;
        // forwarding them would also delete text, submit, move the caret or close the editor.
        if (browser != null && !MacIme.composition().isEmpty()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            // Escape first closes an open select list, as in a normal browser, instead of leaving the editor.
            if (browser != null && nativePopup) { browser.sendKeyPress(key,scan,mods); return true; }
            if(browser!=null&&ready&&error==null)browser.executeJavaScript("window.cceduEscape && window.cceduEscape()",EditorOrigin.URL,0);
            else onClose();
            return true;
        }
        // Offscreen CEF on macOS ignores Cmd+V and Cmd+A in text fields, so the page applies them; the key is still
        // forwarded below for Blockly's own block shortcuts on the workspace.
        if (browser != null && Minecraft.ON_OSX && ready && error == null) {
            if (Screen.isPaste(key)) browser.executeJavaScript("window.cceduEditCommand && window.cceduEditCommand('paste'," + new com.google.gson.Gson().toJson(Minecraft.getInstance().keyboardHandler.getClipboard()) + ")", EditorOrigin.URL, 0);
            else if (Screen.isSelectAll(key)) browser.executeJavaScript("window.cceduEditCommand && window.cceduEditCommand('selectAll')", EditorOrigin.URL, 0);
        }
        if (browser != null) { browser.sendKeyPress(key,scan,mods); return true; }
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean keyReleased(int key, int scan, int mods) {
        if (browser != null) { browser.sendKeyRelease(key,scan,mods); return true; } return false;
    }
    @Override public boolean charTyped(char c, int mods) {
        if (browser == null) return false;
        if (c < 128) { browser.sendKeyTyped(c,mods); return true; }
        // The pinned JCEF macOS CHAR path maps text through a US key table and drops anything non-ASCII,
        // including committed Chinese IME text, so the page inserts it into the focused field instead.
        if (Character.isHighSurrogate(c)) { pendingHighSurrogate = c; return true; }
        String text = pendingHighSurrogate != 0 && Character.isLowSurrogate(c) ? new String(new char[]{pendingHighSurrogate, c}) : String.valueOf(c);
        pendingHighSurrogate = 0;
        if (ready && error == null) browser.executeJavaScript("window.cceduInsertText && window.cceduInsertText(" + new com.google.gson.Gson().toJson(text) + ")", EditorOrigin.URL, 0);
        return true;
    }
}
