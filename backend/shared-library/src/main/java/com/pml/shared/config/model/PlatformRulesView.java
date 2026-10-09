package com.pml.shared.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Everything an organizer or buyer must obey, assembled from the one settings document: the
 * {@code rules} section, the commission default and minimum payout from {@code payment}, and the
 * approval settings. Contains no personal data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRulesView {

    private PlatformRulesSection rules;

    /** Default commission as a fraction (0.05 = 5%). */
    private Double commissionRate;

    private BigDecimal minimumPayout;

    private int approvalSlaHours;

    private int approvalWarningThresholdHours;

    private boolean autoEscalationEnabled;

    private int escalationDelayHours;

    private boolean requireCommentsOnRejection;

    private boolean requireCommentsOnChangesRequested;

    private boolean allowSelfApproval;

    private Instant updatedAt;

    /** Display name or id of the administrator who last saved; an id is never an email. */
    private String updatedBy;
}
