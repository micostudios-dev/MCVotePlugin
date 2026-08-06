package org.mcvote.server.menu.layout;

import java.util.List;

public record LinksFill(
        boolean enabled,
        int startSlot,
        String material,
        String name,
        List<String> lore,
        String clickMessage
) {
}
