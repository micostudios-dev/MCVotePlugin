package org.mcvote.common.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mcvote.api.Vote;
import org.mcvote.common.config.ReceiverConfig;
import org.mcvote.common.net.protocol.MCVoteProtocol;
import org.mcvote.common.net.protocol.VoteSignature;
import org.mcvote.common.net.protocol.VotifierProtocol;
import org.mcvote.common.platform.UnifiedLogger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;

class VoteReceiverProtocolTest {

    private static final String TOKEN = "a-very-long-default-token";

    @TempDir
    Path dataFolder;

    private final BlockingQueue<Vote> votes = new LinkedBlockingQueue<>();
    private VoteReceiver receiver;

    @AfterEach
    void tearDown() {
        if (receiver != null) {
            receiver.stop();
        }
    }

    private int start(ReceiverConfig config) throws Exception {
        receiver = new VoteReceiver(config, dataFolder, votes::add, new SilentLogger());
        receiver.start();

        return receiver.boundPort();
    }

    private ReceiverConfig config() {
        return new ReceiverConfig(true, "127.0.0.1", 0, Map.of("default", TOKEN), "api-key", 300_000L);
    }

    private Vote awaitVote() throws Exception {
        return votes.poll(5, TimeUnit.SECONDS);
    }

    @Test
    void acceptsVotifierV2Vote() throws Exception {
        int port = start(config());

        long before = System.currentTimeMillis();
        JsonObject reply;

        try (Socket socket = new Socket("127.0.0.1", port)) {
            String challenge = greeting(socket);
            reply = sendV2(socket, payload("MinecraftMP", "Notch", "1.2.3.4", challenge, 1L), TOKEN);
        }

        assertEquals("ok", reply.get("status").getAsString());

        Vote vote = awaitVote();
        assertNotNull(vote);
        assertEquals("MinecraftMP", vote.serviceName());
        assertEquals("Notch", vote.username());
        assertEquals("1.2.3.4", vote.address());
        assertTrue(vote.timestamp() >= before, "timestamp must come from the server clock");
    }

    @Test
    void rejectsV2WithAWrongToken() throws Exception {
        int port = start(config());

        try (Socket socket = new Socket("127.0.0.1", port)) {
            String challenge = greeting(socket);
            JsonObject reply = sendV2(socket, payload("MinecraftMP", "Notch", "", challenge, 0L), "not-the-token");
            assertEquals("error", reply.get("status").getAsString());
            assertEquals("invalid signature", reply.get("cause").getAsString());
        }

        assertNull(awaitVote());
    }

    @Test
    void rejectsV2ReplayedWithAnotherConnectionsChallenge() throws Exception {
        int port = start(config());

        try (Socket socket = new Socket("127.0.0.1", port)) {
            greeting(socket);
            JsonObject reply = sendV2(socket, payload("MinecraftMP", "Notch", "", "stolen-challenge", 0L), TOKEN);
            assertEquals("challenge mismatch", reply.get("cause").getAsString());
        }

        assertNull(awaitVote());
    }

    @Test
    void generatesATokenWhenTheConfigHasNone() throws Exception {
        ReceiverConfig empty = new ReceiverConfig(true, "127.0.0.1", 0, Map.of(), "", 300_000L);
        int port = start(empty);

        String generated = Files.readString(dataFolder.resolve("token.txt")).trim();
        assertFalse(generated.isEmpty(), "a token must be generated on first start");
        assertEquals(generated, receiver.defaultToken());

        try (Socket socket = new Socket("127.0.0.1", port)) {
            String challenge = greeting(socket);
            JsonObject reply = sendV2(socket,
                    payload("MinecraftMP", "Notch", "", challenge, 0L), generated);
            assertEquals("ok", reply.get("status").getAsString());
        }

        assertNotNull(awaitVote());
    }

    @Test
    void theShippedPlaceholderIsNeverAValidCredential() throws Exception {
        ReceiverConfig untouched = new ReceiverConfig(true, "127.0.0.1", 0, Map.of(), "CHANGE_ME", 300_000L);
        int port = start(untouched);

        assertNotEquals("CHANGE_ME", receiver.defaultToken());

        try (Socket socket = new Socket("127.0.0.1", port)) {
            String challenge = greeting(socket);
            JsonObject reply = sendV2(socket,
                    payload("MinecraftMP", "Notch", "", challenge, 0L), "CHANGE_ME");
            assertEquals("error", reply.get("status").getAsString());
        }

        assertNull(awaitVote());
    }

    @Test
    void acceptsClassicVotifierV1Vote() throws Exception {
        int port = start(config());
        byte[] block = v1Block("VOTE\nTopG\nJeb_\n5.6.7.8\n1\n");

        long before = System.currentTimeMillis();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            greeting(socket);
            socket.getOutputStream().write(block);
            socket.getOutputStream().flush();
        }

        Vote vote = awaitVote();
        assertNotNull(vote);
        assertEquals("TopG", vote.serviceName());
        assertEquals("Jeb_", vote.username());
        assertEquals("5.6.7.8", vote.address());
        assertTrue(vote.timestamp() >= before, "timestamp must come from the server clock");
    }

    @Test
    void rejectsAReplayedV1Block() throws Exception {
        int port = start(config());
        byte[] block = v1Block("VOTE\nTopG\nJeb_\n5.6.7.8\n1\n");

        for (int i = 0; i < 2; i++) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                greeting(socket);
                socket.getOutputStream().write(block);
                socket.getOutputStream().flush();
            }
        }

        assertNotNull(awaitVote());
        assertNull(votes.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void stillAcceptsTheLegacyMcVoteFrame() throws Exception {
        int port = start(config());

        try (Socket socket = new Socket("127.0.0.1", port)) {
            String challenge = greeting(socket);

            JsonObject body = new JsonObject();
            body.addProperty("username", "Dinnerbone");
            body.addProperty("address", "9.9.9.9");
            body.addProperty("timestamp", 1L);
            body.addProperty("challenge", challenge);
            body.addProperty("service", "MCVote");
            String payload = body.toString();

            JsonObject envelope = new JsonObject();
            envelope.addProperty("payload", payload);
            envelope.addProperty("signature", VoteSignature.sign(payload, "api-key"));

            JsonObject reply = sendFrame(socket, MCVoteProtocol.MAGIC_1, MCVoteProtocol.MAGIC_2, envelope.toString());
            assertEquals("ok", reply.get("status").getAsString());
        }

        Vote vote = awaitVote();
        assertNotNull(vote);
        assertEquals("Dinnerbone", vote.username());
    }

    private static String greeting(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();
        assertNotNull(line);
        String[] fields = line.trim().split(" ");
        assertEquals("VOTIFIER", fields[0]);
        assertEquals("2", fields[1]);

        return fields[2];
    }

    private static String payload(String service, String username, String address, String challenge, long timestamp) {
        JsonObject body = new JsonObject();
        body.addProperty("serviceName", service);
        body.addProperty("username", username);
        body.addProperty("address", address);
        body.addProperty("timestamp", timestamp);
        body.addProperty("challenge", challenge);

        return body.toString();
    }

    private static JsonObject sendV2(Socket socket, String payload, String token) throws Exception {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("payload", payload);
        envelope.addProperty("signature", VoteSignature.sign(payload, token));

        return sendFrame(socket, VotifierProtocol.V2_MAGIC_1, VotifierProtocol.V2_MAGIC_2, envelope.toString());
    }

    private static JsonObject sendFrame(Socket socket, int magic1, int magic2, String json) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeByte(magic1);
        out.writeByte(magic2);
        out.writeShort(body.length);
        out.write(body);
        out.flush();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();
        assertNotNull(line, "the receiver sent no reply");

        return JsonParser.parseString(line).getAsJsonObject();
    }

    private byte[] v1Block(String contents) throws Exception {
        String base64 = Files.readString(dataFolder.resolve("rsa").resolve("public.key")).replaceAll("\\s", "");
        PublicKey key = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return cipher.doFinal(contents.getBytes(StandardCharsets.UTF_8));
    }

    private static final class SilentLogger implements UnifiedLogger {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }
    }
}
