package org.mcvote.common.platform;

import java.util.List;

@FunctionalInterface
public interface OnlinePlayers {

    List<PlayerRef> online();
}
