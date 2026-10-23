package net.md_5.bungee.api.chat;
public class TextComponent extends BaseComponent {
    public static BaseComponent[] fromLegacyText(String message) { return new BaseComponent[0]; }
    public TextComponent(String text) { }
    public TextComponent() { }
    public String getText() { return ""; }
    public void setText(String text) { }
}
