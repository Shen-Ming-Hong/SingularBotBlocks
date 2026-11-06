package org.singularbots.turtle;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import java.util.Collections;

/**
 * How far a crop in front of (or above / below) a robot has grown. The last stage is read from the block itself, so
 * wheat (7), sweet berries and Ars Nouveau's sourceberry bush (3), nether wart (3) and modded crops all work without a
 * list of numbers: a {@link CropBlock} reports its own age, any other block uses its integer {@code age} property.
 */
public final class CropGrowth {
    private CropGrowth() { }
    /** The growth stage a robot reports when the block there does not grow in stages. */
    public static final int NOT_A_CROP = -1;
    public record Stage(int age, int max) {
        public boolean grown() { return age >= max; }
    }
    /** The block's growth stage, or {@code null} when it does not grow in stages (air, stone, a chest…). */
    public static Stage of(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return new Stage(crop.getAge(state), crop.getMaxAge());
        for (var property : state.getProperties())
            if (property instanceof IntegerProperty age && "age".equals(age.getName()))
                return new Stage(state.getValue(age), Collections.max(age.getPossibleValues()));
        return null;
    }
    public static boolean grown(BlockState state) { var stage = of(state); return stage != null && stage.grown(); }
    public static int age(BlockState state) { var stage = of(state); return stage == null ? NOT_A_CROP : stage.age(); }
}
