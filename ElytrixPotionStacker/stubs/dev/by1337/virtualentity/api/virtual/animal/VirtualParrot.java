package dev.by1337.virtualentity.api.virtual.animal;
public interface VirtualParrot extends dev.by1337.virtualentity.api.virtual.VirtualEntity {
    void setVariant(int variant);
    int getVariant();
    void setTame(boolean tame);
    void setSitting(boolean sitting);
    boolean isTame();
    boolean isInSittingPose();
    void setInSittingPose(boolean flag);
    static VirtualParrot create() { return null; }
}
