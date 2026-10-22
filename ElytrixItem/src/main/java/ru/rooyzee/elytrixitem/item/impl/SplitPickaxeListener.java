package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.hook.WorldGuardHook;
import ru.rooyzee.elytrixitem.util.ColorUtil;

/**
 * Добыча спавнера Киркой Раскола.
 *
 * Спавнер выпадает пустым предметом: моб из него не возвращается.
 * Кирка сразу ломается. В регионах WorldGuard, где игрок не может строить,
 * спавнер не отдаётся.
 */
public final class SplitPickaxeListener implements Listener {

    private final Main plugin;
    private final NamespacedKey key;

    public SplitPickaxeListener(Main plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "custom_item_id");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isSplitPickaxe(tool)) {
            return;
        }

        event.setCancelled(true);

        if (!WorldGuardHook.canBuild(player, block.getLocation())) {
            player.sendMessage(ColorUtil.color("&#F8BEFB&l┃ &fЗдесь нельзя добывать спавнер"));
            return;
        }

        ItemStack drop = createSpawnerItem();
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);

        block.setType(Material.AIR, false);
        world.dropItemNaturally(center, drop);
        world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.0F, 0.8F);

        breakTool(player, tool);
        world.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
        player.sendMessage(ColorUtil.color("&#F8BEFB&l┃ &fСпавнер добыт, &#F8BEFBКирка Раскола &fсломалась"));
        player.sendMessage(ColorUtil.color("&#F8BEFB&l┃ &fМоб из спавнера &#cне возвращается"));
    }

    private boolean isSplitPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_PICKAXE || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String id = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return SplitPickaxe.ID.equals(id);
    }

    /** Пустой спавнер: моб из добытого спавнера не возвращается. */
    private ItemStack createSpawnerItem() {
        return new ItemStack(Material.SPAWNER);
    }

    /** Кирка ломается: из стопки убирается одна штука. */
    private void breakTool(Player player, ItemStack tool) {
        int amount = tool.getAmount() - 1;
        if (amount <= 0) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        tool.setAmount(amount);
        player.getInventory().setItemInMainHand(tool);
    }
}
