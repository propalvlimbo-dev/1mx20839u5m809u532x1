package ru.elytrix.duelsbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Кликабельные сообщения в чате: кнопки «Принять», «Отклонить», «Выйти».
 *
 * Строки пишем привычным &-форматом, а Adventure переводит их в компоненты
 * (он есть в Paper начиная с 1.16, наш api-version 1.21).
 */
final class Text {

    private Text() {
    }

    /** Строка с &-кодами → компонент. */
    static Component of(String legacy) {
        if (legacy == null) return null;
        return LegacyComponentSerializer.legacySection()
                .deserialize(ElytrixDuelsBridge.colorize(legacy));
    }

    /** Кнопка: текст + команда по клику + подсказка при наведении. */
    static Component button(String label, String command, String hover) {
        Component part = of(label);
        if (part == null) return null;
        if (command != null && !command.isEmpty()) {
            part = part.clickEvent(ClickEvent.runCommand(command));
        }
        if (hover != null && !hover.isEmpty()) {
            part = part.hoverEvent(HoverEvent.showText(of(hover)));
        }
        return part;
    }

    static void send(Player player, Component component) {
        if (player == null || component == null) return;
        player.sendMessage(component);
    }
}
