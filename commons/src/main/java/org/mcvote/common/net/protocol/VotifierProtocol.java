package org.mcvote.common.net.protocol;

public final class VotifierProtocol {

    public static final String GREETING_PREFIX = "VOTIFIER 2 ";

    public static final int V2_MAGIC_1 = 0x73;

    public static final int V2_MAGIC_2 = 0x3A;

    public static final int V1_BLOCK_SIZE = 256;

    public static final String V1_OPCODE = "VOTE";

    public static final int MAX_V2_FRAME = 1024;

    public static final String DEFAULT_TOKEN = "default";

    private VotifierProtocol() {
    }
}
