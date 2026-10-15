package org.singularbots.compat.cct.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.model.turtle.TurtleModel;
import dan200.computercraft.client.render.TurtleBlockEntityRenderer;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.geometry.*;

import java.util.List;
import java.util.function.Function;

/** Fixed CC 1.120.2 integration: retain its overlays/upgrades while selecting legacy bodies. */
public final class JuniorModelLoader implements IGeometryLoader<JuniorModelLoader.Unbaked> {
    @Override
    public Unbaked read(JsonObject json, JsonDeserializationContext context) {
        return new Unbaked();
    }

    public static final class Unbaked implements IUnbakedGeometry<Unbaked> {
        private static final ResourceLocation FAMILY = ResourceLocation.fromNamespaceAndPath("singularbots", "item/turtle_junior_family");

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> models, IGeometryBakingContext context) {
            models.apply(FAMILY).resolveParents(models);
            for (int i = 0; i < JuniorBodyModels.NAMES.length; i++) {
                models.apply(JuniorBodyModels.model(i)).resolveParents(models);
            }
            models.apply(TurtleBlockEntityRenderer.COLOUR_TURTLE_MODEL).resolveParents(models);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker,
                Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
            var family = baker.bake(FAMILY, state, sprites);
            var colours = new TurtleModel[JuniorBodyModels.NAMES.length];
            for (int i = 0; i < colours.length; i++) {
                colours[i] = new TurtleModel(family, baker.bake(JuniorBodyModels.model(i), state, sprites));
            }
            var fallback = new TurtleModel(family, baker.bake(TurtleBlockEntityRenderer.COLOUR_TURTLE_MODEL, state, sprites));
            return new JuniorModel(family, colours, fallback);
        }
    }

    private static final class JuniorModel extends BakedModelWrapper<BakedModel> {
        private final TurtleModel[] colours;
        private final TurtleModel fallback;

        private JuniorModel(BakedModel family, TurtleModel[] colours, TurtleModel fallback) {
            super(family);
            this.colours = colours;
            this.fallback = fallback;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            originalModel.applyTransform(context, pose, leftHand);
            return this;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            int index = JuniorBodyModels.index(DyedItemColor.getOrDefault(stack, -1));
            return (index < 0 ? fallback : colours[index]).getRenderPasses(stack, fabulous);
        }
    }
}
