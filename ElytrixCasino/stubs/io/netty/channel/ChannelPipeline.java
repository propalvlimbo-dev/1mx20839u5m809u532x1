package io.netty.channel;

import java.util.List;

public interface ChannelPipeline {
    ChannelHandler get(String name);
    ChannelHandler remove(String name);
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);
    ChannelPipeline addLast(String name, ChannelHandler handler);
    List<String> names();
}
