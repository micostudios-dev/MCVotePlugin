package org.mcvote.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiniMessageText {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private static final Pattern MINI_TAG = Pattern.compile("<[#/a-zA-Z]");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");

    private static final String[] LEGACY_TAGS = new String[128];

    static {
        String[] colours = {
                "0", "black", "1", "dark_blue", "2", "dark_green", "3", "dark_aqua",
                "4", "dark_red", "5", "dark_purple", "6", "gold", "7", "gray",
                "8", "dark_gray", "9", "blue", "a", "green", "b", "aqua",
                "c", "red", "d", "light_purple", "e", "yellow", "f", "white",
                "k", "obfuscated", "l", "bold", "m", "strikethrough", "n", "underlined",
                "o", "italic", "r", "reset"
        };
        for (int i = 0; i < colours.length; i += 2) {
            char code = colours[i].charAt(0);
            LEGACY_TAGS[code] = "<" + colours[i + 1] + ">";
            LEGACY_TAGS[Character.toUpperCase(code)] = "<" + colours[i + 1] + ">";
        }
    }

    private MiniMessageText() {
    }

    public static Component render(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(MINI_TAG.matcher(raw).find() ? raw : fromLegacyCodes(raw));
    }

    public static String toLegacySection(String raw) {
        return SECTION.serialize(render(raw));
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("<", "\\<").replace("'", "\\'");
    }

    private static String fromLegacyCodes(String raw) {
        Matcher matcher = LEGACY_CODE.matcher(raw);
        StringBuilder out = new StringBuilder(raw.length() + 16);
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(LEGACY_TAGS[matcher.group(1).charAt(0)]));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
