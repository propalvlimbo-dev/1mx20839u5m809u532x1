package ru.elytrix.dungeons.command;

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
import ru.elytrix.dungeons.model.TrialType;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonCommand implements TabExecutor {

    private static final class BuilderSession {
        BlockPosKey pos1;
        BlockPosKey pos2;
        BlockPosKey altar;
        BlockPosKey entrance;
        final List<BlockPosKey> seals = new ArrayList<>();
        final List<BlockPosKey> explorerChests = new ArrayList<>();
        final List<BlockPosKey> rewardChests = new ArrayList<>();
        final List<BlockPosKey> mobSpawns = new ArrayList<>();
    }

    private final ElytrixDungeonsPlugin plugin;
    private final Map<UUID, BuilderSession> sessions = new HashMap<>();

    public DungeonCommand(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setPos1(Player p, BlockPosKey pos) {
        sessions.computeIfAbsent(p.getUniqueId(), k -> new BuilderSession()).pos1 = pos;
    }

    public void setPos2(Player p, BlockPosKey pos) {
        sessions.computeIfAbsent(p.getUniqueId(), k -> new BuilderSession()).pos2 = pos;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        if (lowerLabel.equals("dungeons") || lowerLabel.equals("dungeon") || lowerLabel.equals("danzh")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Only for players.");
                return true;
            }
            plugin.getMenuService().openPlayerOverview(p);
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
                    BlockPosKey a = d.getAltarPos();
                    sender.sendMessage(TextUtil.color(" &#F8BEFB● &f" + d.getId()
                            + " &8[" + d.getCategory().getColorHex() + d.getCategory().getTitle() + "&8] &7— &f"
                            + a.x() + ", " + a.y() + ", " + a.z() + " &8(&7" + TextUtil.formatDuration(d.secondsUntilReset()) + "&8)"));
                }
            }
            case "tp" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "Использование: /edungeon tp <id>");
                    return true;
                }
                DungeonInstance d = plugin.getDungeonManager().getDungeon(args[1]);
                if (d == null) {
                    sender.sendMessage(plugin.prefix() + "§cДанж не найден: " + args[1]);
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
                    if (d == null) {
                        sender.sendMessage(plugin.prefix() + "§cДанж не найден.");
                        return true;
                    }
                    plugin.getDungeonManager().regenerateDungeon(d, false);
                    sender.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7восстановлен.");
                }
            }
            case "relocate" -> {
                String target = args.length >= 2 ? args[1] : "all";
                if (target.equalsIgnoreCase("all")) {
                    for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                        plugin.getDungeonManager().regenerateDungeon(d, true);
                    }
                    sender.sendMessage(plugin.prefix() + "Все данжи откатили старый рельеф и перенесены на новые координаты!");
                } else {
                    DungeonInstance d = plugin.getDungeonManager().getDungeon(target);
                    if (d != null) {
                        plugin.getDungeonManager().regenerateDungeon(d, true);
                        sender.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7перенесён на новое место.");
                    }
                }
            }
            case "autospawn", "generate" -> {
                plugin.getDungeonManager().ensureTargetDungeonCounts();
                sender.sendMessage(plugin.prefix() + "Запущена генерация недостающих данжей (5 наземных / 5 подземных / 5 воздушных).");
            }
            case "spawn", "paste" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "Использование: /edungeon spawn <шаблон>");
                    return true;
                }
                DungeonTemplate tpl = plugin.getDungeonManager().getTemplate(args[1]);
                if (tpl == null) {
                    sender.sendMessage(plugin.prefix() + "§cШаблон не найден: " + args[1]);
                    return true;
                }
                plugin.getDungeonManager().manualSpawnHere(p, tpl);
                sender.sendMessage(plugin.prefix() + "Постройка §f" + tpl.getDisplayName() + " §7установлена!");
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "Использование: /edungeon remove <id>");
                    return true;
                }
                boolean ok = plugin.getDungeonManager().removeDungeon(args[1], true);
                sender.sendMessage(plugin.prefix() + (ok ? "Данж удалён и исходный рельеф восстановлен." : "§cДанж не найден."));
            }
            case "loot" -> {
                if (sender instanceof Player p) {
                    plugin.getMenuService().openLootTierSelector(p);
                }
            }
            case "wand" -> {
                if (sender instanceof Player p) {
                    ItemStack wand = new ItemStack(Material.BLAZE_ROD);
                    ItemMeta meta = wand.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(TextUtil.color("&#F8BEFBЖезл Архитектора Данжей"));
                        meta.setLore(TextUtil.colorList(List.of(
                                "&7● &fЛКМ по блоку — Точка 1",
                                "&7● &fПКМ по блоку — Точка 2"
                        )));
                        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                        meta.getPersistentDataContainer().set(plugin.getWandPdc(), PersistentDataType.BYTE, (byte) 1);
                        wand.setItemMeta(meta);
                    }
                    p.getInventory().addItem(wand);
                    p.sendMessage(plugin.prefix() + "Выдан жезл выделения данжа.");
                }
            }
            case "mark" -> {
                if (!(sender instanceof Player p) || args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "Использование: /edungeon mark <altar|entrance|seal|chest|reward|mob>");
                    return true;
                }
                BuilderSession bs = sessions.computeIfAbsent(p.getUniqueId(), k -> new BuilderSession());
                BlockPosKey here = BlockPosKey.of(p.getLocation());
                BlockPosKey under = here.add(0, -1, 0);
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "altar" -> bs.altar = under;
                    case "entrance" -> bs.entrance = here;
                    case "seal" -> bs.seals.add(under);
                    case "chest" -> bs.explorerChests.add(under);
                    case "reward" -> bs.rewardChests.add(under);
                    case "mob" -> bs.mobSpawns.add(here);
                    default -> {
                        sender.sendMessage(plugin.prefix() + "§cНеизвестная метка.");
                        return true;
                    }
                }
                sender.sendMessage(plugin.prefix() + "Метка §f" + args[1] + " §7записана!");
            }
            case "save" -> {
                if (!(sender instanceof Player p) || args.length < 4) {
                    sender.sendMessage(plugin.prefix() + "Использование: /edungeon save <id> <SURFACE|UNDERGROUND|SKY> <1..5> [WAVES|CAPTURE|SEALS]");
                    return true;
                }
                saveCustomTemplateFromSelection(p, args);
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> sender.sendMessage(plugin.prefix() + "Команды: /edungeon <gui|list|tp|regen|relocate|autospawn|spawn|remove|loot|wand|mark|save|reload>");
        }
        return true;
    }

    private void saveCustomTemplateFromSelection(Player p, String[] args) {
        BuilderSession bs = sessions.get(p.getUniqueId());
        CuboidRegion box = null;
        if (bs != null && bs.pos1 != null && bs.pos2 != null) {
            box = new CuboidRegion(bs.pos1.worldName(), bs.pos1.x(), bs.pos1.y(), bs.pos1.z(),
                    bs.pos2.x(), bs.pos2.y(), bs.pos2.z()).normalized();
        } else {
            box = plugin.getWorldEditHook().getPlayerSelection(p);
        }
        if (box == null) {
            p.sendMessage(plugin.prefix() + "§cСначала выделите область жезлом (/edungeon wand) или WorldEdit!");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        DungeonCategory cat = DungeonCategory.valueOf(args[2].toUpperCase(Locale.ROOT));
        int tier = Math.max(1, Math.min(5, Integer.parseInt(args[3])));
        TrialType tt = args.length >= 5 ? TrialType.valueOf(args[4].toUpperCase(Locale.ROOT)) : TrialType.WAVES;

        World w = p.getWorld();
        DungeonTemplate tpl = new DungeonTemplate(id, id, cat, tier, tt, box.sizeX(), box.sizeY(), box.sizeZ());
        for (int y = 0; y < box.sizeY(); y++) {
            for (int z = 0; z < box.sizeZ(); z++) {
                for (int x = 0; x < box.sizeX(); x++) {
                    String st = w.getBlockAt(box.minX() + x, box.minY() + y, box.minZ() + z).getBlockData().getAsString(true);
                    tpl.setBlock(x, y, z, st);
                    if (st.startsWith("minecraft:barrel")) tpl.getExplorerChestOffsets().add(new int[]{x, y, z});
                    else if (st.startsWith("minecraft:chest")) tpl.getRewardChestOffsets().add(new int[]{x, y, z});
                    else if (st.startsWith("minecraft:vault")) tpl.setAltarOffset(x, y, z);
                }
            }
        }
        if (bs != null) {
            if (bs.altar != null) tpl.setAltarOffset(bs.altar.x() - box.minX(), bs.altar.y() - box.minY(), bs.altar.z() - box.minZ());
            if (bs.entrance != null) tpl.setEntranceOffset(bs.entrance.x() - box.minX(), bs.entrance.y() - box.minY(), bs.entrance.z() - box.minZ());
            for (BlockPosKey s : bs.seals) tpl.getSealOffsets().add(new int[]{s.x() - box.minX(), s.y() - box.minY(), s.z() - box.minZ()});
            for (BlockPosKey c : bs.explorerChests) tpl.getExplorerChestOffsets().add(new int[]{c.x() - box.minX(), c.y() - box.minY(), c.z() - box.minZ()});
            for (BlockPosKey r : bs.rewardChests) tpl.getRewardChestOffsets().add(new int[]{r.x() - box.minX(), r.y() - box.minY(), r.z() - box.minZ()});
            for (BlockPosKey m : bs.mobSpawns) tpl.getMobSpawnOffsets().add(new int[]{m.x() - box.minX(), m.y() - box.minY(), m.z() - box.minZ()});
        }
        try {
            plugin.getDungeonManager().saveTemplateYaml(tpl);
            p.sendMessage(plugin.prefix() + "Шаблон §f" + id + " §7сохранён в schematics/" + id + ".yml!");
        } catch (IOException ex) {
            p.sendMessage(plugin.prefix() + "§cОшибка сохранения: " + ex.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("elytrixdungeons.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0],
                    List.of("gui", "list", "tp", "regen", "relocate", "autospawn", "spawn", "remove", "loot", "wand", "mark", "save", "reload"),
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
            if (sub.equals("mark")) {
                return StringUtil.copyPartialMatches(args[1], List.of("altar", "entrance", "seal", "chest", "reward", "mob"), new ArrayList<>());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("save")) {
            return StringUtil.copyPartialMatches(args[2], List.of("SURFACE", "UNDERGROUND", "SKY"), new ArrayList<>());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("save")) {
            return StringUtil.copyPartialMatches(args[3], List.of("1", "2", "3", "4", "5"), new ArrayList<>());
        }
        return List.of();
    }
}
