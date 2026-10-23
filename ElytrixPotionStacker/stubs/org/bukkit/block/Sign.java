package org.bukkit.block;
public interface Sign extends BlockState {
    boolean isWritable();
    void setWritable(boolean writable);
    String[] getLines();
    void setLine(int index, String line);
}
