package dev.by1337.virtualentity.api.virtual;

import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.by1337.blib.geom.Vec3d;

/** Слепок API VirtualEntityApi 1.4.2 (только то, что используем). */
public interface VirtualEntity {
    void setPos(Vec3d pos);
    Vec3d getPos();
    void setYaw(float yaw);
    void setPitch(float pitch);
    void setMotion(Vec3d motion);
    void setNoMotion();
    void tick(Set<Player> viewers);
    void setSilent(boolean flag);
    void setNoGravity(boolean flag);
    void setCustomName(Component customName);
    void setCustomNameVisible(boolean flag);
    void setGlowing(boolean flag);
    void setInvisible(boolean flag);
    void setOnGround(boolean onGround);
    void addTickTask(Object task);
    void removeAllTickTask();
    void respawn();
    int getId();
}
