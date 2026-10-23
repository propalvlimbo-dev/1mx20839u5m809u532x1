package ru.elytrix.vault.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import ru.elytrix.vault.ElytrixVaultPlugin;
import ru.elytrix.vault.gui.EditorMainGui;
import ru.elytrix.vault.gui.KeyPoolEditorGui;
import ru.elytrix.vault.model.VaultBlock;
import ru.elytrix.vault.model.VaultKeyType;
import ru.elytrix.vault.util.TextUtil;

public class VaultCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("edit", "give", "set", "remove", "list", "reload", "help");

    private final ElytrixVaultPlugin plugin;

    public VaultCommand(ElytrixVaultPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elytrixvault.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                new EditorMainGui(plugin).open(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "edit", "gui", "menu" -> handleEdit(sender, args);
            case "give", "givekey", "key" -> handleGive(sender, args);
            case "set", "create", "place", "add" -> handleSet(sender, args);
            case "remove", "delete", "del" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("only-players"));
            return;
        }
        if (args.length >= 2) {
            VaultKeyType key = plugin.getKeyRegistry().getKey(args[1]);
            if (key == null) {
                sender.sendMessage(unknownKeyMsg(args[1]));
                return;
            }
            new KeyPoolEditorGui(plugin, key, 0).open(player);
            return;
        }
        new EditorMainGui(plugin).open(player);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }
        String targetName = args[1];
        VaultKeyType keyType = plugin.getKeyRegistry().getKey(args[2]);
        if (keyType == null) {
            sender.sendMessage(unknownKeyMsg(args[2]));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.msg("invalid-amount"));
                return;
            }
        }
        if (amount < 1 || amount > 64) {
            sender.sendMessage(plugin.msg("invalid-amount"));
            return;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        plugin.getKeyRegistry().giveKeyToPlayer(online, targetName, keyType, amount);

        String coloredKey = TextUtil.color(keyType.getDisplayName());
        if (online != null && online.isOnline()) {
            sender.sendMessage(plugin.msg("key-given-sender")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{key}", coloredKey)
                    .replace("{player}", online.getName()));
            online.sendMessage(plugin.msg("key-received")
                    .replace("{key}", coloredKey)
                    .replace("{amount}", String.valueOf(amount)));
            online.playSound(online.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        } else {
            sender.sendMessage(plugin.msg("key-given-queued")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{key}", coloredKey)
                    .replace("{player}", targetName));
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("only-players"));
            return;
        }
        RayTraceResult hit = player.rayTraceBlocks(6.0);
        if (hit == null || hit.getHitBlock() == null) {
            player.sendMessage(plugin.msg("look-at-block"));
            return;
        }
        Block looked = hit.getHitBlock();
        Block placeAt;
        if (looked.getType() == Material.SCULK_SHRIEKER) {
            VaultBlock already = plugin.getVaultManager().getVaultByBlock(looked);
            if (already != null && args.length < 2) {
                player.sendMessage(plugin.msg("vault-already-exists").replace("{id}", already.getId()));
                return;
            }
            placeAt = looked;
        } else {
            BlockFace face = hit.getHitBlockFace() != null ? hit.getHitBlockFace() : BlockFace.UP;
            placeAt = looked.getRelative(face);
        }

        String id = args.length >= 2 ? args[1] : null;
        VaultBlock created = plugin.getVaultManager().createVault(id, placeAt);
        player.playSound(placeAt.getLocation(), Sound.BLOCK_VAULT_PLACE, 1.0f, 1.0f);
        player.sendMessage(plugin.msg("vault-created")
                .replace("{id}", created.getId())
                .replace("{world}", created.getPos().getWorldName())
                .replace("{x}", String.valueOf(created.getPos().getX()))
                .replace("{y}", String.valueOf(created.getPos().getY()))
                .replace("{z}", String.valueOf(created.getPos().getZ())));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        VaultBlock target = null;
        if (args.length >= 2) {
            target = plugin.getVaultManager().getVaultById(args[1]);
        } else if (sender instanceof Player player) {
            RayTraceResult hit = player.rayTraceBlocks(6.0);
            if (hit != null && hit.getHitBlock() != null) {
                target = plugin.getVaultManager().getVaultByBlock(hit.getHitBlock());
            }
        }
        if (target == null) {
            sender.sendMessage(plugin.msg("vault-not-found"));
            return;
        }
        String removedId = target.getId();
        plugin.getVaultManager().removeVault(target, true);
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_VAULT_BREAK, 0.85f, 1.0f);
        }
        sender.sendMessage(plugin.msg("vault-removed").replace("{id}", removedId));
    }

    private void handleList(CommandSender sender) {
        var all = plugin.getVaultManager().getAllVaults();
        if (all.isEmpty()) {
            sender.sendMessage(plugin.msg("vault-list-empty"));
            return;
        }
        sender.sendMessage(TextUtil.color(plugin.rawMsg("vault-list-header")
                .replace("{count}", String.valueOf(all.size()))));
        String tpl = plugin.rawMsg("vault-list-line");
        for (VaultBlock vb : all) {
            sender.sendMessage(TextUtil.color(tpl
                    .replace("{id}", vb.getId())
                    .replace("{world}", vb.getPos().getWorldName())
                    .replace("{x}", String.valueOf(vb.getPos().getX()))
                    .replace("{y}", String.valueOf(vb.getPos().getY()))
                    .replace("{z}", String.valueOf(vb.getPos().getZ()))));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(plugin.msg("reloaded")
                .replace("{keys}", String.valueOf(plugin.getKeyRegistry().getAllKeys().size()))
                .replace("{vaults}", String.valueOf(plugin.getVaultManager().getAllVaults().size())));
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getMessages().getStringList("admin-help")) {
            sender.sendMessage(TextUtil.color(line));
        }
    }

    private String unknownKeyMsg(String given) {
        String available = plugin.getKeyRegistry().getAllKeys().stream()
                .map(VaultKeyType::getId)
                .collect(Collectors.joining(", "));
        return plugin.msg("unknown-key")
                .replace("{key}", given)
                .replace("{keys}", available);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("elytrixvault.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "edit", "gui", "menu" -> filter(
                        plugin.getKeyRegistry().getAllKeys().stream().map(VaultKeyType::getId).toList(),
                        args[1]
                );
                case "give", "givekey", "key" -> filter(
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),
                        args[1]
                );
                case "remove", "delete", "del" -> filter(
                        plugin.getVaultManager().getAllVaults().stream().map(VaultBlock::getId).toList(),
                        args[1]
                );
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3 && (sub.equals("give") || sub.equals("givekey") || sub.equals("key"))) {
            return filter(
                    plugin.getKeyRegistry().getAllKeys().stream().map(VaultKeyType::getId).toList(),
                    args[2]
            );
        }
        if (args.length == 4 && (sub.equals("give") || sub.equals("givekey") || sub.equals("key"))) {
            return filter(List.of("1", "3", "5", "10"), args[3]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> source, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String item : source) {
            if (item.toLowerCase(Locale.ROOT).startsWith(low)) {
                out.add(item);
            }
        }
        return out;
    }
}
