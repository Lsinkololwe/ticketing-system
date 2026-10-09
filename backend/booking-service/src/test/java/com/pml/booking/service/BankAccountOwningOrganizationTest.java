package com.pml.booking.service;

import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient.OrganizationMembershipInfo;
import com.pml.booking.infrastructure.client.IdentityServiceClient.UserOrganizationsResponse;
import com.pml.booking.repository.BankAccountRepository;
import com.pml.booking.service.impl.BankAccountServiceImpl;
import com.pml.booking.web.graphql.dto.CreateBankAccountInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The validator requires organizationId on every bank account; the client never supplies it. */
@Tag("L1")
@Tag("ET-FIN-001")
@DisplayName("ET-FIN-001 · a new bank account is attached to the organization the caller owns")
class BankAccountOwningOrganizationTest {

    @Test
    void createResolvesTheOwnedOrganization() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        IdentityServiceClient identity = mock(IdentityServiceClient.class);
        when(identity.getUserOrganizations("u1")).thenReturn(Mono.just(new UserOrganizationsResponse(List.of(
                new OrganizationMembershipInfo("6ac424255e2b6cdaab5ce55e", "MANAGER", true),
                new OrganizationMembershipInfo("6ac424255e2b6cdaab5ce55d", "OWNER", true)))));
        when(repository.save(any(BankAccount.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        CreateBankAccountInput input = new CreateBankAccountInput("u1", "E2E Events Ltd", "Zanaco", null, null, null,
                "0123456789012", null, "ZMW", null, null);

        StepVerifier.create(new BankAccountServiceImpl(repository, identity).create(input, "u1"))
                .expectNextCount(1).verifyComplete();

        ArgumentCaptor<BankAccount> saved = ArgumentCaptor.forClass(BankAccount.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo("6ac424255e2b6cdaab5ce55d");
        assertThat(saved.getValue().getOrganizerId()).isEqualTo("u1");
    }
}
