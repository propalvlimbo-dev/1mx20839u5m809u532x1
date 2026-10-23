package net.md_5.bungee.api.chat;

public final class ClickEvent {
    public enum Action {
        OPEN_URL, OPEN_FILE, RUN_COMMAND, SUGGEST_COMMAND, CHANGE_PAGE, COPY_TO_CLIPBOARD
    }
    private final Action action;
    private final String value;
    public ClickEvent(Action action, String value) {
        this.action = action;
        this.value = value;
    }
    public Action getAction() { return action; }
    public String getValue() { return value; }
}
