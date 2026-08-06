package org.mcvote.server.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mcvote.server.platform.MessageService;

import java.util.ArrayList;
import java.util.List;

public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, String name, List<String> lore, MessageService messages) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(messages.toLegacy(name));
            }

            if (lore != null && !lore.isEmpty()) {
                List<String> rendered = new ArrayList<>(lore.size());

                for (String line : lore) {
                    rendered.add(messages.toLegacy(line));
                }

                meta.setLore(rendered);
            }

            item.setItemMeta(meta);
        }

        return item;
    }
}
