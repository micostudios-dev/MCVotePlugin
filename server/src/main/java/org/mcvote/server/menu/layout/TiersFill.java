package org.mcvote.server.menu.layout;

import java.util.List;

public record TiersFill(
        boolean enabled,
        int startSlot,
        String unlockedMaterial,
        String lockedMaterial,
        String name,
        List<String> lore,
        String unlockedStatus,
        String lockedStatus
) {
}
