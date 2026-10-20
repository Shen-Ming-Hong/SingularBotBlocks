package org.singularbots.mixin;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.singularbots.compat.cct.JuniorMining;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Block.class)
public abstract class JuniorDropsMixin {
    @Redirect(method="popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private static boolean robotDrops(GameRules rules,GameRules.Key<GameRules.BooleanValue> key,Level level,java.util.function.Supplier<net.minecraft.world.entity.item.ItemEntity> item,net.minecraft.world.item.ItemStack stack){
        return rules.getBoolean(key)||(key==GameRules.RULE_DOBLOCKDROPS&&JuniorMining.collecting(level));
    }
}
