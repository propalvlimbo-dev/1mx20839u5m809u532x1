package net.kyori.adventure.text;

import net.kyori.adventure.text.format.Style;

public interface Component {
    static Component empty() { return null; }
    static TextComponent text(String content) { return null; }
    static TextComponent text(String content, Style style) { return null; }
    static TextComponent.Builder text() { return null; }
    Component append(Component component);
}
