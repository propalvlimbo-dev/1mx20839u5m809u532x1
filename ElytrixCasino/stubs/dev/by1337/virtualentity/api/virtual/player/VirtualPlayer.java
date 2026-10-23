package dev.by1337.virtualentity.api.virtual.player;

import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.HumanoidArm;
import dev.by1337.virtualentity.api.virtual.VirtualLivingEntity;
import org.bukkit.entity.Player;

import java.util.Set;

public interface VirtualPlayer extends VirtualLivingEntity {
    static VirtualPlayer create() { return null; }
    void setName(String name);
    void setTexture(String value, String signature);
    void setListed(boolean listed);
    void setOnGround(boolean onGround);
    void setPlayerMainHand(HumanoidArm arm);
    void setPlayerModeCustomisation(byte mask);
    void playAnimation(EntityAnimation animation);
    void respawn();
    void sendRemovePlayerPacket(Player player);
    Set<Player> getLastViewers();
}
