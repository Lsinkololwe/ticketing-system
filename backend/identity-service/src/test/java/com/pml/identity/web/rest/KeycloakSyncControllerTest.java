package com.pml.identity.web.rest;

import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.security.revocation.KeycloakSessionRevoker;
import com.pml.identity.workflow.usersync.UserSyncProcess;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Change;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Keycloak listener's endpoint takes the slim event of CONTRACT 4.6 and nothing else: no
 * attributes, no names, no roles, no phone - and no full-profile endpoint, no open health check.
 */
@Tag("L1")
@Tag("ET-IDN-004")
@Tag("ET-IDN-002")
@Tag("ET-IDN-003")
@DisplayName("ET-IDN-004 · /api/internal/keycloak/sync/event takes the slim event")
class KeycloakSyncControllerTest {

    private UserSyncProcess process;
    private KeycloakSessionRevoker revoker;
    private WebTestClient client;

    @BeforeEach
    void bind() {
        process = Mockito.mock(UserSyncProcess.class);
        when(process.accept(any(), any())).thenReturn(Mono.empty());
        revoker = Mockito.mock(KeycloakSessionRevoker.class);
        when(revoker.revoke(any(), any(), any())).thenReturn(Mono.just(1));
        client = WebTestClient.bindToController(
                new KeycloakSyncController(process, new KeycloakProperties(), revoker))
                .build();
    }

    @Test
    @DisplayName("a slim DELETE of a staff user is accepted as a signal carrying the realm and the listener's event id")
    void slimDelete() {
        client.post().uri("/api/internal/keycloak/sync/event").bodyValue("""
                {"eventId":"evt-1","eventType":"DELETE","userId":"kc-1","username":"ops@example.org",
                 "realm":"myticketzm-admin","enabled":false,"emailVerified":true,"timestamp":1730000000000}
                """).header("Content-Type", "application/json").exchange().expectStatus().isAccepted();

        ArgumentCaptor<Change> change = ArgumentCaptor.forClass(Change.class);
        verify(process).accept(eq("kc-1"), change.capture());
        assertThat(change.getValue().kind()).isEqualTo(Kind.DELETE);
        assertThat(change.getValue().realm()).isEqualTo("myticketzm-admin");
        assertThat(change.getValue().eventId()).isEqualTo("keycloak:evt-1");
        assertThat(change.getValue().occurredAtMillis()).isEqualTo(1730000000000L);
    }

    @Test
    @DisplayName("a login with no realm is a buyer-realm login; the profile events are syncs")
    void kinds() {
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"LOGIN\",\"userId\":\"kc-2\",\"timestamp\":5}").exchange().expectStatus().isAccepted();
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"UPDATE_EMAIL\",\"userId\":\"kc-3\",\"realm\":\"myticketzm\",\"timestamp\":6}")
                .exchange().expectStatus().isAccepted();

        ArgumentCaptor<Change> change = ArgumentCaptor.forClass(Change.class);
        verify(process, Mockito.times(2)).accept(any(), change.capture());
        assertThat(change.getAllValues()).extracting(Change::kind).containsExactly(Kind.LOGIN, Kind.SYNC);
        assertThat(change.getAllValues().get(0).realm()).isNull();
    }

    @Test
    @DisplayName("an event nothing syncs, and a realm identity does not serve, are skipped without a signal")
    void skipped() {
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"UPDATE_CREDENTIAL\",\"userId\":\"kc-1\"}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.action").isEqualTo("SKIPPED");
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"LOGIN\",\"userId\":\"kc-1\",\"realm\":\"master\"}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.action").isEqualTo("SKIPPED");

        verify(process, never()).accept(any(), any());
    }

    @Test
    @DisplayName("the full-profile endpoint and the open health check are gone")
    void removedEndpoints() {
        client.post().uri("/api/internal/keycloak/sync/user-data").header("Content-Type", "application/json")
                .bodyValue("{\"id\":\"kc-1\",\"eventType\":\"REGISTER\"}").exchange().expectStatus().isNotFound();
        client.get().uri("/api/internal/keycloak/sync/health").exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("the event DTO has no attribute, name, role or phone component")
    void theDtoIsSlim() {
        assertThat(java.util.Arrays.stream(com.pml.identity.web.rest.dto.KeycloakEventDto.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder("eventId", "eventType", "userId", "username", "realm", "enabled",
                        "emailVerified", "timestamp", "sid");
    }

    @Test
    @DisplayName("ET-IDN-003-R7 · a Keycloak LOGOUT with a sid revokes that session, answers 202 and signals no user workflow")
    void logoutRevokesTheSession() {
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventId\":\"e1\",\"eventType\":\"LOGOUT\",\"userId\":\"kc-1\",\"realm\":\"myticketzm-admin\","
                        + "\"timestamp\":7,\"sid\":\"sid-1\"}")
                .exchange().expectStatus().isAccepted().expectBody().jsonPath("$.action").isEqualTo("REVOKED");

        verify(revoker).revoke("LOGOUT", "sid-1", "myticketzm-admin");
        verify(process, never()).accept(any(), any());
    }

    @Test
    @DisplayName("ET-IDN-003-R7 · REFRESH_TOKEN_ERROR with a sid revokes it too; with no sid nothing is revoked and the user is never widened")
    void refreshErrorAndMissingSid() {
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"REFRESH_TOKEN_ERROR\",\"userId\":\"kc-1\",\"sid\":\"sid-2\"}")
                .exchange().expectStatus().isAccepted();
        verify(revoker).revoke("REFRESH_TOKEN_ERROR", "sid-2", null);

        Mockito.clearInvocations(revoker);
        when(revoker.revoke(any(), any(), any())).thenReturn(Mono.just(0));
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"LOGOUT\",\"userId\":\"kc-1\"}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.action").isEqualTo("SKIPPED");
        verify(revoker, never()).revoke(any(), any(), any());
    }

    @Test
    @DisplayName("ET-IDN-003-R5 · when the revocation cannot be written the answer is 503, so the listener retries")
    void revocationFailureIsRetryable() {
        when(revoker.revoke(any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("mongo down")));
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"LOGOUT\",\"userId\":\"kc-1\",\"sid\":\"sid-3\"}")
                .exchange().expectStatus().isEqualTo(503);
    }

    @Test
    @DisplayName("ET-IDN-003-R7 · a LOGOUT from a realm identity does not serve is skipped without a revocation")
    void foreignRealmLogout() {
        client.post().uri("/api/internal/keycloak/sync/event").header("Content-Type", "application/json")
                .bodyValue("{\"eventType\":\"LOGOUT\",\"userId\":\"kc-1\",\"realm\":\"master\",\"sid\":\"sid-4\"}")
                .exchange().expectStatus().isOk();
        verify(revoker, never()).revoke(any(), any(), any());
    }

    @Test
    @DisplayName("every endpoint of the controller requires an internal scope or the super-admin role")
    void everyEndpointIsGuarded() {
        for (Method method : KeycloakSyncController.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertThat(method.getAnnotation(PreAuthorize.class)).as(method.getName()).isNotNull();
            }
        }
    }
}
