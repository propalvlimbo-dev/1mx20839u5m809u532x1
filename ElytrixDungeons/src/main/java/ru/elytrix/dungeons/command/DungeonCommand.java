package ru.elytrix.dungeons.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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

    private static final List<String> SUBCOMMANDS = List.of(
            "gui", "tp", "list", "status", "findspots", "regen", "relocate",
            "hide", "show", "protect", "loot", "giveshard", "wand", "save", "spawn", "remove", "reload"
    );

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
                if (args.length >= 2 && args[0].equalsIgnoreCase("tp")) {
                    if (!plugin.getMenuService().canTeleportFromPlayerMenu(p)) {
                        p.sendMessage(plugin.msg("no-permission"));
                        return true;
                    }
                    teleportToDungeon(p, args[1]);
                    return true;
                }
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
            case "tp" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length < 2) {
                    p.sendMessage(plugin.prefix() + "/edungeon tp <surface_1..surface_7 | underground_1..underground_7>");
                    return true;
                }
                teleportToDungeon(p, args[1]);
            }
            case "list" -> sendClickableDungeonList(sender);
            case "status", "info" -> {
                sender.sendMessage(plugin.prefix() + "Статус ElytrixDungeons:");
                sender.sendMessage(TextUtil.color(" &#F8BEFB● &fНаземных (Домов): &#96FF91"
                        + plugin.getDungeonManager().countByCategory(DungeonCategory.SURFACE) + "/7"
                        + " &8┃ &fПодземных: &#89A0FF" + plugin.getDungeonManager().countByCategory(DungeonCategory.UNDERGROUND) + "/7"));
                sender.sendMessage(TextUtil.color(" &#F8BEFB● &fЗагружено .schem файлов: &#F8BEFB"
                        + plugin.getDungeonManager().getTemplates().keySet()));
                sender.sendMessage(TextUtil.color(" &#F8BEFB● &fПоиск мест: &#F8BEFB"
                        + (plugin.getDungeonManager().isScanning()
                                ? plugin.getDungeonManager().getPendingScanDone() + "/" + plugin.getDungeonManager().getPendingScanTotal()
                                : "завершён (зафиксированы в dungeons.yml)")));
                sender.sendMessage(TextUtil.color(" &#F8BEFB● &fВидимость в мире: "
                        + (plugin.getDungeonManager().isHiddenFromWorld() ? "&cскрыты" : "&#96FF91активны")));
            }
            case "findspots", "rebuildall" -> {
                plugin.getDungeonManager().rebuildAllFromSchematics();
                sender.sendMessage(plugin.prefix() + "Запущена прогрузка чанков и поиск 14 идеальных мест (7 Наземных [4+3] + 7 Подземных [4+3])!");
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
                        sender.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7и рельеф вокруг него полностью восстановлены!");
                    }
                }
            }
            case "relocate" -> {
                String target = args.length >= 2 ? args[1] : "all";
                if (target.equalsIgnoreCase("all")) {
                    for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                        plugin.getDungeonManager().regenerateDungeon(d, true);
                    }
                    sender.sendMessage(plugin.prefix() + "Все данжи откатили ландшафт и перенесены на новые координаты!");
                } else {
                    DungeonInstance d = plugin.getDungeonManager().getDungeon(target);
                    if (d != null) {
                        plugin.getDungeonManager().regenerateDungeon(d, true);
                        sender.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7перенесён.");
                    }
                }
            }
            case "hide" -> {
                plugin.getDungeonManager().hideAllDungeonsNow();
                sender.sendMessage(plugin.prefix() + "Все 14 данжей скрыты с карты (вернулся чистый ландшафт, координаты сохранены).");
            }
            case "show" -> {
                plugin.getDungeonManager().showAllDungeonsNow();
                sender.sendMessage(plugin.prefix() + "Все 14 данжей возвращаются на сохранённые места!");
            }
            case "protect" -> {
                boolean next = !plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true);
                plugin.getConfig().set("protection.allow-block-modify-in-zone", next);
                plugin.saveConfig();
                plugin.getDungeonManager().syncAllWorldGuardRegions();
                sender.sendMessage(plugin.prefix() + "Ломание блоков внутри данжей: "
                        + (next ? "§aРазрешено §7(восстанавливается вокруг +12 блоков)" : "§cЗапрещено"));
            }
            case "loot" -> {
                if (sender instanceof Player p) plugin.getMenuService().openLootPoolSelector(p);
            }
            case "giveshard" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                int amount = 1;
                if (args.length >= 3) {
                    try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) {}
                } else if (args.length == 2 && target == null && sender instanceof Player p) {
                    target = p;
                    try { amount = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
                }
                if (target == null) {
                    sender.sendMessage(plugin.prefix() + "/edungeon giveshard <игрок> [кол-во]");
                    return true;
                }
                target.getInventory().addItem(plugin.getItemsHook().createGhostShard(amount));
                sender.sendMessage(plugin.prefix() + "Выдано §f" + amount + "× Призрачный осколок §7игроку §f" + target.getName());
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
                    sender.sendMessage(plugin.prefix() + "/edungeon save <имя> <surface|underground>");
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
            default -> sender.sendMessage(plugin.prefix()
                    + "/edungeon <gui|tp|list|status|findspots|regen|relocate|hide|show|protect|loot|giveshard|wand|save|spawn|remove|reload>");
        }
        return true;
    }

    private void teleportToDungeon(Player p, String id) {
        DungeonInstance d = plugin.getDungeonManager().getDungeon(id);
        if (d == null) {
            p.sendMessage(plugin.prefix() + "§cДанж '" + id + "' не найден. Список: /edungeon list");
            return;
        }
        Location loc = d.teleportLocation();
        if (loc != null) {
            p.teleport(loc);
            p.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.15f);
            p.sendMessage(plugin.prefix() + "Телепорт в §f" + d.getId() + " §8(§7" + d.getTemplateId() + ".schem§8)");
        }
    }

    private void sendClickableDungeonList(CommandSender sender) {
        sender.sendMessage(plugin.prefix() + "Активные данжи (§f" + plugin.getDungeonManager().getAllDungeons().size() + "/14§7):");
        for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
            BlockPosKey a = d.getHologramAnchor();
            String line = TextUtil.color(" &#F8BEFB● &f" + d.getId()
                    + " &8[" + d.getCategory().getColorHex() + d.getTemplateId() + "&8] &7— &f"
                    + a.x() + ", " + a.y() + ", " + a.z() + " ");
            if (sender instanceof Player p) {
                TextComponent root = new TextComponent("");
                for (BaseComponent bc : TextComponent.fromLegacyText(line)) root.addExtra(bc);
                TextComponent btn = new TextComponent(TextUtil.color("&#96FF91[ТП]"));
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/edungeon tp " + d.getId()));
                root.addExtra(btn);
                p.spigot().sendMessage(ChatMessageType.CHAT, root);
            } else {
                sender.sendMessage(line);
            }
        }
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
        String title = cat == DungeonCategory.SURFACE ? "Дом призраков" : "Подземный данж";
        DungeonTemplate tpl = new DungeonTemplate(rawName.toLowerCase(Locale.ROOT), title,
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
            p.sendMessage(plugin.prefix() + "Схема сохранена в §fschematics/" + cat.name().toLowerCase(Locale.ROOT) + "/" + out.getName() + "§7!");
        } catch (IOException ex) {
            p.sendMessage(plugin.prefix() + "§cОшибка записи .schem: " + ex.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String lower = alias.toLowerCase(Locale.ROOT);
        if (lower.equals("dungeons") || lower.equals("dungeon") || lower.equals("danzh")) {
            if (args.length == 1) return StringUtil.copyPartialMatches(args[0], List.of("tp"), new ArrayList<>());
            if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
                List<String> ids = new ArrayList<>();
                for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) ids.add(d.getId());
                return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>());
            }
            return List.of();
        }

        if (!sender.hasPermission("elytrixdungeons.admin")) return List.of();
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
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
            if (sub.equals("giveshard")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("save")) {
            return StringUtil.copyPartialMatches(args[2], List.of("SURFACE", "UNDERGROUND"), new ArrayList<>());
        }
        return List.of();
    }
}
