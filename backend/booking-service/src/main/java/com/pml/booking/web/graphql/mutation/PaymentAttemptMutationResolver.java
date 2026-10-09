package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.service.PaymentAttemptService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * GraphQL mutations on payment attempts: an operator's notes and review status only.
 *
 * <p>A payment's lifecycle — starting it, applying the provider's answer, expiring it — belongs to
 * the reservation's {@code PurchaseWorkflow}. Nothing here moves a
 * payment's status. The actor is always the authenticated caller (OWASP A01).
 */
@Slf4j
@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class PaymentAttemptMutationResolver {

    private final PaymentAttemptService paymentAttemptService;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttempt> addPaymentAttemptNote(
            @InputArgument String depositId,
            @InputArgument String note
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(author -> log.info("GraphQL mutation: addPaymentAttemptNote(depositId={}, author={})", depositId, author))
                .flatMap(author -> paymentAttemptService.addNote(depositId, author, note));
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttempt> setPaymentAttemptReviewStatus(
            @InputArgument String depositId,
            @InputArgument String reviewStatus,
            @InputArgument String notes
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewedBy -> log.info("GraphQL mutation: setPaymentAttemptReviewStatus(depositId={}, status={}, reviewedBy={})",
                        depositId, reviewStatus, reviewedBy))
                .flatMap(reviewedBy -> paymentAttemptService.setReviewStatus(depositId, reviewStatus, reviewedBy, notes));
    }
}
