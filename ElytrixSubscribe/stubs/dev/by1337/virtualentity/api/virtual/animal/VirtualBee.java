package dev.by1337.virtualentity.api.virtual.animal;
public interface VirtualBee extends dev.by1337.virtualentity.api.virtual.VirtualEntity {
    void setRolling(boolean rolling);
    boolean isRolling();
    void setHasStung(boolean stung);
    void setAngerEndTime(long endTime);
    void setBaby(boolean baby);
    static VirtualBee create() { return null; }
}
