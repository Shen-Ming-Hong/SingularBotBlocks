package org.singularbots.compat.cct;

import dan200.computercraft.shared.platform.RegistryEntry;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.items.TurtleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class TeachingContent {
    public static boolean isTurtle(net.minecraft.world.level.block.Block block){return block instanceof dan200.computercraft.shared.turtle.blocks.TurtleBlock;}
    public static final String MOD="singularbots";
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MOD);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MOD);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MOD);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MOD);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MOD);
    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_SERIALIZER,MOD);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<JuniorConversionRecipe>> CONVERSION=
        RECIPES.register("junior_conversion",()->new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(JuniorConversionRecipe::new));
    public static final DeferredHolder<net.minecraft.world.level.block.Block,JuniorTurtleBlock> JUNIOR=BLOCKS.register("turtle_junior",
        () -> new JuniorTurtleBlock(BlockBehaviour.Properties.of().strength(2.5f),new RegistryEntry<>() {
            public BlockEntityType<TurtleBlockEntity> get() { return JUNIOR_ENTITY.get(); }
            public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath(MOD,"turtle_junior"); }
        }));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<TurtleBlockEntity>> JUNIOR_ENTITY=ENTITIES.register("turtle_junior",
        () -> BlockEntityType.Builder.<TurtleBlockEntity>of(JuniorTurtleEntity::new,JUNIOR.get()).build(null));
    public static final DeferredHolder<Item,TurtleItem> JUNIOR_ITEM=ITEMS.register("turtle_junior",() -> new TurtleItem(JUNIOR.get(),new Item.Properties().stacksTo(64)));
    public static final DeferredHolder<Item,RemoteItem> REMOTE=ITEMS.register("remote",() -> new RemoteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<net.minecraft.world.level.block.Block,PermissionBlock> BUILD_ALLOW=permission("build_allow",PermissionBlock.Rule.ALLOW_BUILD);
    public static final DeferredHolder<net.minecraft.world.level.block.Block,PermissionBlock> BUILD_DENY=permission("build_deny",PermissionBlock.Rule.DENY_BUILD);
    public static final DeferredHolder<net.minecraft.world.level.block.Block,PermissionBlock> BORDER=permission("turtle_border",PermissionBlock.Rule.BORDER);
    public static final DeferredHolder<net.minecraft.world.level.block.Block,PermissionBlock> ANTI_BORDER=permission("turtle_anti_border",PermissionBlock.Rule.ANTI_BORDER);
    private static DeferredHolder<net.minecraft.world.level.block.Block,PermissionBlock> permission(String name,PermissionBlock.Rule rule){var block=BLOCKS.register(name,()->new PermissionBlock(rule));ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties()));return block;}
    public static final DeferredHolder<MenuType<?>,MenuType<RemoteMenu>> REMOTE_MENU=MENUS.register("remote",
        () -> IMenuTypeExtension.create((id,inv,buf)->new RemoteMenu(id,inv,buf.readUUID())));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TEACHING_TAB=TABS.register("teaching",()->CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.singularbots"))
        .icon(()->new ItemStack(JUNIOR_ITEM.get()))
        .withTabsBefore(ResourceLocation.fromNamespaceAndPath("computercraft","tab"))
        .displayItems((parameters,output)->{
            output.accept(JuniorMining.item(parameters.holders()));
        }).build());
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ENTITIES.register(bus); ITEMS.register(bus); TABS.register(bus); MENUS.register(bus); RECIPES.register(bus);
    }
}
