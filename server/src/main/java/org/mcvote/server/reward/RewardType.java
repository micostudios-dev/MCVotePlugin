package org.mcvote.server.reward;

public enum RewardType {

    CONSOLE_COMMAND,
    PLAYER_COMMAND,
    MESSAGE,
    BROADCAST,
    ITEM,
    SOUND;

    public static RewardType fromKeyword(String keyword) {
        return switch (keyword.toLowerCase().trim()) {
            case "player", "player-command", "run" -> PLAYER_COMMAND;
            case "message", "msg", "tell" -> MESSAGE;
            case "broadcast", "announce" -> BROADCAST;
            case "item", "give" -> ITEM;
            case "sound" -> SOUND;
            default -> CONSOLE_COMMAND;
        };
    }
}
