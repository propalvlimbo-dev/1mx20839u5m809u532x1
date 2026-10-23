package ru.elytrix.dungeons.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonTemplate;
import ru.elytrix.dungeons.structure.SpongeSchematicCodec;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonCommand implements TabExecutor {

    private final ElytrixDungeonsPlugin plugin;
    private final Map<UUID, BlockPosKey> pos1Map = new HashMap<>();
    private final Map<UUID, BlockPosKey> pos2Map = new HashMap<>();

    public DungeonCommand(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setPos1(Player p, BlockPosKey pos) { pos1Map.put(p.getUniqueId(), pos); }
    public void setPos2(Player p, BlockPosKey pos) { pos2Map.put(p.getUniqueId(), pos); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.equals("dungeons") || lower.equals("dungeon") || lower.equals("danzh")) {
            if (sender instanceof Player p) {
                plugin.getMenuService().openPlayerOverview(p);
            }
            return true;
        }

        if (!sender.hasPermission("elytrixdungeons.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("admin")) {
            if (sender instanceof Player p) {
                plugin.getMenuService().openAdminBoard(p);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(plugin.prefix() + "Активные данжи (§f" + plugin.getDungeonManager().getAllDungeons().size() + "§7):");
                for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                    BlockPosKey a = d.getHologramAnchor();
                    sender.sendMessage(TextUtil.color(" &#F8BEFB● &f" + d.getId()
                            + " &8[" + d.getCategory().getColorHex() + d.getCategory().getTitle() + "&8] &7— &f"
                            + a.x() + ", " + a.y() + ", " + a.z() + " &8(&7" + TextUtil.formatDuration(d.secondsUntilReset()) + "&8)"));
                }
            }
            case "tp" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "/edungeon tp <id>");
                    return true;
                }
                DungeonInstance d = plugin.getDungeonManager().getDungeon(args[1]);
                if (d == null) {
                    sender.sendMessage(plugin.prefix() + "§cДанж не найден.");
                    return true;
                }
                Location loc = d.teleportLocation();
                if (loc != null) p.teleport(loc);
            }
            case "regen" -> {
                String target = args.length >= 2 ? args[1] : "all";
                if (target.equalsIgnoreCase("all")) {
                    for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                        plugin.getDungeonManager().regenerateDungeon(d, false);
                    }
                    sender.sendMessage(plugin.msg("broadcast-all-reset"));
                } else {
                    DungeonInstance d = plugin.getDungeonManager().getDungeon(target);
                    if (d != null) {
                        plugin.getDungeonManager().regenerateDungeon(d, false);
                        sender.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7обновлён.");
                    }
                }
            }
            case "relocate" -> {
                String target = args.length >= 2 ? args[1] : "all";
                if (target.equalsIgnoreCase("all")) {
                    for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                        plugin.getDungeonManager().regenerateDungeon(d, true);
                    }
                    sender.sendMessage(plugin.prefix() + "Все данжи откатили рельеф и перенесены на новые координаты!");
                } else {
                    DungeonInstance d = plugin.getDungeonManager().getDungeon(target);
                    if (d != null) {
                        plugin.getDungeonManager().regenerateDungeon(d, true);
                        sender.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7перенесён.");
                    }
                }
            }
            case "rebuildall" -> {
                plugin.getDungeonManager().rebuildAllFromSchematics();
                sender.sendMessage(plugin.prefix() + "Старые 15 данжей удалены с возвратом ландшафта, запущен спавн 5 / 5 / 5 из папок schematics/!");
            }
            case "loot" -> {
                if (sender instanceof Player p) plugin.getMenuService().openLootPoolSelector(p);
            }
            case "wand" -> {
                if (sender instanceof Player p) {
                    ItemStack wand = new ItemStack(Material.BLAZE_ROD);
                    ItemMeta meta = wand.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(TextUtil.color("&#F8BEFBЖезл Схемы Данжа"));
                        meta.setLore(TextUtil.colorList(List.of("&7● &fЛКМ — Точка 1", "&7● &fПКМ — Точка 2")));
                        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                        meta.getPersistentDataContainer().set(plugin.getWandPdc(), PersistentDataType.BYTE, (byte) 1);
                        wand.setItemMeta(meta);
                    }
                    p.getInventory().addItem(wand);
                    p.sendMessage(plugin.prefix() + "Выдан жезл выделения для сохранения .schem.");
                }
            }
            case "save" -> {
                if (!(sender instanceof Player p) || args.length < 3) {
                    sender.sendMessage(plugin.prefix() + "/edungeon save <имя> <SURFACE|UNDERGROUND|SKY>");
                    return true;
                }
                exportSelectionToSchem(p, args[1], DungeonCategory.valueOf(args[2].toUpperCase(Locale.ROOT)));
            }
            case "spawn", "paste" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "/edungeon spawn <id_схемы>");
                    return true;
                }
                DungeonTemplate tpl = plugin.getDungeonManager().getTemplate(args[1]);
                if (tpl != null) {
                    plugin.getDungeonManager().manualSpawnHere(p, tpl);
                    sender.sendMessage(plugin.prefix() + "Схема поставлена!");
                }
            }
            case "remove" -> {
                if (args.length >= 2 && plugin.getDungeonManager().removeDungeon(args[1], true)) {
                    sender.sendMessage(plugin.prefix() + "Данж удалён, природный рельеф возвращён.");
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> sender.sendMessage(plugin.prefix() + "/edungeon <gui|loot|tp|list|regen|relocate|rebuildall|wand|save|spawn|remove|reload>");
        }
        return true;
    }

    private void exportSelectionToSchem(Player p, String rawName, DungeonCategory cat) {
        BlockPosKey p1 = pos1Map.get(p.getUniqueId());
        BlockPosKey p2 = pos2Map.get(p.getUniqueId());
        CuboidRegion box = (p1 != null && p2 != null)
                ? new CuboidRegion(p1.worldName(), p1.x(), p1.y(), p1.z(), p2.x(), p2.y(), p2.z()).normalized()
                : plugin.getWorldEditHook().getPlayerSelection(p);
        if (box == null) {
            p.sendMessage(plugin.prefix() + "§cСначала выделите область (/edungeon wand или //pos1 //pos2)!");
            return;
        }
        World w = p.getWorld();
        DungeonTemplate tpl = new DungeonTemplate(rawName.toLowerCase(Locale.ROOT), cat.getTitle() + " данж",
                cat, box.sizeX(), box.sizeY(), box.sizeZ());
        for (int y = 0; y < box.sizeY(); y++) {
            for (int z = 0; z < box.sizeZ(); z++) {
                for (int x = 0; x < box.sizeX(); x++) {
                    tpl.setBlock(x, y, z, w.getBlockAt(box.minX() + x, box.minY() + y, box.minZ() + z).getBlockData().getAsString(true));
                }
            }
        }
        File out = new File(plugin.getDungeonManager().categorySchemDir(cat), rawName.toLowerCase(Locale.ROOT) + ".schem");
        try {
            SpongeSchematicCodec.writeSchem(tpl, out);
            plugin.getDungeonManager().loadSchematics();
            p.sendMessage(plugin.prefix() + "Постройка сохранена в §fschematics/" + cat.name().toLowerCase(Locale.ROOT) + "/" + out.getName() + "§7!");
        } catch (IOException ex) {
            p.sendMessage(plugin.prefix() + "§cОшибка записи .schem: " + ex.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("elytrixdungeons.admin")) return List.of();
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0],
                    List.of("gui", "loot", "list", "tp", "regen", "relocate", "rebuildall", "wand", "save", "spawn", "remove", "reload"),
                    new ArrayList<>());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("tp") || sub.equals("remove") || sub.equals("regen") || sub.equals("relocate")) {
                List<String> ids = new ArrayList<>();
                if (sub.equals("regen") || sub.equals("relocate")) ids.add("all");
                for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) ids.add(d.getId());
                return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>());
            }
            if (sub.equals("spawn") || sub.equals("paste")) {
                return StringUtil.copyPartialMatches(args[1], plugin.getDungeonManager().getTemplates().keySet(), new ArrayList<>());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("save")) {
            return StringUtil.copyPartialMatches(args[2], List.of("SURFACE", "UNDERGROUND", "SKY"), new ArrayList<>());
        }
        return List.of();
    }
}
