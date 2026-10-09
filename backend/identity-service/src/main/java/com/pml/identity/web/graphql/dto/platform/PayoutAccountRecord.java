package com.pml.identity.web.graphql.dto.platform;

import com.pml.identity.domain.enums.MobileMoneyProvider;
import com.pml.identity.service.OrganizationRules.PayoutAccountStatus;
import com.pml.shared.constants.PayoutMethod;

import java.time.Instant;

/**
 * One organization's payout account as the admin review table shows it. Account numbers and
 * phones appear masked only; nothing here can pay anybody.
 */
public record PayoutAccountRecord(
        String organizationId,
        String organizationName,
        String organizationSlug,
        PayoutMethod method,
        PayoutAccountStatus status,
        String bankName,
        String accountNumberMasked,
        String accountHolderName,
        MobileMoneyProvider network,
        String phoneMasked,
        String rejectionReason,
        String suspendedReason,
        Instant testDepositSentAt,
        int verificationAttemptsLeft,
        Instant updatedAt
) {}
