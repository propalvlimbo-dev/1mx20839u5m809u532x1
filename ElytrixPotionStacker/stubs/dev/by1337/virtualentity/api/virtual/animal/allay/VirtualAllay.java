package dev.by1337.virtualentity.api.virtual.animal.allay;
public interface VirtualAllay extends dev.by1337.virtualentity.api.virtual.VirtualEntity {
    void setDancing(boolean dancing);
    boolean isDancing();
    boolean isCanDuplicate();
    void setCanDuplicate(boolean canDuplicate);
    static VirtualAllay create() { return null; }
}
