package org.mcvote.common.net.protocol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

public final class VotifierKeys {

    private static final String ALGORITHM = "RSA";

    private static final String CIPHER = "RSA/ECB/PKCS1Padding";
    private static final int KEY_SIZE = 2048;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final Path directory;
    private final boolean generated;

    private VotifierKeys(KeyPair pair, Path directory, boolean generated) {
        this.publicKey = pair.getPublic();
        this.privateKey = pair.getPrivate();
        this.directory = directory;
        this.generated = generated;
    }

    public static VotifierKeys loadOrCreate(Path dataFolder) throws Exception {
        Path directory = dataFolder.resolve("rsa");
        Path publicFile = directory.resolve("public.key");
        Path privateFile = directory.resolve("private.key");

        if (Files.isRegularFile(publicFile) && Files.isRegularFile(privateFile)) {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            PublicKey pub = factory.generatePublic(new X509EncodedKeySpec(read(publicFile)));
            PrivateKey priv = factory.generatePrivate(new PKCS8EncodedKeySpec(read(privateFile)));

            return new VotifierKeys(new KeyPair(pub, priv), directory, false);
        }

        Files.createDirectories(directory);
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        generator.initialize(KEY_SIZE);
        KeyPair pair = generator.generateKeyPair();

        write(publicFile, pair.getPublic().getEncoded());
        write(privateFile, pair.getPrivate().getEncoded());

        return new VotifierKeys(pair, directory, true);
    }

    public String decrypt(byte[] block) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        return new String(cipher.doFinal(block), StandardCharsets.UTF_8);
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public Path directory() {
        return directory;
    }

    public boolean generated() {
        return generated;
    }

    private static byte[] read(Path file) throws Exception {
        String base64 = Files.readString(file, StandardCharsets.UTF_8).replaceAll("\\s", "");

        return Base64.getDecoder().decode(base64);
    }

    private static void write(Path file, byte[] encoded) throws Exception {
        Files.writeString(file, Base64.getEncoder().encodeToString(encoded), StandardCharsets.UTF_8);
    }
}
