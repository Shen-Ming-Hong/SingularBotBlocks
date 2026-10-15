package org.singularbots.compat.cct;

import net.minecraft.world.level.block.Block;

public final class PermissionBlock extends Block {
    public enum Rule { ALLOW_BUILD,DENY_BUILD,BORDER,ANTI_BORDER }
    public final Rule rule;
    public PermissionBlock(Rule rule){super(Properties.of().strength(-1.0f,3600000.0f));this.rule=rule;}
}
