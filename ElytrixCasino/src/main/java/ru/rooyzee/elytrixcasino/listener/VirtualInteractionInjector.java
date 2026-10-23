package ru.rooyzee.elytrixcasino.listener;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.rooyzee.elytrixcasino.Main;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Перехват кликов по пакетным NPC (Рулетка, Минёр) через Netty-пайплайн игрока.
 * Совместим с Spigot 1.16.5+ и Mojang-mapped Paper 1.20.5–1.21.11+.
 */
public class VirtualInteractionInjector implements Listener {

    private static final String HANDLER_NAME = "elytrixcasino_interact";
    private static final Set<String> ACTIONS = new HashSet<>(Arrays.asList("INTERACT", "ATTACK", "INTERACT_AT"));

    private final Main plugin;
    private final CasinoListener handler;
    private final Map<Class<?>, Field> entityIdCache = new ConcurrentHashMap<>();
    private boolean warned;

    public VirtualInteractionInjector(Main plugin, CasinoListener handler) {
        this.plugin = plugin;
        this.handler = handler;
    }

    public void injectAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            inject(player);
        }
    }

    public void uninjectAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            uninject(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        uninject(event.getPlayer());
    }

    public void inject(Player player) {
        try {
            Channel channel = findChannel(player);
            if (channel == null) {
                warn("Не удалось получить netty-канал игрока — клик по NPC работать не будет.", null);
                return;
            }
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
            ChannelDuplexHandler listener = new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    try {
                        handlePacket(player, msg);
                    } catch (Throwable t) {
                        warn("Ошибка обработки interact-пакета", t);
                    }
                    super.channelRead(ctx, msg);
                }
            };
            if (channel.pipeline().get("decoder") != null) {
                channel.pipeline().addAfter("decoder", HANDLER_NAME, listener);
            } else if (channel.pipeline().get("packet_handler") != null) {
                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, listener);
            } else {
                channel.pipeline().addLast(HANDLER_NAME, listener);
            }
        } catch (Throwable t) {
            warn("Не удалось внедриться в канал игрока " + player.getName(), t);
        }
    }

    public void uninject(Player player) {
        try {
            Channel channel = findChannel(player);
            if (channel == null || channel.pipeline().get(HANDLER_NAME) == null) {
                return;
            }
            channel.eventLoop().execute(() -> {
                try {
                    if (channel.pipeline().get(HANDLER_NAME) != null) {
                        channel.pipeline().remove(HANDLER_NAME);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void handlePacket(Player player, Object packet) throws Exception {
        String name = packet.getClass().getSimpleName();
        if (!name.equals("PacketPlayInUseEntity") && !name.equals("ServerboundInteractPacket")) {
            return;
        }
        int entityId = readEntityId(packet);
        if (entityId < 0) {
            return;
        }
        String action = readAction(packet);
        if (action == null || action.equals("INTERACT_AT")) {
            return;
        }
        boolean attack = action.equals("ATTACK");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                handler.onVirtualInteract(player, entityId, attack);
            }
        });
    }

    private int readEntityId(Object packet) throws IllegalAccessException {
        Field cached = entityIdCache.get(packet.getClass());
        if (cached != null) {
            return cached.getInt(packet);
        }
        for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                    continue;
                }
                field.setAccessible(true);
                entityIdCache.put(packet.getClass(), field);
                return field.getInt(packet);
            }
        }
        return -1;
    }

    private String readAction(Object packet) throws Exception {
        for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(packet);
                if (value == null) {
                    continue;
                }
                if (value instanceof Enum) {
                    String enumName = ((Enum<?>) value).name();
                    if (ACTIONS.contains(enumName)) {
                        return enumName;
                    }
                    continue;
                }
                // В Paper 1.20.5–1.21.11 (Mojang mappings) Action.getType() — package-private,
                // поэтому обходим как getMethods(), так и getDeclaredMethods() всей иерархии!
                String enumAction = extractEnumFromObject(value);
                if (enumAction != null) {
                    return enumAction;
                }
            }
        }
        return null;
    }

    private String extractEnumFromObject(Object value) {
        for (Class<?> cls = value.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Method method : cls.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || !Enum.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(value);
                    if (result instanceof Enum) {
                        String enumName = ((Enum<?>) result).name();
                        if (ACTIONS.contains(enumName)) {
                            return enumName;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private Channel findChannel(Player player) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        return searchChannel(handle, 4);
    }

    private Channel searchChannel(Object object, int depth) throws IllegalAccessException {
        if (object == null || depth < 0) {
            return null;
        }
        for (Class<?> c = object.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                boolean isChannel = Channel.class.isAssignableFrom(field.getType());
                if (!isChannel && !isConnectionLike(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(object);
                if (value instanceof Channel) {
                    return (Channel) value;
                }
                Channel nested = searchChannel(value, depth - 1);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean isConnectionLike(Class<?> type) {
        String name = type.getName();
        return name.contains("Connection") || name.contains("NetworkManager") || name.contains("PacketListener");
    }

    private void warn(String message, Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        if (throwable == null) {
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().log(Level.WARNING, message, throwable);
        }
    }
}
