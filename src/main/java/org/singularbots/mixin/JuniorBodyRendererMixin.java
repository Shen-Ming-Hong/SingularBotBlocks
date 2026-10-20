package org.singularbots.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.render.TurtleBlockEntityRenderer;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.singularbots.compat.cct.JuniorTurtleEntity;
import org.singularbots.compat.cct.client.AnimatedSprites;
import org.singularbots.compat.cct.client.JuniorBodyModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Client-only CC 1.120.2 hook; other turtles, labels, animation and upgrades are unchanged. */
@Mixin(value = TurtleBlockEntityRenderer.class, remap = false)
public abstract class JuniorBodyRendererMixin {
    @Shadow
    private void renderModel(PoseStack transform, MultiBufferSource buffers, int lightmapCoord, int overlayLight,
            ResourceLocation modelLocation, int[] tints) { }

    @Shadow
    private void renderModel(PoseStack transform, MultiBufferSource buffers, int lightmapCoord, int overlayLight,
            BakedModel model, int[] tints) { }

    @ModifyArgs(method = "render(Ldan200/computercraft/shared/turtle/blocks/TurtleBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE", target = "Ldan200/computercraft/client/render/TurtleBlockEntityRenderer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/resources/ResourceLocation;[I)V"))
    private void eduBody(Args args, TurtleBlockEntity turtle, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlayLight) {
        if (!(turtle instanceof JuniorTurtleEntity)
                || !TurtleBlockEntityRenderer.COLOUR_TURTLE_MODEL.equals(args.get(4))) return;
        renderEdges(args.get(0), buffers, overlayLight, (JuniorTurtleEntity) turtle);
        int index = JuniorBodyModels.index(turtle.getColour());
        if (index < 0) return;
        drawn(JuniorBodyModels.model(index));
        args.set(4, JuniorBodyModels.model(index));
        args.set(5, null); // Original PNGs already contain the colour and have no tint indices.
    }

    /** Every body colour, the tinted fallback included, wears the glowing edge signal. */
    private void renderEdges(PoseStack transform, MultiBufferSource buffers, int overlayLight, JuniorTurtleEntity turtle) {
        var edges = JuniorBodyModels.edges(turtle.signal());
        drawn(edges);
        renderModel(transform, buffers, LightTexture.FULL_BRIGHT, overlayLight, edges, null);
    }

    /** CC's renderer bypasses the paths where Embeddium/Sodium notice animated textures; keep the edge light and eyes moving. */
    private static void drawn(ResourceLocation model) {
        AnimatedSprites.drawn(Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(model)));
    }

    /** CC draws an uncoloured turtle with its item model; a junior robot without a colour is the default white body. */
    @Redirect(method = "render(Ldan200/computercraft/shared/turtle/blocks/TurtleBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE", target = "Ldan200/computercraft/client/render/TurtleBlockEntityRenderer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;[I)V"))
    private void eduUncolouredBody(TurtleBlockEntityRenderer self, PoseStack transform, MultiBufferSource buffers,
            int light, int overlayLight, BakedModel model, int[] tints, TurtleBlockEntity turtle, float partialTicks,
            PoseStack pose, MultiBufferSource outerBuffers, int outerLight, int outerOverlay) {
        if (!(turtle instanceof JuniorTurtleEntity)) {
            renderModel(transform, buffers, light, overlayLight, model, tints);
            return;
        }
        drawn(JuniorBodyModels.model(0));
        renderModel(transform, buffers, light, overlayLight, JuniorBodyModels.model(0), null);
        renderEdges(transform, buffers, overlayLight, (JuniorTurtleEntity) turtle);
    }
}
