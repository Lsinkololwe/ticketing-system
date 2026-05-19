package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Field Resolver for PayoutRequest type.
 *
 * <h2>Business Context</h2>
 * Resolves the bankAccount field on PayoutRequest entities. When a payout request
 * is queried, the bank account details are fetched via this resolver to display
 * the destination account information.
 *
 * <h2>Usage</h2>
 * This resolver is automatically invoked by the GraphQL framework when the
 * bankAccount field is requested on a PayoutRequest query.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PayoutRequestFieldResolver {

    private final BankAccountService bankAccountService;

    /**
     * Resolve PayoutRequest.bankAccount from bankAccountId.
     *
     * @param dfe DataFetchingEnvironment containing the parent PayoutRequest
     * @return BankAccount entity or null if not found
     */
    @DgsData(parentType = "PayoutRequest", field = "bankAccount")
    public CompletableFuture<BankAccount> bankAccount(DgsDataFetchingEnvironment dfe) {
        PayoutRequest payoutRequest = dfe.getSource();

        if (payoutRequest.getBankAccountId() == null) {
            log.debug("No bankAccountId on PayoutRequest {}", payoutRequest.getId());
            return CompletableFuture.completedFuture(null);
        }

        log.debug("Resolving bankAccount {} for PayoutRequest {}",
                payoutRequest.getBankAccountId(), payoutRequest.getId());

        return bankAccountService.findById(payoutRequest.getBankAccountId())
                .toFuture();
    }
}
