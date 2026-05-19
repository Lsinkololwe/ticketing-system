package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.Ticket;
import lombok.extern.slf4j.Slf4j;

/**
 * Field Resolver for Ticket computed fields.
 *
 * Resolves fields that require transformation or computation:
 * - paymentInfo: Maps PaymentInfo embedded object with status conversion
 * - refundInfo: Maps RefundInfo embedded object with status conversion
 *
 * Note: Federated fields (event, buyer) are handled by TicketFieldResolver
 * in the federation package.
 */
@Slf4j
@DgsComponent
public class TicketComputedFieldResolver {

    /**
     * Resolve PaymentInfo.status - convert enum to String for GraphQL.
     */
    @DgsData(parentType = "PaymentInfo", field = "status")
    public String paymentInfoStatus(DgsDataFetchingEnvironment dfe) {
        Ticket.PaymentInfo paymentInfo = dfe.getSource();
        return paymentInfo.getStatus() != null
                ? paymentInfo.getStatus().name()
                : null;
    }

    /**
     * Resolve PaymentInfo.providerReference - map from entity or return null.
     */
    @DgsData(parentType = "PaymentInfo", field = "providerReference")
    public String paymentInfoProviderReference(DgsDataFetchingEnvironment dfe) {
        Ticket.PaymentInfo paymentInfo = dfe.getSource();
        return paymentInfo.getProviderReference();
    }

    /**
     * Resolve RefundInfo.status - convert enum to String for GraphQL.
     */
    @DgsData(parentType = "RefundInfo", field = "status")
    public String refundInfoStatus(DgsDataFetchingEnvironment dfe) {
        Ticket.RefundInfo refundInfo = dfe.getSource();
        return refundInfo.getStatus() != null
                ? refundInfo.getStatus().name()
                : null;
    }

    /**
     * Resolve RefundInfo.transactionId - map from entity.
     */
    @DgsData(parentType = "RefundInfo", field = "transactionId")
    public String refundInfoTransactionId(DgsDataFetchingEnvironment dfe) {
        Ticket.RefundInfo refundInfo = dfe.getSource();
        return refundInfo.getTransactionId();
    }
}
