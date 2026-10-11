package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.security.AccountNumberMask;

/**
 * A payout account's number is entered once and shown masked afterwards, to everyone who may see the
 * account at all. The full number stays in the record; it is never part of a response.
 */
@DgsComponent
public class BankAccountFields {

    @DgsData(parentType = "BankAccount", field = "accountNumber")
    public String accountNumber(DgsDataFetchingEnvironment dfe) {
        BankAccount account = dfe.getSource();
        return AccountNumberMask.of(account.getAccountNumber());
    }
}
