package org.singularbots.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.render.TurtleBlockEntityRenderer;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.singularbots.compat.cct.JuniorTurtleEntity;
import org.singularbots.compat.cct.client.JuniorBodyModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Client-only CC 1.120.2 hook; other turtles, labels, animation and upgrades are unchanged. */
@Mixin(value = TurtleBlockEntityRenderer.class, remap = false)
public abstract class JuniorBodyRendererMixin {
    @ModifyArgs(method = "render(Ldan200/computercraft/shared/turtle/blocks/TurtleBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE", target = "Ldan200/computercraft/client/render/TurtleBlockEntityRenderer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/resources/ResourceLocation;[I)V"))
    private void eduBody(Args args, TurtleBlockEntity turtle, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlayLight) {
        if (!(turtle instanceof JuniorTurtleEntity)
                || !TurtleBlockEntityRenderer.COLOUR_TURTLE_MODEL.equals(args.get(4))) return;
        int index = JuniorBodyModels.index(turtle.getColour());
        if (index < 0) return;
        args.set(4, JuniorBodyModels.model(index));
        args.set(5, null); // Original PNGs already contain the colour and have no tint indices.
    }
}
