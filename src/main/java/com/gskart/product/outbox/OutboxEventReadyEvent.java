package com.gskart.product.outbox;

import java.util.List;

// Published within the write transaction and picked up after commit (see OutboxEventListener) to
// trigger the immediate publish path - the scheduled OutboxRelayScheduler sweep is the fallback.
public class OutboxEventReadyEvent {

    private final List<Long> outboxEventIds;

    public OutboxEventReadyEvent(List<Long> outboxEventIds) {
        this.outboxEventIds = outboxEventIds;
    }

    public List<Long> getOutboxEventIds() {
        return outboxEventIds;
    }
}
