package io.netty.channel;

public interface EventLoop {
    void execute(Runnable command);
}
