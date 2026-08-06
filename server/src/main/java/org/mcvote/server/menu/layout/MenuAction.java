package org.mcvote.server.menu.layout;

public record MenuAction(MenuActionType type, String argument) {

    public static MenuAction parse(String line) {
        String trimmed = line == null ? "" : line.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return new MenuAction(MenuActionType.fromKeyword(trimmed), "");
        }
        return new MenuAction(MenuActionType.fromKeyword(trimmed.substring(0, colon)),
                trimmed.substring(colon + 1).trim());
    }
}
