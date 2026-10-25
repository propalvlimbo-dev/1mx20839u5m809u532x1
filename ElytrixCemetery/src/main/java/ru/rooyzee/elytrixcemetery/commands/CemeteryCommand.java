package ru.rooyzee.elytrixcemetery.commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.loot.LootEditorGUI;
import ru.rooyzee.elytrixcemetery.loot.LootType;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CemeteryCommand implements CommandExecutor, TabCompleter {

    private static final long TELEPORT_COOLDOWN_MILLIS = 5L * 60L * 1000L;
    private static final int TELEPORT_SEARCH_RADIUS = 4;
    private static final int[] SAFE_Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5};

    private final Main plugin;
    private final NamespacedKey teleportCooldownKey;

    public CemeteryCommand(Main plugin) {
        this.plugin = plugin;
        this.teleportCooldownKey = new NamespacedKey(plugin, "cemetery_teleport_last_use");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "tp":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только для игроков");
                    return true;
                }
                Player p = (Player) sender;
                long now = System.currentTimeMillis();
                Long lastTeleport = p.getPersistentDataContainer().get(teleportCooldownKey, PersistentDataType.LONG);
                if (lastTeleport != null) {
                    long remainingMillis = TELEPORT_COOLDOWN_MILLIS - Math.max(0L, now - lastTeleport);
                    if (remainingMillis > 0L) {
                        long remainingSeconds = (remainingMillis + 999L) / 1000L;
                        long minutes = remainingSeconds / 60L;
                        long seconds = remainingSeconds % 60L;
                        p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cТелепортация на Кладбище будет доступна через &f"
                                + minutes + " мин. " + seconds + " сек."));
                        return true;
                    }
                }

                Location center = plugin.getConfigManager().getCenterLocation();
                if (center == null || center.getWorld() == null) {
                    p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cЛокация кладбища не настроена!"));
                    return true;
                }

                Location desired = center.clone().add(0.5, 1, -25.5);
                Location safeLocation = findSafeCemeteryLocation(center, desired);
                if (safeLocation == null) {
                    p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНе удалось найти безопасное место на Кладбище. Телепортация отменена."));
                    return true;
                }

                if (p.teleport(safeLocation)) {
                    // Player PDC keeps the cooldown with the player's saved data across relogs/restarts.
                    p.getPersistentDataContainer().set(teleportCooldownKey, PersistentDataType.LONG, now);
                    p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aВы успешно телепортированы на &fКладбище&a!"));
                } else {
                    p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cТелепортация не удалась. Попробуйте позже."));
                }
                break;

            case "info":
                int dug = plugin.getGraveManager().getDugCount();
                int total = plugin.getGraveManager().getTotalCount();
                int ghosts = plugin.getGhostController().getAliveCount();
                String time = plugin.getGraveManager().formatTime(plugin.getGraveManager().getRemainingRefillSeconds());
                Location infoCenter = plugin.getConfigManager().getCenterLocation();
                String locationText = infoCenter == null || infoCenter.getWorld() == null
                        ? "не настроена"
                        : infoCenter.getWorld().getName() + " (" + infoCenter.getBlockX() + ", "
                        + infoCenter.getBlockY() + ", " + infoCenter.getBlockZ() + ")";

                sender.sendMessage("");
                sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &#F8BEFBИнформация о Кладбище"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fЛокация: &#F8BEFB" + locationText));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fСейчас раскопано могил: &#F8BEFB" + dug + "&f из &#F8BEFB" + total));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fЕщё можно раскопать: &#F8BEFB" + Math.max(0, total - dug)));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fЖивых призраков на кладбище: &#F8BEFB" + ghosts));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fМогилы закопают и лут обновят через: &#F8BEFB" + time));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fСтатус: &#F8BEFBАктивно всегда"));
                sender.sendMessage("");
                break;

            case "actionbar":
            case "on":
            case "off":
                if (sender instanceof Player) {
                    Player actionBarPlayer = (Player) sender;
                    if (sub.equals("on")) {
                        plugin.getActionBarTask().setPlayerEnabled(actionBarPlayer, true);
                    } else if (sub.equals("off")) {
                        plugin.getActionBarTask().setPlayerEnabled(actionBarPlayer, false);
                    } else {
                        plugin.getActionBarTask().togglePlayer(actionBarPlayer);
                    }
                }
                break;

            case "loot":
            case "edit":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только для игроков");
                    return true;
                }
                LootType type = LootType.GRAVES;
                if (args.length >= 2 && args[1].equalsIgnoreCase("ghosts")) {
                    type = LootType.GHOSTS;
                }
                new LootEditorGUI(plugin).open((Player) sender, type, 1);
                break;

            case "refill":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                plugin.getGraveManager().refillAndBuryAll();
                sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&aВсе могилы успешно закопаны, лут обновлён!"));
                break;

            case "paste":
            case "respawn":
            case "generate":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                if (plugin.getSchematicManager().buildOrPasteCemetery()) {
                    plugin.getRegionManager().createOrUpdateRegion();
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&aПостройка кладбища успешно пересоздана!"));
                } else {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix()
                            + "&cНе удалось построить Кладбище: проверьте мир и координаты в config.yml."));
                }
                break;

            case "clear":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                plugin.getGraveManager().clearAll();
                plugin.getGhostController().clearAll();
                plugin.getSchematicManager().getGenerator().clearCemeteryBlocks();
                plugin.getRegionManager().removeRegion();
                sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&aКладбище, призраки и все постройки успешно удалены!"));
                break;

            case "reload":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                plugin.getConfigManager().reload();
                plugin.getLootStorageManager().loadAll();
                plugin.getRegionManager().createOrUpdateRegion();
                plugin.getGraveManager().refreshHolograms();
                plugin.getHologramManager().removeMainHologram();
                sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&aКонфигурация и лут успешно перезагружены!"));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private Location findSafeCemeteryLocation(Location center, Location desired) {
        Location safe = findSafeLocationNear(center, desired);
        if (safe != null) return safe;

        // The gate may be obstructed by terrain; try the configured center as a nearby fallback.
        Location centerSpawn = center.clone().add(0.5, 1.0, 0.5);
        return findSafeLocationNear(center, centerSpawn);
    }

    private Location findSafeLocationNear(Location cemeteryCenter, Location target) {
        World world = target.getWorld();
        if (world == null || cemeteryCenter.getWorld() == null
                || !world.equals(cemeteryCenter.getWorld())) return null;

        int boundaryRadius = Math.max(0, plugin.getConfigManager().getBoundaryRadius());
        int targetX = target.getBlockX();
        int targetY = target.getBlockY();
        int targetZ = target.getBlockZ();

        for (int radius = 0; radius <= TELEPORT_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                    int x = targetX + dx;
                    int z = targetZ + dz;
                    if (Math.abs(x - cemeteryCenter.getBlockX()) > boundaryRadius
                            || Math.abs(z - cemeteryCenter.getBlockZ()) > boundaryRadius
                            || !world.isChunkLoaded(x >> 4, z >> 4)) continue;

                    for (int dy : SAFE_Y_OFFSETS) {
                        int feetY = targetY + dy;
                        if (feetY <= world.getMinHeight() || feetY + 1 >= world.getMaxHeight()) continue;

                        Block feet = world.getBlockAt(x, feetY, z);
                        Block head = world.getBlockAt(x, feetY + 1, z);
                        Block floor = world.getBlockAt(x, feetY - 1, z);
                        if (!feet.isPassable() || feet.isLiquid() || isUnsafeTeleportBlock(feet.getType())
                                || !head.isPassable() || head.isLiquid() || isUnsafeTeleportBlock(head.getType())
                                || !floor.getType().isSolid() || floor.isLiquid()
                                || isUnsafeTeleportBlock(floor.getType())) continue;

                        Location candidate = new Location(world, x + 0.5, feetY, z + 0.5,
                                target.getYaw(), target.getPitch());
                        if (world.getWorldBorder().isInside(candidate)) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isUnsafeTeleportBlock(Material material) {
        String name = material.name();
        return name.equals("LAVA") || name.equals("FIRE") || name.equals("SOUL_FIRE")
                || name.equals("MAGMA_BLOCK") || name.equals("CACTUS") || name.equals("WITHER_ROSE")
                || name.equals("SWEET_BERRY_BUSH") || name.equals("CAMPFIRE")
                || name.equals("SOUL_CAMPFIRE") || name.equals("NETHER_PORTAL")
                || name.equals("END_PORTAL") || name.equals("END_GATEWAY")
                || name.equals("POWDER_SNOW") || name.equals("POINTED_DRIPSTONE");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &#F8BEFBКоманды Кладбища"));
        sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery tp &8— &#F8BEFBТелепортация на Кладбище"));
        sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery info &8— &#F8BEFBИнформация об ивенте"));
        sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery actionbar [on|off] &8— &#F8BEFBВкл/Выкл Actionbar"));
        if (sender.hasPermission("elytrixcemetery.admin")) {
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery loot <graves|ghosts> &8— &#F8BEFBРедактирование лута"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery refill &8— &#F8BEFBЗакопать могилы и обновить лут"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery paste &8— &#F8BEFBВставить/сгенерировать кладбище"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery clear &8— &#F8BEFBПолная очистка построек и призраков"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery reload &8— &#F8BEFBПерезагрузка плагина"));
        }
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("tp", "info", "actionbar", "on", "off"));
            if (sender.hasPermission("elytrixcemetery.admin")) {
                list.addAll(Arrays.asList("loot", "refill", "paste", "clear", "reload"));
            }
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("loot")) {
            return Arrays.asList("graves", "ghosts");
        }
        return new ArrayList<>();
    }
}
