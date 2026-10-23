package net.kyori.adventure.text.serializer.legacy;

import net.kyori.adventure.text.TextComponent;

/** Заглушка Adventure LegacyComponentSerializer — нужна только для компиляции. */
public interface LegacyComponentSerializer {

    static LegacyComponentSerializer legacySection() {
        return null;
    }

    TextComponent deserialize(String input);
}
