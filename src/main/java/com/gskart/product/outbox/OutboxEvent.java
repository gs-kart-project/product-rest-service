package com.gskart.product.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    // Plain auto_increment rather than the products/categories "_seq" table convention: this
    // table is high-churn (one row per write) and doesn't need cross-service id coordination.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;
    private Long aggregateId;
    private String topic;

    @Lob
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    private int attempts;

    private OffsetDateTime createdOn;
    private OffsetDateTime sentOn;

    // Set when the row is claimed (PENDING -> IN_PROGRESS). Used to detect rows stuck IN_PROGRESS
    // by a crash between claim and mark-outcome (OutboxEventStore#reclaimStuckInProgress, M3 fix).
    private OffsetDateTime claimedOn;

    // Optimistic lock: the row's claim (PENDING -> IN_PROGRESS) is how the immediate AFTER_COMMIT
    // publish and the scheduled fallback sweep avoid double-sending the same event (see OutboxRelay).
    @Version
    @Column(name = "version")
    private Long version;

    public enum OutboxStatus {
        PENDING,
        IN_PROGRESS,
        SENT,
        FAILED
    }
}
