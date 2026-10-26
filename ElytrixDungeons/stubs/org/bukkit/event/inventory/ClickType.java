package org.bukkit.event.inventory;
public enum ClickType {
    LEFT, SHIFT_LEFT, RIGHT, SHIFT_RIGHT, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT,
    MIDDLE, NUMBER_KEY, DOUBLE_CLICK, DROP, CONTROL_DROP, CREATIVE, SWAP_OFFHAND, UNKNOWN;
    public boolean isLeftClick() { return false; }
    public boolean isRightClick() { return false; }
    public boolean isShiftClick() { return false; }
}
