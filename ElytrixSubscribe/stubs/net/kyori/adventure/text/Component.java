package net.kyori.adventure.text;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEventSource;

/** Заглушка Adventure Component — нужна только для компиляции. */
public interface Component {
    Component append(Component component);
    Component clickEvent(ClickEvent event);
    Component hoverEvent(HoverEventSource<?> source);
}
