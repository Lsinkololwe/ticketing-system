package com.pml.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The line an administrator's action leaves behind: who, what, on which record, why. One logger name
 * and a fixed {@code key=value} shape so the log pipeline can route and retain these separately from
 * ordinary logs. The durable, queryable trail is the owning document's own history (a payout's hold
 * history, a proposal's proposer and confirmer, a transfer's executor); this is the record that cannot
 * be edited after the fact.
 */
public final class AdminAudit {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.pml.audit.booking");

    private AdminAudit() {
    }

    public static void record(String action, String actorId, String subjectType, String subjectId, String detail) {
        AUDIT.info("AUDIT action={} actor={} subjectType={} subjectId={} detail={}", action, actorId, subjectType, subjectId,
                detail == null ? "" : detail.replaceAll("[\\r\\n]+", " "));
    }
}
