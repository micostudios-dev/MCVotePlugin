package org.mcvote.common.net.protocol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

public record VotifierToken(String value, boolean generated, Path file) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String FILE_NAME = "token.txt";

    public static VotifierToken loadOrCreate(Path dataFolder) throws Exception {
        Path file = dataFolder.resolve(FILE_NAME);

        if (Files.isRegularFile(file)) {
            String existing = Files.readString(file, StandardCharsets.UTF_8).trim();

            if (!existing.isEmpty()) {
                return new VotifierToken(existing, false, file);
            }
        }

        Files.createDirectories(dataFolder);
        String token = generate();
        Files.writeString(file, token, StandardCharsets.UTF_8);

        return new VotifierToken(token, true, file);
    }

    private static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
