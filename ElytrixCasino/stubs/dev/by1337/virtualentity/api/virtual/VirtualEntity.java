package dev.by1337.virtualentity.api.virtual;

import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.Pose;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.by1337.blib.geom.Vec3d;

import java.util.Set;
import java.util.UUID;

public interface VirtualEntity {
    int getId();
    UUID getUuid();
    Vec3d getPos();
    void setPos(Vec3d pos);
    void setYaw(float yaw);
    void setPitch(float pitch);
    float getYaw();
    float getPitch();
    void lookAt(Vec3d pos);
    void setNoGravity(boolean noGravity);
    void setSilent(boolean silent);
    void setInvisible(boolean invisible);
    void setCustomName(Component name);
    void setCustomNameVisible(boolean visible);
    void setPose(Pose pose);
    void broadcastAnimation(EntityAnimation animation);
    void tick(Set<Player> viewers);
}
