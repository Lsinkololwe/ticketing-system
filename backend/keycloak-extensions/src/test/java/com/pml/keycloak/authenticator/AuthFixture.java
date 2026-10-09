package com.pml.keycloak.authenticator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.managers.BruteForceProtector;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.Answers;
import org.mockito.Mockito;

/** Hand-rolled fake of the flow context: notes are real, everything else is a recording mock. */
final class AuthFixture {

    final AuthenticationFlowContext ctx = mock(AuthenticationFlowContext.class);
    final AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
    final KeycloakSession session = mock(KeycloakSession.class);
    final UserProvider users = mock(UserProvider.class);
    final RealmModel realm = mock(RealmModel.class);
    final EventBuilder event = mock(EventBuilder.class, Answers.RETURNS_SELF);
    final BruteForceProtector protector = mock(BruteForceProtector.class);
    final Response page = mock(Response.class);
    final MultivaluedHashMap<String, String> formData = new MultivaluedHashMap<>();
    final Map<String, String> authNotes = new HashMap<>();
    final Map<String, String> clientNotes = new HashMap<>();
    final List<LoginFormsProvider> forms = new ArrayList<>();
    final List<String> templates = new ArrayList<>();
    final List<FormMessage> fieldErrors = new ArrayList<>();
    final List<FormMessage> globalErrors = new ArrayList<>();
    final Map<String, Object> attributes = new HashMap<>();

    AuthFixture() {
        ClientModel client = mock(ClientModel.class);
        when(client.getClientId()).thenReturn("myticketzm-web");
        when(authSession.getClient()).thenReturn(client);
        Mockito.doAnswer(i -> authNotes.put(i.getArgument(0), i.getArgument(1)))
                .when(authSession).setAuthNote(anyString(), anyString());
        when(authSession.getAuthNote(anyString())).thenAnswer(i -> authNotes.get(i.<String>getArgument(0)));
        Mockito.doAnswer(i -> authNotes.remove(i.<String>getArgument(0))).when(authSession).removeAuthNote(anyString());
        when(authSession.getClientNote(anyString())).thenAnswer(i -> clientNotes.get(i.<String>getArgument(0)));
        Mockito.doAnswer(i -> clientNotes.remove(i.<String>getArgument(0))).when(authSession).removeClientNote(anyString());

        HttpRequest request = mock(HttpRequest.class);
        when(request.getDecodedFormParameters()).thenReturn(formData);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.getRemoteAddr()).thenReturn("10.0.0.7");
        when(session.users()).thenReturn(users);

        when(ctx.getAuthenticationSession()).thenReturn(authSession);
        when(ctx.getHttpRequest()).thenReturn(request);
        when(ctx.getConnection()).thenReturn(connection);
        when(ctx.getSession()).thenReturn(session);
        when(ctx.getRealm()).thenReturn(realm);
        when(ctx.getEvent()).thenReturn(event);
        when(ctx.getProtector()).thenReturn(protector);
        when(ctx.form()).thenAnswer(i -> newForm());
    }

    private LoginFormsProvider newForm() {
        LoginFormsProvider form = mock(LoginFormsProvider.class, Answers.RETURNS_SELF);
        when(form.createForm(anyString())).thenAnswer(i -> {
            templates.add(i.getArgument(0));
            return page;
        });
        when(form.createErrorPage(any())).thenReturn(page);
        Mockito.doAnswer(i -> {
            fieldErrors.add(i.getArgument(0));
            return form;
        }).when(form).addError(any(FormMessage.class));
        Mockito.doAnswer(i -> {
            Object[] args = i.getArguments();
            Object[] params = new Object[args.length - 1];
            System.arraycopy(args, 1, params, 0, params.length);
            // varargs arrive expanded for mocks
            globalErrors.add(new FormMessage(null, (String) args[0], params.length == 1 && params[0] instanceof Object[] o ? o : params));
            return form;
        }).when(form).setError(anyString(), any(Object[].class));
        Mockito.doAnswer(i -> {
            attributes.put(i.getArgument(0), i.getArgument(1));
            return form;
        }).when(form).setAttribute(anyString(), any());
        forms.add(form);
        return form;
    }

    RoleModel role(String name) {
        RoleModel r = mock(RoleModel.class);
        when(realm.getRole(name)).thenReturn(r);
        return r;
    }

    UserModel user(String username, boolean enabled) {
        UserModel u = mock(UserModel.class);
        when(u.getUsername()).thenReturn(username);
        when(u.isEnabled()).thenReturn(enabled);
        when(users.getUserByUsername(eq(realm), eq(username))).thenReturn(u);
        return u;
    }

    String lastTemplate() {
        return templates.isEmpty() ? null : templates.get(templates.size() - 1);
    }

    boolean hasKey(String key) {
        return fieldErrors.stream().anyMatch(m -> key.equals(m.getMessage()))
                || globalErrors.stream().anyMatch(m -> key.equals(m.getMessage()));
    }

    FormMessage message(String key) {
        return java.util.stream.Stream.concat(fieldErrors.stream(), globalErrors.stream())
                .filter(m -> key.equals(m.getMessage())).findFirst().orElseThrow();
    }
}
