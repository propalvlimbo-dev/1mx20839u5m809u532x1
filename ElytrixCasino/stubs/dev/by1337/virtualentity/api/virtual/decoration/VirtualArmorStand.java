package dev.by1337.virtualentity.api.virtual.decoration;

import dev.by1337.virtualentity.api.virtual.VirtualLivingEntity;
import org.by1337.blib.geom.Vec3f;

public interface VirtualArmorStand extends VirtualLivingEntity {
    static VirtualArmorStand create() { return null; }
    void setSmall(boolean small);
    void setMarker(boolean marker);
    void setNoBasePlate(boolean noBasePlate);
    void setShowArms(boolean showArms);
    void setHeadPose(Vec3f pose);
    void setRightArmPose(Vec3f pose);
    void setLeftArmPose(Vec3f pose);
}
