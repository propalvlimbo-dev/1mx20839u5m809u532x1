package ru.rooyzee.elytrixsubscribe.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.Main;

import java.util.function.Consumer;

public class SignInputUtil {

    public static void openSignInput(Player player, Consumer<String> callback) {
        Location loc = player.getLocation().clone();
        loc.setY(Math.max(1, loc.getBlockY() - 2));
        BlockPosition pos = new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        sendFakeSign(player, pos);

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            PacketContainer openSign = new PacketContainer(PacketType.Play.Server.OPEN_SIGN_EDITOR);
            openSign.getBlockPositionModifier().write(0, pos);
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, openSign);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 2L);

        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(Main.getInstance(), PacketType.Play.Client.UPDATE_SIGN) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                event.setCancelled(true);
                ProtocolLibrary.getProtocolManager().removePacketListener(this);

                String[] lines = event.getPacket().getStringArrays().read(0);
                String result = (lines != null && lines.length > 0 && lines[0] != null) ? lines[0] : "";

                removeFakeSign(player, loc);

                Bukkit.getScheduler().runTask(Main.getInstance(), () -> callback.accept(result));
            }
        });
    }

    private static void sendFakeSign(Player player, BlockPosition pos) {
        PacketContainer blockChange = new PacketContainer(PacketType.Play.Server.BLOCK_CHANGE);
        blockChange.getBlockPositionModifier().write(0, pos);
        blockChange.getBlockData().write(0, WrappedBlockData.createData(Material.OAK_SIGN));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, blockChange);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void removeFakeSign(Player player, Location loc) {
        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }
}