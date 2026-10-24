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
import ru.elytrix.dungeons.service.DungeonManager;
import ru.elytrix.dungeons.structure.SpongeSchematicCodec;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "gui", "create", "tp", "list", "status", "findspots", "regen", "relocate",
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
                String scanStatus;
                if (plugin.getDungeonManager().isScanning()) {
                    scanStatus = plugin.getDungeonManager().getPendingScanDone() + "/" + plugin.getDungeonManager().getPendingScanTotal();
                } else if (plugin.getDungeonManager().isPlacementInProgress()) {
                    scanStatus = "устанавливается данж, дождитесь завершения";
                } else if (plugin.getDungeonManager().getAllDungeons().isEmpty()) {
                    scanStatus = "ожидает /elytrixdungeon create <surface|underground> <id-схемы>";
                } else if (plugin.getDungeonManager().getAllDungeons().size() < 14) {
                    scanStatus = "создано вручную: " + plugin.getDungeonManager().getAllDungeons().size() + "/14";
                } else {
                    scanStatus = "завершён (места сохранены в dungeons.yml)";
                }
                sender.sendMessage(TextUtil.color(" &#F8BEFB● &fПоиск мест: &#F8BEFB" + scanStatus));
                sender.sendMessage(TextUtil.color(" &#F8BEFB● &fВидимость в мире: "
                        + (plugin.getDungeonManager().isHiddenFromWorld() ? "&cскрыты" : "&#96FF91активны")));
            }
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "/elytrixdungeon create <surface|underground> <id-схемы>");
                    return true;
                }
                String templateId = args.length >= 3 ? args[2] : null;
                sendCreateResult(sender, plugin.getDungeonManager().createDungeonFromCommand(args[1], templateId));
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
                    DungeonInstance created = plugin.getDungeonManager().manualSpawnHere(p, tpl);
                    if (created != null) {
                        sender.sendMessage(plugin.prefix() + "Схема поставлена как данж §f" + created.getId() + "§7.");
                    } else if (plugin.getDungeonManager().countByCategory(tpl.getCategory()) >= DungeonManager.MAX_DUNGEONS_PER_CATEGORY) {
                        sender.sendMessage(plugin.prefix() + "§cДля типа «" + tpl.getCategory().getTitle() + "» максимум — 7 данжей.");
                    } else {
                        sender.sendMessage(plugin.prefix() + "§cНе удалось создать данж здесь: место занято, защищено или идёт поиск мест.");
                    }
                } else {
                    sender.sendMessage(plugin.prefix() + "§cСхема не найдена. Проверьте ID в /edungeon status.");
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "/elytrixdungeon remove <id-данжа>");
                    return true;
                }
                if (plugin.getDungeonManager().removeDungeon(args[1], true)) {
                    sender.sendMessage(plugin.prefix() + "Данж удалён, природный рельеф возвращён.");
                } else {
                    sender.sendMessage(plugin.prefix() + "§cНе удалось удалить данж: он не найден, идёт поиск/установка/обновление или не удалось восстановить снимок рельефа.");
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> sender.sendMessage(plugin.prefix()
                    + "/elytrixdungeon <create|gui|tp|list|status|findspots|regen|relocate|hide|show|protect|loot|giveshard|wand|save|spawn|remove|reload>");
        }
        return true;
    }

    private void sendCreateResult(CommandSender sender, DungeonManager.CreateResult result) {
        DungeonCategory category = result.category();
        String typeName = category == null ? "данжа" : category.getTitle();
        String available = availableSchematics(category);
        switch (result.status()) {
            case CREATED -> {
                DungeonInstance dungeon = result.dungeon();
                BlockPosKey origin = dungeon.getOrigin();
                sender.sendMessage(plugin.prefix() + "Устанавливается данж §f" + dungeon.getId() + " §7("
                        + dungeon.getCategory().getTitle() + ", схема " + dungeon.getTemplateId() + ").");
                sender.sendMessage(TextUtil.color(" &#F8BEFB&l┃ &fКоординаты: &#F8BEFB" + origin.x() + ", " + origin.y() + ", " + origin.z()
                        + " &8┃ &fСоздано этого типа: &#F8BEFB" + result.count() + "/" + DungeonManager.MAX_DUNGEONS_PER_CATEGORY));
            }
            case MAX_REACHED -> sender.sendMessage(plugin.prefix() + "§cДля типа «" + typeName + "» уже создано максимально допустимое количество данжей: "
                    + result.count() + "/" + DungeonManager.MAX_DUNGEONS_PER_CATEGORY + ".");
            case INVALID_ID -> sender.sendMessage(plugin.prefix()
                    + "§cУкажите тип surface или underground (либо конкретный ID данжа), затем ID схемы: "
                    + "/elytrixdungeon create <surface|underground> <id-схемы>.");
            case ID_ALREADY_EXISTS -> sender.sendMessage(plugin.prefix() + "§cДанж с таким ID уже существует.");
            case MISSING_SCHEMATIC -> sender.sendMessage(plugin.prefix() + "§cДля типа «" + typeName + "» пока нет загруженных схем. "
                    + "Поместите .schem в plugins/ElytrixDungeons/schematics/" + category.name().toLowerCase(Locale.ROOT) + "/.");
            case TEMPLATE_REQUIRED -> sender.sendMessage(plugin.prefix() + "§eУкажите ID загруженной схемы. Доступные схемы для «" + typeName + "»: §f" + available);
            case INVALID_TEMPLATE -> sender.sendMessage(plugin.prefix() + "§cСхема не найдена. Доступные схемы для «" + typeName + "»: §f" + available);
            case TEMPLATE_CATEGORY_MISMATCH -> sender.sendMessage(plugin.prefix() + "§cЭта схема относится к другому типу данжа. Доступные схемы для «" + typeName + "»: §f" + available);
            case WORLD_NOT_FOUND -> sender.sendMessage(plugin.prefix() + "§cМир для создания данжей не найден. Проверьте world-settings.world в config.yml.");
            case NO_SAFE_SPOT -> sender.sendMessage(plugin.prefix() + "§cНе удалось найти безопасное место. Проверьте размер схемы и границы поиска в config.yml.");
            case SNAPSHOT_FAILED -> sender.sendMessage(plugin.prefix() + "§cНе удалось сохранить исходный ландшафт. Данж не создан; проверьте права записи и свободное место.");
            case SCAN_IN_PROGRESS -> sender.sendMessage(plugin.prefix() + "§cСейчас идёт поиск места или установка другого данжа. Дождитесь завершения.");
        }
    }

    private String availableSchematics(DungeonCategory category) {
        if (category == null) return "нет загруженных схем";
        List<String> ids = new ArrayList<>();
        for (DungeonTemplate template : plugin.getDungeonManager().getTemplates(category)) {
            ids.add(template.getId());
        }
        return ids.isEmpty() ? "нет загруженных схем" : String.join(", ", ids);
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
            BlockPosKey a = d.getEntrancePos() != null ? d.getEntrancePos() : d.getOrigin();
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
            if (sub.equals("create")) {
                List<String> ids = new ArrayList<>(List.of("surface", "underground"));
                for (DungeonCategory category : DungeonCategory.values()) {
                    String prefix = category.name().toLowerCase(Locale.ROOT) + "_";
                    for (int number = 1; number <= DungeonManager.MAX_DUNGEONS_PER_CATEGORY; number++) {
                        String id = prefix + number;
                        if (plugin.getDungeonManager().getDungeon(id) == null) ids.add(id);
                    }
                }
                return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>());
            }
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
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            DungeonCategory category = categoryForCreateTarget(args[1]);
            if (category == null) return List.of();
            List<String> schematicIds = new ArrayList<>();
            for (DungeonTemplate template : plugin.getDungeonManager().getTemplates(category)) {
                schematicIds.add(template.getId());
            }
            return StringUtil.copyPartialMatches(args[2], schematicIds, new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("save")) {
            return StringUtil.copyPartialMatches(args[2], List.of("SURFACE", "UNDERGROUND"), new ArrayList<>());
        }
        return List.of();
    }

    private static DungeonCategory categoryForCreateTarget(String target) {
        if (target == null) return null;
        String input = target.toLowerCase(Locale.ROOT);
        if (input.equals("surface")) return DungeonCategory.SURFACE;
        if (input.equals("underground")) return DungeonCategory.UNDERGROUND;
        int separator = input.indexOf('_');
        if (separator <= 0) return null;
        String suffix = input.substring(separator + 1);
        if (suffix.isEmpty()) return null;
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return null;
        }
        return switch (input.substring(0, separator)) {
            case "surface" -> DungeonCategory.SURFACE;
            case "underground" -> DungeonCategory.UNDERGROUND;
            default -> null;
        };
    }
}
