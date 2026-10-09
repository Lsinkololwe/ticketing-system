package com.pml.keycloak.eventlistener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.keycloak.identity.ContactOtpClient;
import com.pml.keycloak.identity.Dto;
import com.pml.keycloak.identity.IdentityApiException;
import com.pml.keycloak.identity.IdentityUnavailableException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.ArgumentCaptor;

@Tag("ET-IDN-001")
@Tag("ET-IDN-003")
@Tag("layer-1-decision")
class UserSyncEventListenerTest {

    final KeycloakSession session = mock(KeycloakSession.class);
    final ContactOtpClient client = mock(ContactOtpClient.class);
    final List<Duration> pauses = new ArrayList<>();
    final Executor direct = Runnable::run;

    UserSyncEventListener listener(Executor executor) {
        RealmProvider realms = mock(RealmProvider.class);
        UserProvider users = mock(UserProvider.class);
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn("myticketzm");
        UserModel user = mock(UserModel.class);
        when(user.getUsername()).thenReturn("acc-1");
        when(user.isEnabled()).thenReturn(true);
        when(user.isEmailVerified()).thenReturn(false);
        when(session.realms()).thenReturn(realms);
        when(session.users()).thenReturn(users);
        when(realms.getRealm("r1")).thenReturn(realm);
        when(users.getUserById(realm, "kc-1")).thenReturn(user);
        return new UserSyncEventListener(session, client, executor, 4, Duration.ofMillis(10), pauses::add);
    }

    Event login() {
        Event e = new Event();
        e.setType(EventType.LOGIN);
        e.setRealmId("r1");
        e.setUserId("kc-1");
        e.setTime(1730000000000L);
        e.setSessionId("s1");
        return e;
    }

    @Test
    @DisplayName("ET-IDN-001-R11 · payload is exactly the slim contract: no roles, attributes, names or phone")
    void slimPayload() throws Exception {
        listener(direct).onEvent(login());
        ArgumentCaptor<Dto.SyncEvent> cap = ArgumentCaptor.forClass(Dto.SyncEvent.class);
        verify(client).syncEvent(cap.capture());
        JsonNode json = new ObjectMapper().valueToTree(cap.getValue());
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("eventId", "eventType", "userId", "username", "realm",
                "enabled", "emailVerified", "timestamp");
        assertThat(json.get("eventType").asText()).isEqualTo("LOGIN");
        assertThat(json.get("username").asText()).isEqualTo("acc-1");
        assertThat(json.get("realm").asText()).isEqualTo("myticketzm");
        assertThat(json.get("timestamp").asLong()).isEqualTo(1730000000000L);
        assertThat(json.has("sid")).as("a LOGIN carries no session id").isFalse();
    }

    @Test
    @DisplayName("ET-IDN-003-R7 · LOGOUT is forwarded with the sid and user id, and nothing else is added")
    void logoutCarriesSid() throws Exception {
        Event e = login();
        e.setType(EventType.LOGOUT);
        listener(direct).onEvent(e);
        ArgumentCaptor<Dto.SyncEvent> cap = ArgumentCaptor.forClass(Dto.SyncEvent.class);
        verify(client).syncEvent(cap.capture());
        JsonNode json = new ObjectMapper().valueToTree(cap.getValue());
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("eventId", "eventType", "userId", "username", "realm",
                "enabled", "emailVerified", "timestamp", "sid");
        assertThat(json.get("eventType").asText()).isEqualTo("LOGOUT");
        assertThat(json.get("sid").asText()).isEqualTo("s1");
        assertThat(json.get("userId").asText()).isEqualTo("kc-1");
    }

    @Test
    @DisplayName("ET-IDN-003-R7 · REFRESH_TOKEN_ERROR is forwarded with its sid; a session event without a sid or user is dropped")
    void refreshErrorAndMissingSid() {
        UserSyncEventListener l = listener(direct);
        Event e = login();
        e.setType(EventType.REFRESH_TOKEN_ERROR);
        l.onEvent(e);
        ArgumentCaptor<Dto.SyncEvent> cap = ArgumentCaptor.forClass(Dto.SyncEvent.class);
        verify(client).syncEvent(cap.capture());
        assertThat(cap.getValue().eventType()).isEqualTo("REFRESH_TOKEN_ERROR");
        assertThat(cap.getValue().sid()).isEqualTo("s1");

        Event noSid = login();
        noSid.setType(EventType.LOGOUT);
        noSid.setSessionId(null);
        l.onEvent(noSid);
        Event noUser = login();
        noUser.setType(EventType.REFRESH_TOKEN_ERROR);
        noUser.setUserId(null);
        l.onEvent(noUser);
        verify(client, times(1)).syncEvent(any());
    }

    @Test
    @DisplayName("ET-IDN-001-R11 · REGISTER and other non-sync events produce no call")
    void registerIgnored() {
        Event e = login();
        e.setType(EventType.REGISTER);
        listener(direct).onEvent(e);
        verify(client, times(0)).syncEvent(any());
    }

    @Test
    @DisplayName("ET-IDN-001-R11 · the event id is stable across retries and across identical events")
    void retriesWithSameIdempotencyKey() {
        doThrow(new IdentityUnavailableException("down")).doThrow(new IdentityApiException(503, null))
                .doNothing().when(client).syncEvent(any());
        listener(direct).onEvent(login());
        ArgumentCaptor<Dto.SyncEvent> cap = ArgumentCaptor.forClass(Dto.SyncEvent.class);
        verify(client, times(3)).syncEvent(cap.capture());
        assertThat(cap.getAllValues().stream().map(Dto.SyncEvent::eventId).distinct()).hasSize(1);
        assertThat(pauses).containsExactly(Duration.ofMillis(10), Duration.ofMillis(20));
        assertThat(UserSyncEventListener.deterministicId("a", "b")).isEqualTo(UserSyncEventListener.deterministicId("a", "b"));
    }

    @Test
    @DisplayName("ET-IDN-001-R11 · retries are bounded and a 4xx is not retried")
    void boundedAndNoRetryOn4xx() {
        doThrow(new IdentityUnavailableException("down")).when(client).syncEvent(any());
        listener(direct).onEvent(login());
        verify(client, times(4)).syncEvent(any());

        ContactOtpClient other = mock(ContactOtpClient.class);
        doThrow(new IdentityApiException(400, null)).when(other).syncEvent(any());
        new UserSyncEventListener(session, other, direct, 4, Duration.ofMillis(1), d -> { }).onEvent(login());
        verify(other, times(1)).syncEvent(any());
    }

    @Test
    @DisplayName("ET-IDN-001-R12 · failures never reach the login: full queue, client crash, lookup crash")
    void failureIsolation() {
        listener(r -> { throw new RejectedExecutionException("full"); }).onEvent(login());

        doThrow(new IllegalStateException("boom")).when(client).syncEvent(any());
        listener(direct).onEvent(login());

        UserSyncEventListener broken = listener(direct);
        when(session.realms()).thenThrow(new IllegalStateException("db"));
        broken.onEvent(login());          // must not throw
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("ET-IDN-001-R11 · admin user events map to ADMIN_<operation> with the user id from the path")
    void adminEvent() {
        UserSyncEventListener l = listener(direct);
        AdminEvent a = new AdminEvent();
        a.setRealmId("r1");
        a.setResourceType(ResourceType.USER);
        a.setOperationType(OperationType.UPDATE);
        a.setResourcePath("users/kc-1");
        a.setTime(5L);
        l.onEvent(a, true);
        ArgumentCaptor<Dto.SyncEvent> cap = ArgumentCaptor.forClass(Dto.SyncEvent.class);
        verify(client).syncEvent(cap.capture());
        assertThat(cap.getValue().eventType()).isEqualTo("ADMIN_UPDATE");
        assertThat(cap.getValue().userId()).isEqualTo("kc-1");

        a.setResourceType(ResourceType.REALM_ROLE_MAPPING);
        l.onEvent(a, true);
        verify(client, times(1)).syncEvent(any());
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · the factory is fail closed without credentials: a no-op provider and no executor")
    void factoryFailClosed() {
        UserSyncEventListenerFactory factory = new UserSyncEventListenerFactory();
        factory.init(null);                  // the test JVM has no IDENTITY_* variables
        assertThat(factory.create(session)).isNotInstanceOf(UserSyncEventListener.class);
        factory.create(session).onEvent(login());
    }
}
