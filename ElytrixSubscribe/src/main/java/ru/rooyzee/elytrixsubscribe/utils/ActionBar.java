package ru.rooyzee.elytrixsubscribe.utils;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.EnumWrappers.ChatType;
import org.bukkit.entity.Player;

public class ActionBar {
    private final String string;

    public ActionBar(String string) {
        this.string = string;
    }

    public void send(Player player) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(Server.CHAT);
            WrappedChatComponent chatComponent = WrappedChatComponent.fromText(Utils.color(this.string));
            packet.getChatComponents().write(0, chatComponent);
            packet.getChatTypes().write(0, ChatType.GAME_INFO);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception var4) {
            var4.printStackTrace();
        }
    }

    public String getString() {
        return this.string;
    }
}