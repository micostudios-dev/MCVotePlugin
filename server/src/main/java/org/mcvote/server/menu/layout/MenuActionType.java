package org.mcvote.server.menu.layout;

public enum MenuActionType {

    OPEN,
    CONSOLE,
    PLAYER,
    MESSAGE,
    CLOSE,
    RELOAD,
    FORCEPARTY,
    NONE;

    public static MenuActionType fromKeyword(String keyword) {
        return switch (keyword == null ? "" : keyword.trim().toLowerCase()) {
            case "open" -> OPEN;
            case "command", "console" -> CONSOLE;
            case "player", "run" -> PLAYER;
            case "message", "msg" -> MESSAGE;
            case "close" -> CLOSE;
            case "reload" -> RELOAD;
            case "forceparty" -> FORCEPARTY;
            default -> NONE;
        };
    }
}
