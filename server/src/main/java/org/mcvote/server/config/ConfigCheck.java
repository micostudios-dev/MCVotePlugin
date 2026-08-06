package org.mcvote.server.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mcvote.common.platform.UnifiedLogger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConfigCheck {

    private ConfigCheck() {
    }

    public static void run(File configFile, UnifiedLogger logger) {
        if (configFile == null || !configFile.isFile()) {
            return;
        }

        if (!isUtf8(configFile)) {
            logger.error("config.yml is not UTF-8, using bundled defaults. Re-save it as UTF-8 without BOM.");
            return;
        }

        YamlConfiguration raw = new YamlConfiguration();
        try {
            raw.load(configFile);
        } catch (InvalidConfigurationException e) {
            logger.error("config.yml is not valid YAML, using bundled defaults: " + firstLine(e.getMessage()));
            return;
        } catch (Exception e) {
            logger.error("Could not read config.yml", e);
            return;
        }

        List<String> problems = new ArrayList<>();
        checkRewardList(raw, "rewards.vote", problems);
        checkRewardList(raw, "rewards.party", problems);
        checkStreakRewards(raw, problems);

        if (problems.isEmpty()) {
            return;
        }
        logger.warn("config.yml, using bundled defaults for: " + String.join("; ", problems));
    }

    private static void checkRewardList(YamlConfiguration raw, String path, List<String> problems) {
        if (!raw.isSet(path)) {
            problems.add(path + " (missing)");
        } else if (!raw.isList(path)) {
            problems.add(path + " (not a list, each line needs a leading \"- \")");
        }
    }

    private static void checkStreakRewards(YamlConfiguration raw, List<String> problems) {
        if (!raw.isSet("rewards.streak")) {
            problems.add("rewards.streak (missing)");
            return;
        }
        ConfigurationSection rewards = raw.getConfigurationSection("rewards.streak");
        if (rewards == null) {
            problems.add("rewards.streak (not a section of tier ids)");
            return;
        }

        for (String id : rewards.getKeys(false)) {
            if (!rewards.isList(id)) {
                problems.add("rewards.streak." + id + " (not a list, each line needs a leading \"- \")");
            }
        }

        ConfigurationSection tiers = raw.getConfigurationSection("streaks.tiers");
        if (tiers == null) {
            return;
        }
        Set<String> tierIds = new LinkedHashSet<>(tiers.getKeys(false));
        for (String id : rewards.getKeys(false)) {
            if (!tierIds.contains(id)) {
                problems.add("rewards.streak." + id + " (no such id in streaks.tiers, never fires)");
            }
        }
        for (String id : tierIds) {
            if (!rewards.isSet(id)) {
                problems.add("streaks.tiers." + id + " (no rewards.streak." + id + ")");
            }
        }
    }

    private static boolean isUtf8(File file) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(Files.readAllBytes(file.toPath())));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
