package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.security.AccountNumberMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/** A payout account's number is shown masked after it has been entered. Flat methods: F-055. */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("A payout account number is never part of a response in full")
class BankAccountFieldsTest {

    @Test
    @DisplayName("the field answers the last four digits only")
    void fieldIsMasked() {
        BankAccount account = new BankAccount();
        account.setAccountNumber("260971234567");
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(account);

        assertThat(new BankAccountFields().accountNumber(dfe)).isEqualTo("****4567").doesNotContain("26097123");
    }

    @Test
    @DisplayName("a short or missing number reveals nothing")
    void shortNumbersRevealNothing() {
        assertThat(AccountNumberMask.of(null)).isEqualTo("****");
        assertThat(AccountNumberMask.of("1234")).isEqualTo("****");
    }
}
