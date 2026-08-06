package org.mcvote.server.menu.layout;

import java.util.List;

public record MenuIcon(
        int slot,
        String material,
        String name,
        List<String> lore,
        List<MenuAction> actions,
        String permission
) {
}
