package org.mcvote.server.reward;

import java.util.ArrayList;
import java.util.List;

public record RewardBundle(List<RewardAction> actions) {

    public static final RewardBundle EMPTY = new RewardBundle(List.of());

    public static RewardBundle parse(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return EMPTY;
        }

        List<RewardAction> parsed = new ArrayList<>(lines.size());

        for (String line : lines) {
            parsed.add(RewardAction.parse(line));
        }

        return new RewardBundle(List.copyOf(parsed));
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }
}
