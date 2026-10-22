package org.bukkit.event.player;
import java.util.UUID;
import org.bukkit.event.Event;
public class AsyncPlayerPreLoginEvent extends Event {
    public String getName() { return ""; }
    public UUID getUniqueId() { return java.util.UUID.randomUUID(); }
    public String getAddress() { return ""; }
    public void disallow(Result result, String message) { }
    public void setLoginResult(Result result) { }
    public enum Result { ALLOWED, KICK_FULL, KICK_BANNED, KICK_WHITELIST, KICK_OTHER }
}
