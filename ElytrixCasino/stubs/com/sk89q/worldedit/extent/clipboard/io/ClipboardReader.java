package com.sk89q.worldedit.extent.clipboard.io;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
public interface ClipboardReader extends java.io.Closeable {
    Clipboard read() throws java.io.IOException;
}
