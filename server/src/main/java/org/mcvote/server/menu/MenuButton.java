package org.mcvote.server.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public record MenuButton(ItemStack icon, Consumer<Player> onClick) {

    public static MenuButton display(ItemStack icon) {
        return new MenuButton(icon, null);
    }

    public void click(Player player) {
        if (onClick != null) {
            onClick.accept(player);
        }
    }
}
