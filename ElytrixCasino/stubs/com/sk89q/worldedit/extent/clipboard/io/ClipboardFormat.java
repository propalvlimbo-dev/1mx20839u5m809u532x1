package com.sk89q.worldedit.extent.clipboard.io;
public interface ClipboardFormat {
    String getName();
    java.util.Set<String> getAliases();
    ClipboardReader getReader(java.io.InputStream input) throws java.io.IOException;
    boolean isFormat(java.io.File file);
}
