package org.singularbots.mixinplugin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Picks the robot body renderer hook that matches the installed CC: Tweaked. CC 1.121 changed the renderer's tint argument
 * from {@code int[]} to {@code IntUnaryOperator}; the two hooks are otherwise identical. Every other mixin always applies.
 */
public final class CcCompatPlugin implements IMixinConfigPlugin {
    private static final String RENDERER = "dan200.computercraft.client.render.TurtleBlockEntityRenderer";
    private static final String OPERATOR_TINTS = "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;Ljava/util/function/IntUnaryOperator;)V";
    private Boolean operatorTints;

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".JuniorBodyRendererMixin")) return !operatorTints(targetClassName);
        if (mixinClassName.endsWith(".JuniorBodyRendererMixin121")) return operatorTints(targetClassName);
        return true;
    }

    private boolean operatorTints(String targetClassName) {
        if (operatorTints == null) operatorTints = usesOperatorTints(targetClassName);
        return operatorTints;
    }

    /** Reads the renderer's bytecode without loading it; on any failure falls back to the 1.120 hook. */
    static boolean usesOperatorTints(String targetClassName) {
        if (!RENDERER.equals(targetClassName)) return false;
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(RENDERER.replace('.', '/'));
            return hasOperatorTints(node);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean hasOperatorTints(ClassNode node) {
        return node.methods.stream().anyMatch(m -> isOperatorTintRender(m.name, m.desc));
    }

    /** True for CC 1.121's {@code renderModel(..., BakedModel, IntUnaryOperator)}. */
    static boolean isOperatorTintRender(String name, String desc) {
        return "renderModel".equals(name) && OPERATOR_TINTS.equals(desc);
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
