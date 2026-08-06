package org.mcvote.common.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mcvote.api.Vote;
import org.mcvote.common.config.ReceiverConfig;
import org.mcvote.common.net.protocol.MCVoteProtocol;
import org.mcvote.common.net.protocol.VoteSignature;
import org.mcvote.common.net.protocol.VotifierKeys;
import org.mcvote.common.net.protocol.VotifierProtocol;
import org.mcvote.common.net.protocol.VotifierToken;
import org.mcvote.common.platform.UnifiedLogger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class VoteReceiver {

    private static final int SOCKET_TIMEOUT_MS = 5_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReceiverConfig config;
    private final Path dataFolder;
    private final Consumer<Vote> handler;
    private final UnifiedLogger logger;
    private final ExecutorService workers;
    private final ReplayGuard replayGuard;

    private volatile VotifierKeys keys;
    private volatile VotifierToken token;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private Thread acceptThread;

    public VoteReceiver(ReceiverConfig config, Path dataFolder, Consumer<Vote> handler, UnifiedLogger logger) {
        this.config = config;
        this.dataFolder = dataFolder;
        this.handler = handler;
        this.logger = logger;
        this.replayGuard = new ReplayGuard(config.replayWindowMs());
        this.workers = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "MCVote-VoteWorker");
            t.setDaemon(true);

            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void start() throws Exception {
        this.keys = VotifierKeys.loadOrCreate(dataFolder);

        if (keys.generated()) {
            logger.info("Generated a new Votifier RSA key pair in " + keys.directory());
        }

        logger.info("Votifier v1 public key (paste this on the listing site): " + keys.publicKeyBase64());

        this.token = VotifierToken.loadOrCreate(dataFolder);

        if (token.generated()) {
            logger.info("Generated a Votifier token in " + token.file());
        }

        logger.info("Votifier token (paste this on the listing site): " + defaultToken());

        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(config.host().isEmpty()
                ? new InetSocketAddress(config.port())
                : new InetSocketAddress(config.host(), config.port()));
        this.serverSocket = socket;
        this.running = true;

        this.acceptThread = new Thread(this::acceptLoop, "MCVote-VoteReceiver");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();

        logger.info("Vote receiver listening on " + (config.host().isEmpty() ? "*" : config.host())
                + ":" + config.port() + " (Votifier v1, Votifier v2, MCVote legacy)");
    }

    public void stop() {
        running = false;

        ServerSocket socket = serverSocket;

        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }

        workers.shutdownNow();
    }

    public String defaultToken() {
        String configured = config.tokenFor(VotifierProtocol.DEFAULT_TOKEN);

        if (!configured.isEmpty()) {
            return configured;
        }

        VotifierToken generated = token;

        return generated == null ? "" : generated.value();
    }

    public String publicKey() {
        VotifierKeys current = keys;

        return current == null ? "" : current.publicKeyBase64();
    }

    public int boundPort() {
        ServerSocket socket = serverSocket;

        return socket == null ? config.port() : socket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket connection = serverSocket.accept();
                workers.execute(() -> handle(connection));
            } catch (Exception e) {
                if (running) {
                    logger.warn("Vote receiver accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.setTcpNoDelay(true);

            OutputStream out = socket.getOutputStream();
            String challenge = newChallenge();
            out.write((VotifierProtocol.GREETING_PREFIX + challenge + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            int first = in.readUnsignedByte();
            int second = in.readUnsignedByte();

            Vote vote;

            if (first == VotifierProtocol.V2_MAGIC_1 && second == VotifierProtocol.V2_MAGIC_2) {
                vote = readVotifierV2(in, out, challenge);
            } else if (first == MCVoteProtocol.MAGIC_1 && second == MCVoteProtocol.MAGIC_2) {
                vote = readLegacyMcVote(in, out, challenge);
            } else {
                vote = readVotifierV1(in, first, second);
            }

            if (vote != null) {
                handler.accept(vote);
            }
        } catch (Exception e) {
            logger.warn("Vote handling error: " + e.getMessage());
        }
    }

    private Vote readVotifierV2(DataInputStream in, OutputStream out, String challenge) throws Exception {
        int length = in.readUnsignedShort();

        if (length <= 0 || length > VotifierProtocol.MAX_V2_FRAME) {
            respondV2(out, "bad length", "Frame length " + length + " is out of range");

            return null;
        }

        byte[] frame = new byte[length];
        in.readFully(frame);

        JsonObject envelope = JsonParser.parseString(new String(frame, StandardCharsets.UTF_8)).getAsJsonObject();
        String payload = envelope.get("payload").getAsString();
        String signature = envelope.get("signature").getAsString();

        JsonObject body = JsonParser.parseString(payload).getAsJsonObject();
        String service = string(body, "serviceName");

        String expected = config.tokenFor(service);

        if (expected.isEmpty()) {
            expected = defaultToken();
        }

        if (expected.isEmpty()) {
            respondV2(out, "no token", "No token configured for service " + service);

            return null;
        }

        if (!VoteSignature.verify(payload, expected, signature)) {
            respondV2(out, "invalid signature", "Signature does not match the token for " + service);

            return null;
        }

        if (!VoteSignature.constantTimeEquals(challenge, string(body, "challenge"))) {
            respondV2(out, "challenge mismatch", "Challenge does not match the one sent in the greeting");

            return null;
        }

        String username = string(body, "username");

        if (username.isBlank()) {
            respondV2(out, "missing username", "The payload carries no username");

            return null;
        }

        respondV2(out, null, null);

        return vote(service, username, string(body, "address"));
    }

    private Vote readVotifierV1(DataInputStream in, int first, int second) throws Exception {
        byte[] block = new byte[VotifierProtocol.V1_BLOCK_SIZE];
        block[0] = (byte) first;
        block[1] = (byte) second;
        in.readFully(block, 2, block.length - 2);

        if (!replayGuard.accept(block)) {
            logger.warn("Rejected a Votifier v1 block: replay of one already received");

            return null;
        }

        String decrypted;

        try {
            decrypted = keys.decrypt(block);
        } catch (Exception e) {
            logger.warn("Rejected a Votifier v1 block: it is not encrypted with our public key");

            return null;
        }

        String[] lines = decrypted.split("\n");

        if (lines.length < 3 || !VotifierProtocol.V1_OPCODE.equals(lines[0].trim())) {
            logger.warn("Rejected a Votifier v1 block: bad opcode");

            return null;
        }

        String username = lines[2].trim();

        if (username.isBlank()) {
            logger.warn("Rejected a Votifier v1 block: no username");

            return null;
        }

        return vote(lines[1].trim(), username, lines.length > 3 ? lines[3].trim() : "");
    }

    private Vote readLegacyMcVote(DataInputStream in, OutputStream out, String challenge) throws Exception {
        int length = in.readUnsignedShort();

        if (length <= 0 || length > MCVoteProtocol.MAX_FRAME) {
            respondLegacy(out, "error", "bad length");

            return null;
        }

        if (config.apiKey().isEmpty()) {
            respondLegacy(out, "error", "no api key configured");

            return null;
        }

        byte[] frame = new byte[length];
        in.readFully(frame);

        JsonObject envelope = JsonParser.parseString(new String(frame, StandardCharsets.UTF_8)).getAsJsonObject();
        String payload = envelope.get("payload").getAsString();
        String signature = envelope.get("signature").getAsString();

        if (!VoteSignature.verify(payload, config.apiKey(), signature)) {
            respondLegacy(out, "error", "invalid signature");

            return null;
        }

        JsonObject body = JsonParser.parseString(payload).getAsJsonObject();

        if (!VoteSignature.constantTimeEquals(challenge, string(body, "challenge"))) {
            respondLegacy(out, "error", "challenge mismatch");

            return null;
        }

        String username = string(body, "username");

        if (username.isBlank()) {
            respondLegacy(out, "error", "missing username");

            return null;
        }

        String service = body.has("service") ? string(body, "service") : "MCVote";
        respondLegacy(out, "ok", null);

        return vote(service, username, string(body, "address"));
    }

    private static Vote vote(String service, String username, String address) {
        return new Vote(service.isBlank() ? "MCVote" : service, username, address, System.currentTimeMillis());
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static void respondV2(OutputStream out, String cause, String error) throws Exception {
        JsonObject response = new JsonObject();

        if (cause == null) {
            response.addProperty("status", "ok");
        } else {
            response.addProperty("status", "error");
            response.addProperty("cause", cause);
            response.addProperty("error", error);
        }

        out.write((response + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void respondLegacy(OutputStream out, String status, String cause) throws Exception {
        JsonObject response = new JsonObject();
        response.addProperty("status", status);

        if (cause != null) {
            response.addProperty("cause", cause);
        }

        out.write((response + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String newChallenge() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
