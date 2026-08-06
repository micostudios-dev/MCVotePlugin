package org.mcvote.common.storage.model;

public enum DeliveryType {

    VOTE,

    STREAK,

    PARTY;

    public static DeliveryType from(String raw) {
        return switch (raw == null ? "" : raw.toUpperCase()) {
            case "STREAK" -> STREAK;
            case "PARTY" -> PARTY;
            default -> VOTE;
        };
    }
}
