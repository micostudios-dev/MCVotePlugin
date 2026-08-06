package org.mcvote.server.reward;

public record RewardAction(RewardType type, String argument) {

    public static RewardAction parse(String line) {
        String trimmed = line == null ? "" : line.trim();
        int colon = trimmed.indexOf(':');

        if (colon < 0) {
            return new RewardAction(RewardType.CONSOLE_COMMAND, trimmed);
        }

        RewardType type = RewardType.fromKeyword(trimmed.substring(0, colon));

        return new RewardAction(type, trimmed.substring(colon + 1).trim());
    }
}
