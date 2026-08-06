package org.mcvote.common.storage.model;

public record PendingDelivery(
        long id,
        String username,
        DeliveryType type,
        String context,
        long createdMs
) {
}
