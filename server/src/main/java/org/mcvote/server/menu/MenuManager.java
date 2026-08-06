package org.mcvote.server.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuManager implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        if (!(holder instanceof MenuHolder menuHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() != inventory || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        MenuButton button = menuHolder.menu().button(event.getRawSlot());
        if (button != null) {
            button.click(player);
        }
    }
}
