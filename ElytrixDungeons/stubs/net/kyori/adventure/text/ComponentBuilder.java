package net.kyori.adventure.text;

public interface ComponentBuilder<C extends BuildableComponent<C, B>, B extends ComponentBuilder<C, B>> {
    B append(Component component);
    C build();
}
