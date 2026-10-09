package com.pml.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.PendingApprovalStatsService;
import com.pml.identity.service.UserService;
import com.pml.identity.service.UserStatsService;
import com.pml.identity.web.graphql.query.UserQueryResolver;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("a profile request is answered only for an active account with nothing pending")
class AccountRequestGateTest {

    private static User account(AccountState state, PendingKind pending) {
        User user = new User();
        user.setId("acct-1");
        user.setStatus(state);
        user.setPendingKind(pending);
        user.setFirstName("Chanda");
        return user;
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(AccountState.SUSPENDED, null, ErrorCode.ACCOUNT_SUSPENDED),
                Arguments.of(AccountState.PROVISIONING, null, ErrorCode.ACCOUNT_NOT_ACTIVE),
                Arguments.of(AccountState.MERGED, null, ErrorCode.ACCOUNT_NOT_ACTIVE),
                Arguments.of(AccountState.DELETED, null, ErrorCode.ACCOUNT_NOT_ACTIVE),
                Arguments.of(AccountState.ACTIVE, PendingKind.MERGING, ErrorCode.ACCOUNT_MERGING),
                Arguments.of(AccountState.SUSPENDED, PendingKind.MERGING, ErrorCode.ACCOUNT_MERGING));
    }

    @ParameterizedTest(name = "{0} / pending {1} is refused with {2}")
    @MethodSource("refusals")
    void refused(AccountState state, PendingKind pending, ErrorCode expected) {
        assertThatThrownBy(() -> AccountRequestGate.admit(account(state, pending)).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal -> {
                    assertThat(refusal.errorCode()).isEqualTo(expected);
                    assertThat(refusal.details()).as("a refusal carries no profile field").isEmpty();
                });
    }

    @Test
    @DisplayName("an active account, or one with a change in progress, is admitted")
    void admitted() {
        assertThat(AccountRequestGate.admit(account(AccountState.ACTIVE, null)).block()).isNotNull();
        assertThat(AccountRequestGate.admit(account(AccountState.ACTIVE, PendingKind.CHANGING)).block()).isNotNull();
    }

    @Test
    @DisplayName("an account written before the status field existed is read from its legacy status")
    void legacyAccountWithoutStatus() {
        User legacy = new User();
        legacy.setId("legacy-1");
        assertThat(AccountRequestGate.admit(legacy).block()).isNotNull();
    }

    @Test
    @DisplayName("me() returns the profile of an active account and only the typed refusal for a suspended one")
    void meUsesTheGate() {
        UserService users = mock(UserService.class);
        UserQueryResolver resolver = new UserQueryResolver(users, mock(UserStatsService.class),
                mock(PendingApprovalStatsService.class));
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("acct-1").claim("accountId", "acct-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var auth = new JwtAuthenticationToken(jwt, java.util.List.of());

        when(users.findBySubject(any())).thenReturn(Mono.just(account(AccountState.ACTIVE, null)));
        User served = resolver.me().contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)).block();
        assertThat(served.getFirstName()).isEqualTo("Chanda");

        when(users.findBySubject(any())).thenReturn(Mono.just(account(AccountState.SUSPENDED, null)));
        assertThatThrownBy(() -> resolver.me().contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.ACCOUNT_SUSPENDED));
        verify(users, org.mockito.Mockito.times(2)).findBySubject(any());
    }
}
