package net.kyori.adventure.text;

public interface TextComponent extends BuildableComponent<TextComponent, TextComponent.Builder> {
    String content();

    interface Builder extends ComponentBuilder<TextComponent, Builder> {
    }
}
