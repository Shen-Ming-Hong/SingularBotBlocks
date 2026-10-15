package org.singularbots.turtle;

import java.util.UUID;

/** The same server policy is used by controls, queued commands and inventory access. */
public final class AccessPolicy {
    private AccessPolicy() { }
    public static boolean permits(UUID player, UUID owner, boolean teacher, boolean locked,
                                  boolean sameWorld, boolean loaded, double distanceSquared) {
        return sameWorld && loaded && distanceSquared <= 64 * 64 && distanceSquared >= 0
            && (teacher || (player.equals(owner) && !locked));
    }
}
