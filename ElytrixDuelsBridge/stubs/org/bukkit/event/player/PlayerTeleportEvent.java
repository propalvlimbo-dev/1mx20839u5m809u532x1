package org.bukkit.event.player;
import org.bukkit.Location;
import org.bukkit.entity.Player;
public class PlayerTeleportEvent extends org.bukkit.event.Event {
    public enum TeleportCause {
        UNKNOWN, PLUGIN, COMMAND, SPECTATE, ENDER_PEARL, CHORUS_FRUIT, NETHER_PORTAL,
        END_PORTAL, ENDER_SIGNAL, DISMOUNT, EXIT_BED
    }
    public Player getPlayer() { return null; }
    public Location getFrom() { return null; }
    public Location getTo() { return null; }
    public TeleportCause getCause() { return TeleportCause.UNKNOWN; }
}
