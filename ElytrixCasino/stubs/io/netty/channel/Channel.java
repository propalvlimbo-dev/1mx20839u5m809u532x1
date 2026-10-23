package io.netty.channel;

public interface Channel {
    EventLoop eventLoop();
    ChannelPipeline pipeline();
}
