package org.mcvote.server.menu.layout;

import java.util.List;

public record MenuLayout(
        boolean enabled,
        String title,
        int rows,
        List<MenuIcon> icons,
        LinksFill links,
        TiersFill tiers
) {
}
