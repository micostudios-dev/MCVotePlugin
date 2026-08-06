package org.mcvote.server.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public final class Menu {

    private final String legacyTitle;
    private final int size;
    private final Map<Integer, MenuButton> buttons = new HashMap<>();

    public Menu(String legacyTitle, int rows) {
        this.legacyTitle = legacyTitle;
        this.size = Math.max(9, Math.min(54, rows * 9));
    }

    public Menu set(int slot, MenuButton button) {
        if (slot >= 0 && slot < size) {
            buttons.put(slot, button);
        }

        return this;
    }

    public MenuButton button(int slot) {
        return buttons.get(slot);
    }

    public void open(Player player) {
        MenuHolder holder = new MenuHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, size, legacyTitle);
        holder.setInventory(inventory);
        buttons.forEach((slot, button) -> inventory.setItem(slot, button.icon()));
        player.openInventory(inventory);
    }
}
