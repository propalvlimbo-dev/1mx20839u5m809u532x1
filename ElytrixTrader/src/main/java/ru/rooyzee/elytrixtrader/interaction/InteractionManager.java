package ru.rooyzee.elytrixtrader.interaction;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderInstance;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InteractionManager {

    private static final String HANDLER = "elytrixtrader_interact";

    private final Main plugin;
    private final Map<UUID, Channel> attached = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field> intFields = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field> actionFields = new ConcurrentHashMap<>();
    private Class<?> usePacket;
    private Class<?> attackPacket;
    private boolean broken;

    public InteractionManager(Main plugin) {
        this.plugin = plugin;
        detect();
    }

    private void detect() {
        usePacket = null;
        attackPacket = null;
        String version = null;
        try {
            String[] parts = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
            if (parts.length > 0) version = parts[parts.length - 1];
        } catch (Throwable ignored) {
        }

        if (version != null && !version.isEmpty()) {
            String root = "net.minecraft.server." + version + ".";
            try {
                usePacket = Class.forName(root + "PacketPlayInUseEntity");
            } catch (Throwable ignored) {
            }
            try {
                attackPacket = Class.forName(root + "PacketPlayInAttackEntity");
            } catch (Throwable ignored) {
            }
        }

        // 1.17+ moved inbound packets out of the versioned NMS package and renamed UseEntity.
        if (usePacket == null) {
            for (String name : new String[]{
                    "net.minecraft.network.protocol.game.ServerboundInteractPacket",
                    "net.minecraft.network.protocol.game.PacketPlayInUseEntity"}) {
                try {
                    usePacket = Class.forName(name);
                    break;
                } catch (Throwable ignored) {
                }
            }
        }
        if (usePacket == null) broken = true;
    }

    public void attach(Player player) {
        if (usePacket == null || player == null) {
            return;
        }
        try {
            Channel channel = channel(player);
            if (channel == null) {
                return;
            }
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) != null) {
                attached.put(player.getUniqueId(), channel);
                return;
            }
            ChannelHandler handler = new InteractHandler(player.getUniqueId());
            if (pipeline.get("packet_handler") != null) {
                pipeline.addBefore("packet_handler", HANDLER, handler);
            } else if (pipeline.get("decoder") != null) {
                pipeline.addAfter("decoder", HANDLER, handler);
            } else if (pipeline.get("packet_decoder") != null) {
                pipeline.addAfter("packet_decoder", HANDLER, handler);
            } else {
                pipeline.addLast(HANDLER, handler);
            }
            attached.put(player.getUniqueId(), channel);
        } catch (Throwable throwable) {
            if (!broken) {
                broken = true;
            }
        }
    }

    public void detach(Player player) {
        if (player == null) {
            return;
        }
        detach(player.getUniqueId());
    }

    public void detach(UUID playerId) {
        lastInteract.remove(playerId);
        Channel channel = attached.remove(playerId);
        if (channel == null) {
            return;
        }
        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) != null) {
                pipeline.remove(HANDLER);
            }
        } catch (Throwable ignored) {
        }
    }

    public void detachAll() {
        for (UUID playerId : new java.util.ArrayList<>(attached.keySet())) {
            detach(playerId);
        }
        attached.clear();
        lastInteract.clear();
    }

    public boolean available() {
        return usePacket != null;
    }

    /** Whether this player's packet hook is attached; otherwise Bukkit click fallback must stay enabled. */
    public boolean isAttached(Player player) {
        if (player == null) return false;
        Channel channel = attached.get(player.getUniqueId());
        if (channel == null) return false;
        try {
            return channel.pipeline().get(HANDLER) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Channel channel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = fieldByType(handle,
                    "PlayerConnection", "ServerGamePacketListenerImpl", "playerConnection", "connection");
            if (connection == null) {
                return null;
            }
            Object manager = fieldByType(connection,
                    "NetworkManager", "Connection", "networkManager", "connection");
            if (manager == null) {
                return null;
            }
            Object channel = fieldByType(manager, "Channel", "channel");
            return channel instanceof Channel ? (Channel) channel : null;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private Object fieldByType(Object target, String... candidates) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                for (String candidate : candidates) {
                    if (field.getType().getSimpleName().equals(candidate) || field.getName().equals(candidate)) {
                        try {
                            field.setAccessible(true);
                            return field.get(target);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private int entityId(Object packet) {
        Field field = intFields.get(packet.getClass());
        if (field == null) {
            Field firstInt = null;
            Class<?> current = packet.getClass();
            while (current != null && field == null) {
                for (Field candidate : current.getDeclaredFields()) {
                    if (candidate.getType() != int.class || Modifier.isStatic(candidate.getModifiers())) continue;
                    if (firstInt == null) firstInt = candidate;
                    String name = candidate.getName().toLowerCase(java.util.Locale.ROOT);
                    if (name.contains("entity") || name.equals("id")) {
                        field = candidate;
                        break;
                    }
                }
                current = current.getSuperclass();
            }
            if (field == null) field = firstInt;
            if (field == null) {
                return -1;
            }
            field.setAccessible(true);
            intFields.put(packet.getClass(), field);
        }
        try {
            return field.getInt(packet);
        } catch (Throwable throwable) {
            return -1;
        }
    }

    private boolean attack(Object packet) {
        Field field = actionFields.get(packet.getClass());
        if (field == null) {
            Class<?> current = packet.getClass();
            while (current != null && field == null) {
                for (Field candidate : current.getDeclaredFields()) {
                    if (Modifier.isStatic(candidate.getModifiers()) || candidate.getType().isPrimitive()) continue;
                    String fieldName = candidate.getName().toLowerCase(java.util.Locale.ROOT);
                    String typeName = candidate.getType().getSimpleName().toLowerCase(java.util.Locale.ROOT);
                    if (fieldName.contains("action") || typeName.contains("action")) {
                        field = candidate;
                        break;
                    }
                }
                current = current.getSuperclass();
            }
            if (field == null) {
                return false;
            }
            field.setAccessible(true);
            actionFields.put(packet.getClass(), field);
        }
        try {
            Object value = field.get(packet);
            if (value == null) return false;
            String description = (value.toString() + " " + value.getClass().getSimpleName())
                    .toUpperCase(java.util.Locale.ROOT);
            return description.contains("ATTACK");
        } catch (Throwable throwable) {
            return false;
        }
    }

    private final class InteractHandler extends ChannelInboundHandlerAdapter {

        private final UUID playerId;

        private InteractHandler(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
            Class<?> type = packet.getClass();
            if (matches(type, usePacket) || matches(type, attackPacket)) {
                TraderInstance instance = plugin.traders().byEntityId(entityId(packet));
                if (instance != null) {
                    boolean isAttack = matches(type, attackPacket) || attack(packet);
                    route(isAttack, instance);
                    return;
                }
            }
            context.fireChannelRead(packet);
        }

        private void route(boolean isAttack, TraderInstance instance) {
            if (isAttack && !plugin.config().openOnAttack()) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = lastInteract.get(playerId);
            if (last != null && now - last < 250L) {
                return;
            }
            lastInteract.put(playerId, now);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    return;
                }
                plugin.menus().interact(player, instance);
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable throwable) {
        }
    }

    private boolean matches(Class<?> type, Class<?> expected) {
        return expected != null && (type == expected || expected.isAssignableFrom(type));
    }
}
