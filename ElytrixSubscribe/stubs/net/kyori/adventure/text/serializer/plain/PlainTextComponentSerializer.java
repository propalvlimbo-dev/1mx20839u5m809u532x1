package net.kyori.adventure.text.serializer.plain;
import net.kyori.adventure.text.Component;
public final class PlainTextComponentSerializer {
    public static PlainTextComponentSerializer plainText() { return new PlainTextComponentSerializer(); }
    public String serialize(Component component) { return ""; }
}
