package net.kyori.adventure.text.format;

public interface Style {
    static Builder style() { return null; }

    interface Builder {
        Builder color(TextColor color);
        Builder decoration(TextDecoration decoration, boolean flag);
        Style build();
    }
}
