package net.kyori.adventure.text.event;

import net.kyori.adventure.text.Component;

/** Заглушка Adventure HoverEvent — нужна только для компиляции. */
public final class HoverEvent<V> implements HoverEventSource<V> {

    public static HoverEvent<Component> showText(Component text) {
        return null;
    }
}
