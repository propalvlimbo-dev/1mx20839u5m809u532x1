package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Пары для боя 2 на 2.
 *
 * Напарника нельзя «взять» молча: приглашённый получает в чате две кнопки —
 * «Принять» и «Отклонить». Приглашение живёт TEAM_INVITE_SECONDS секунд,
 * после этого пропадает само. Принять можно только одно приглашение.
 *
 * Пара живёт, пока не начнётся бой, пока кто-то не выйдет из пары или не
 * покинет сервер. Отдельного «срока» у пары нет — это просто двое игроков,
 * которые договорились идти в очередь вместе.
 */
final class Teams {

    private static final long INVITE_SECONDS = 60L;

    /** Приглашение: кто позвал и когда. Хранится у приглашённого. */
    private static final class Invite {
        final UUID owner;
        final String ownerName;
        final long createdAt;

        Invite(UUID owner, String ownerName, long createdAt) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.createdAt = createdAt;
        }
    }

    private final ElytrixDuelsBridge plugin;
    /** приглашённый → приглашение */
    private final Map<UUID, Invite> invites = new HashMap<UUID, Invite>();
    /** оба игрока пары ссылаются друг на друга */
    private final Map<UUID, UUID> partner = new HashMap<UUID, UUID>();

    Teams(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- состояние

    boolean paired(UUID id) {
        return partner.containsKey(id);
    }

    UUID partnerOf(UUID id) {
        return partner.get(id);
    }

    /** Строка «А + Б» для сообщений. */
    String pairLine(UUID first) {
        UUID second = partner.get(first);
        return name(first) + (second == null ? "" : " &f+ &d" + name(second));
    }

    private String name(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? "?" : player.getName();
    }

    private boolean busy(UUID id) {
        return plugin.duels().busy(id) || plugin.queue().isQueued(id);
    }

    // ------------------------------------------------------------ приглашение

    /** Пригласить конкретного игрока в пару. */
    void invite(Player owner, Player target) {
        if (target == null || !target.isOnline()) {
            plugin.msg(owner, pair("messages.team-target-gone",
                    "&#F8BEFBДуэли &8» &cИгрок уже вышел."));
            return;
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            plugin.msg(owner, pair("messages.team-self",
                    "&#F8BEFBДуэли &8» &cСебя в напарники не взять."));
            return;
        }
        UUID ownerId = owner.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (paired(ownerId)) {
            plugin.msg(owner, pair("messages.team-already",
                    "&#F8BEFBДуэли &8» &cТы уже в паре с &f%name%&c. Сначала выйди из неё.")
                    .replace("%name%", name(partnerOf(ownerId))));
            return;
        }
        if (busy(targetId)) {
            plugin.msg(owner, pair("messages.team-target-busy",
                    "&#F8BEFBДуэли &8» &c%name%&c сейчас занят: в бою или в поиске.")
                    .replace("%name%", target.getName()));
            return;
        }
        if (paired(targetId)) {
            plugin.msg(owner, pair("messages.team-target-paired",
                    "&#F8BEFBДуэли &8» &c%name%&c уже в паре.")
                    .replace("%name%", target.getName()));
            return;
        }
        Invite existing = invites.get(targetId);
        if (existing != null && existing.owner.equals(ownerId)) {
            plugin.msg(owner, pair("messages.team-invite-wait",
                    "&#F8BEFBДуэли &8» &7Ты уже позвал &f%name%&7, ждём ответа.")
                    .replace("%name%", target.getName()));
            return;
        }
        invites.put(targetId, new Invite(ownerId, owner.getName(), System.currentTimeMillis()));
        plugin.forgetTarget(ownerId);

        plugin.msg(owner, pair("messages.team-invite-sent",
                "&#F8BEFBДуэли &8» &fПриглашение отправлено: &d%name%&f. "
                        + "Ждём ответа &f60 с&f.")
                .replace("%name%", target.getName()));

        Component line = Text.of(pair("messages.team-invite-received",
                "&#F8BEFBДуэли &8» &d%owner% &fзовёт тебя в пару для боя 2 на 2. ")
                .replace("%owner%", owner.getName()));
        Component accept = Text.button("&a[Принять] ", "/duel team accept " + owner.getName(),
                "Встать в пару с " + owner.getName());
        Component deny = Text.button("&c[Отклонить]", "/duel team deny " + owner.getName(),
                "Отказаться от приглашения");
        Text.send(target, line.append(accept).append(deny));
    }

    /** Случайный свободный игрок получает приглашение. */
    void randomInvite(Player owner) {
        if (paired(owner.getUniqueId())) {
            plugin.msg(owner, pair("messages.team-already",
                    "&#F8BEFBДуэли &8» &cТы уже в паре с &f%name%&c. Сначала выйди из неё.")
                    .replace("%name%", name(partnerOf(owner.getUniqueId()))));
            return;
        }
        List<Player> candidates = new ArrayList<Player>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID id = online.getUniqueId();
            if (id.equals(owner.getUniqueId())) continue;
            if (!online.isOnline()) continue;
            if (busy(id) || paired(id)) continue;
            Invite pending = invites.get(id);
            if (pending != null && System.currentTimeMillis() - pending.createdAt < INVITE_SECONDS * 1000L) {
                continue;
            }
            candidates.add(online);
        }
        if (candidates.isEmpty()) {
            plugin.msg(owner, pair("messages.team-no-random",
                    "&#F8BEFBДуэли &8» &cСвободных игроков нет — попробуй через минуту."));
            return;
        }
        Collections.shuffle(candidates);
        invite(owner, candidates.get(0));
    }

    /** Приглашённый нажал «Принять». expectedOwner — ник из кнопки, может быть null. */
    void accept(Player invitee, String expectedOwner) {
        UUID id = invitee.getUniqueId();
        Invite pending = invites.get(id);
        if (pending != null && expectedOwner != null && !expectedOwner.isEmpty()
                && !pending.ownerName.equalsIgnoreCase(expectedOwner)) {
            plugin.msg(invitee, pair("messages.team-invite-stale",
                    "&#F8BEFBДуэли &8» &7Это приглашение уже неактуально."));
            return;
        }
        Invite invite = invites.remove(id);
        if (invite == null) {
            plugin.msg(invitee, pair("messages.team-none",
                    "&#F8BEFBДуэли &8» &cТебя никто не приглашал (или приглашение истекло)."));
            return;
        }
        Player owner = Bukkit.getPlayer(invite.owner);
        if (owner == null || !owner.isOnline()) {
            plugin.msg(invitee, pair("messages.team-owner-gone",
                    "&#F8BEFBДуэли &8» &c%name%&c уже вышел.").replace("%name%", invite.ownerName));
            return;
        }
        if (busy(id) || busy(invite.owner)) {
            plugin.msg(invitee, pair("messages.team-busy",
                    "&#F8BEFBДуэли &8» &cКто-то уже в бою или в поиске."));
            return;
        }
        if (paired(id) || paired(invite.owner)) {
            plugin.msg(invitee, pair("messages.team-paired",
                    "&#F8BEFBДуэли &8» &cУ кого-то уже есть пара."));
            return;
        }
        partner.put(id, invite.owner);
        partner.put(invite.owner, id);
        invites.remove(invite.owner);

        String text = pair("messages.team-ready",
                "&#F8BEFBДуэли &8» &fПара собрана: &d%a% &f+ &d%b%&f.")
                .replace("%a%", owner.getName()).replace("%b%", invitee.getName());
        plugin.msg(owner, text);
        plugin.msg(invitee, text);

        Component more = Text.of(pair("messages.team-next",
                "&#F8BEFBДуэли &8» &7Дальше выбери кит — встанем в очередь парой. "));
        Component kit = Text.button("&d[Выбрать кит]", "/duel kitmenu", "Открыть выбор кита");
        Component out = Text.button(" &7[Выйти из пары]", "/duel team leave", "Разорвать пару");
        Text.send(owner, more.append(kit).append(out));
        Text.send(invitee, more.append(kit).append(out));
    }

    /** Приглашённый нажал «Отклонить». */
    void deny(Player invitee) {
        Invite invite = invites.remove(invitee.getUniqueId());
        if (invite == null) {
            plugin.msg(invitee, pair("messages.team-none",
                    "&#F8BEFBДуэли &8» &cТебя никто не приглашал (или приглашение истекло)."));
            return;
        }
        plugin.msg(invitee, pair("messages.team-denied-self",
                "&#F8BEFBДуэли &8» &7Приглашение отклонено."));
        Player owner = Bukkit.getPlayer(invite.owner);
        if (owner != null) {
            plugin.msg(owner, pair("messages.team-denied",
                    "&#F8BEFBДуэли &8» &c%name%&c отклонил приглашение.")
                    .replace("%name%", invitee.getName()));
        }
    }

    /** Разорвать пару (или выйти из неё). */
    void leave(Player player) {
        UUID id = player.getUniqueId();
        UUID other = partner.remove(id);
        if (other == null) {
            plugin.msg(player, pair("messages.team-not-paired",
                    "&#F8BEFBДуэли &8» &7Ты не в паре."));
            return;
        }
        partner.remove(other);
        // молча: игрок выходит из пары, а не «отменяет поиск»
        plugin.queue().leave(player, false);
        plugin.msg(player, pair("messages.team-left",
                "&#F8BEFBДуэли &8» &7Ты вышел из пары."));
        Player partnerPlayer = Bukkit.getPlayer(other);
        if (partnerPlayer != null) {
            plugin.msg(partnerPlayer, pair("messages.team-partner-left",
                    "&#F8BEFBДуэли &8» &f%name% &7вышел из пары.")
                    .replace("%name%", player.getName()));
        }
    }

    /** Игрок вышел с сервера: снимаем приглашения и рвём пару. */
    void quit(Player player) {
        UUID id = player.getUniqueId();
        invites.remove(id);
        Iterator<Map.Entry<UUID, Invite>> iterator = invites.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().owner.equals(id)) iterator.remove();
        }
        UUID other = partner.remove(id);
        if (other == null) return;
        partner.remove(other);
        Player partnerPlayer = Bukkit.getPlayer(other);
        if (partnerPlayer != null) {
            plugin.msg(partnerPlayer, pair("messages.team-partner-left",
                    "&#F8BEFBДуэли &8» &f%name% &7вышел из пары.")
                    .replace("%name%", player.getName()));
        }
    }

    /** Раз в секунду: приглашения старше минуты пропадают. */
    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Invite>> iterator = invites.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, Invite> entry = iterator.next();
                    Invite invite = entry.getValue();
                    if (now - invite.createdAt < INVITE_SECONDS * 1000L) continue;
                    iterator.remove();
                    Player owner = Bukkit.getPlayer(invite.owner);
                    Player invitee = Bukkit.getPlayer(entry.getKey());
                    if (owner != null) {
                        plugin.msg(owner, pair("messages.team-expired",
                                "&#F8BEFBДуэли &8» &7Приглашение для &f%name%&7 истекло.")
                                .replace("%name%", invitee == null ? "?" : invitee.getName()));
                    }
                    if (invitee != null) {
                        plugin.msg(invitee, pair("messages.team-expired-self",
                                "&#F8BEFBДуэли &8» &7Приглашение от &f%name%&7 истекло.")
                                .replace("%name%", invite.ownerName));
                    }
                }
            }
        }, 20L, 20L);
    }

    private String pair(String path, String fallback) {
        return plugin.string(path, fallback);
    }
}
