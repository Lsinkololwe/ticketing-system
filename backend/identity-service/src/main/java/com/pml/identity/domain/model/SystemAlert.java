package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.domain.enums.AlertStatus;
import com.pml.identity.persistence.IdentityCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An operational condition somebody should look at. One alert is open per {@code (source, key)}:
 * a condition that keeps recurring raises its {@code occurrences} instead of filling the list.
 */
@Document(collection = IdentityCollections.SYSTEM_ALERTS)
@TypeAlias("system_alerts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SystemAlert {

    @Id
    private String id;

    /** The service or probe that raised it, e.g. {@code health-probe}, {@code booking-service}. */
    private String source;

    /** Identifies the condition within the source, e.g. {@code service-down:catalog}. */
    private String key;

    private AlertSeverity severity;

    private String title;

    private String message;

    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @Builder.Default
    private int occurrences = 1;

    private Instant raisedAt;

    private Instant lastSeenAt;

    private Instant acknowledgedAt;

    private String acknowledgedBy;

    private Instant resolvedAt;
}
