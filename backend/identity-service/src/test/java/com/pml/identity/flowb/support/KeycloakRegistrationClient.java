package com.pml.identity.flowb.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drives Keycloak's <em>real</em> browser registration flow over HTTP.
 *
 * <p>Flow B's role minting happens inside the {@code MyTicket Registration Form} authentication
 * flow. Creating users through the Admin API would skip
 * {@code AccountTypeRoleMapper} entirely — the very component under test — so this client walks
 * the same three requests a browser makes: authorization endpoint → registration page →
 * form POST, carrying cookies throughout.</p>
 *
 * <p>The field names below come from the shipped
 * {@code docker-resources/keycloak/themes/myticketzm/login/register.ftl}. If that template is
 * renamed, these tests fail — which is the point: the theme is part of the flow.</p>
 */
public final class KeycloakRegistrationClient {

    private static final Pattern REGISTRATION_LINK =
            Pattern.compile("href=\"([^\"]*login-actions/registration[^\"]*)\"");
    private static final Pattern REGISTER_FORM_ACTION =
            Pattern.compile("<form[^>]*id=\"kc-register-form\"[^>]*action=\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern ANY_FORM_ACTION =
            Pattern.compile("<form[^>]*action=\"([^\"]*login-actions/registration[^\"]*)\"", Pattern.DOTALL);
    private static final Pattern FIELD_ERROR =
            Pattern.compile("id=\"input-error[^\"]*\"[^>]*>\\s*([^<]+?)\\s*<", Pattern.DOTALL);
    private static final Pattern FEEDBACK_ERROR =
            Pattern.compile("class=\"[^\"]*(?:kc-feedback-text|alert-error|error-message)[^\"]*\"[^>]*>\\s*([^<]+?)\\s*<",
                    Pattern.DOTALL);

    private final String authServerUrl;
    private final String realm;

    public KeycloakRegistrationClient(String authServerUrl, String realm) {
        this.authServerUrl = authServerUrl;
        this.realm = realm;
    }

    // ------------------------------------------------------------------
    // types
    // ------------------------------------------------------------------

    /**
     * One registration submission.
     *
     * @param accountTypes the values posted as repeated {@code user.attributes.accountType}
     *                     fields; an empty list posts the field not at all, which is how a
     *                     browser submits an all-unchecked checkbox group
     * @param extraFields  raw additional form fields, used by the injection tests
     */
    public record Registration(
            String username,
            String email,
            String firstName,
            String lastName,
            String password,
            List<String> accountTypes,
            String phoneNumber,
            Map<String, String> extraFields) {

        public static Registration organizer(String handle) {
            return of(handle, List.of("ORGANIZER"));
        }

        public static Registration customer(String handle) {
            return of(handle, List.of("CUSTOMER"));
        }

        public static Registration of(String handle, List<String> accountTypes) {
            return new Registration(
                    handle,
                    handle + "@flowb.test",
                    "Flow",
                    "Bee",
                    "Str0ng!Passw0rd#" + handle.hashCode(),
                    accountTypes,
                    null,
                    Map.of());
        }

        public Registration withFirstName(String value) {
            return new Registration(username, email, value, lastName, password, accountTypes,
                    phoneNumber, extraFields);
        }

        public Registration withUsername(String value) {
            return new Registration(value, email, firstName, lastName, password, accountTypes,
                    phoneNumber, extraFields);
        }

        public Registration withEmail(String value) {
            return new Registration(username, value, firstName, lastName, password, accountTypes,
                    phoneNumber, extraFields);
        }

        public Registration withPassword(String value) {
            return new Registration(username, email, firstName, lastName, value, accountTypes,
                    phoneNumber, extraFields);
        }

        public Registration withPhoneNumber(String value) {
            return new Registration(username, email, firstName, lastName, password, accountTypes,
                    value, extraFields);
        }

        public Registration withAccountTypes(List<String> values) {
            return new Registration(username, email, firstName, lastName, password, values,
                    phoneNumber, extraFields);
        }
    }

    /** Result of a submission. */
    public record Outcome(int status, boolean accepted, String authorizationCode,
                          List<String> errors, String html) {

        /** True when the rendered page mentions the given text (message key or English copy). */
        public boolean mentions(String needle) {
            return html != null && html.toLowerCase().contains(needle.toLowerCase());
        }

        public String describe() {
            return "status=" + status + " accepted=" + accepted + " errors=" + errors;
        }
    }

    // ------------------------------------------------------------------
    // flow
    // ------------------------------------------------------------------

    public Outcome register(Registration registration) {
        CookieJar client = newClient();
        try {
            String loginPage = get(client, authorizationUri()).body();
            String registrationUrl = absolute(extract(loginPage, REGISTRATION_LINK)
                    .orElseThrow(() -> new IllegalStateException(
                            "No registration link on the login page — is registrationAllowed still true? "
                                    + snippet(loginPage))));

            String registrationPage = get(client, URI.create(registrationUrl)).body();
            String action = absolute(extract(registrationPage, REGISTER_FORM_ACTION)
                    .or(() -> extract(registrationPage, ANY_FORM_ACTION))
                    .orElseThrow(() -> new IllegalStateException(
                            "No registration form action found. " + snippet(registrationPage))));

            HttpResponse<String> response = postForm(client, URI.create(action), formBody(registration));

            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.headers().firstValue("Location").orElse("");
                return new Outcome(response.statusCode(), true, codeFrom(location), List.of(), location);
            }
            String body = response.body();
            return new Outcome(response.statusCode(), false, null, errorsFrom(body), body);
        } catch (IOException e) {
            throw new IllegalStateException("Registration request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Registration interrupted", e);
        }
    }

    /**
     * A single failed password login, used by the brute-force tests.
     *
     * @return the HTTP status of the token request (401 while merely wrong, 400 once locked)
     */
    public int attemptPasswordLogin(String username, String password) {
        CookieJar client = newClient();
        String body = form(Map.of(
                "grant_type", "password",
                "client_id", RealmProvisioner.TEST_CLIENT_ID,
                "username", username,
                "password", password,
                "scope", "openid"));
        try {
            return postForm(client, URI.create(tokenEndpoint()), body).statusCode();
        } catch (IOException e) {
            throw new IllegalStateException("Password login failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Password login interrupted", e);
        }
    }

    /** Access token for a user via the direct grant, or empty when the grant is refused. */
    public Optional<String> passwordGrantToken(String username, String password) {
        CookieJar client = newClient();
        String body = form(Map.of(
                "grant_type", "password",
                "client_id", RealmProvisioner.TEST_CLIENT_ID,
                "username", username,
                "password", password,
                "scope", "openid"));
        try {
            HttpResponse<String> response = postForm(client, URI.create(tokenEndpoint()), body);
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(readJsonString(response.body(), "access_token"));
        } catch (IOException e) {
            throw new IllegalStateException("Token request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token request interrupted", e);
        }
    }

    /** Access token for a confidential client via {@code client_credentials}. */
    public Optional<String> clientCredentialsToken(String clientId, String clientSecret, String scope) {
        CookieJar client = newClient();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("grant_type", "client_credentials");
        fields.put("client_id", clientId);
        fields.put("client_secret", clientSecret);
        if (scope != null) {
            fields.put("scope", scope);
        }
        try {
            HttpResponse<String> response = postForm(client, URI.create(tokenEndpoint()), form(fields));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(readJsonString(response.body(), "access_token"));
        } catch (IOException e) {
            throw new IllegalStateException("Client credentials request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Client credentials request interrupted", e);
        }
    }

    public String tokenEndpoint() {
        return authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * A deliberately naive cookie jar: name → value, echoed back on every request.
     *
     * <p>{@link java.net.CookieManager} cannot be used here. Keycloak marks {@code AUTH_SESSION_ID}
     * as {@code Secure; SameSite=None}, and the JDK cookie store refuses to replay {@code Secure}
     * cookies over plain HTTP — the auth session is then lost and Keycloak answers
     * {@code REGISTER_ERROR / cookie_not_found}. Terminating TLS in the test would add a
     * moving part without testing anything, so the jar simply ignores cookie attributes.</p>
     */
    private static final class CookieJar {
        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        private final Map<String, String> cookies = new LinkedHashMap<>();

        HttpResponse<String> send(HttpRequest.Builder builder, URI uri)
                throws IOException, InterruptedException {
            if (!cookies.isEmpty()) {
                builder.header("Cookie", cookies.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse(""));
            }
            HttpResponse<String> response =
                    http.send(builder.uri(uri).build(), HttpResponse.BodyHandlers.ofString());
            response.headers().allValues("set-cookie").forEach(this::remember);
            return response;
        }

        private void remember(String setCookie) {
            String pair = setCookie.split(";", 2)[0].trim();
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                return;
            }
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (value.isEmpty()) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }

    private CookieJar newClient() {
        return new CookieJar();
    }

    private URI authorizationUri() {
        return URI.create(authServerUrl + "/realms/" + realm + "/protocol/openid-connect/auth?"
                + form(new LinkedHashMap<>(Map.of(
                "client_id", RealmProvisioner.TEST_CLIENT_ID,
                "response_type", "code",
                "scope", "openid",
                "redirect_uri", RealmProvisioner.TEST_REDIRECT_URI,
                "state", "flowb-state",
                "nonce", "flowb-nonce"))));
    }

    /** GET that follows redirects except the one back to our own redirect URI. */
    private HttpResponse<String> get(CookieJar client, URI uri)
            throws IOException, InterruptedException {
        URI current = uri;
        for (int hop = 0; hop < 6; hop++) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder().GET().timeout(Duration.ofSeconds(30)), current);
            if (response.statusCode() < 300 || response.statusCode() >= 400) {
                return response;
            }
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null || location.startsWith(RealmProvisioner.TEST_REDIRECT_URI)) {
                return response;
            }
            current = current.resolve(location);
        }
        throw new IllegalStateException("Too many redirects starting at " + uri);
    }

    private HttpResponse<String> postForm(CookieJar client, URI uri, String body)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder()
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(body)),
                uri);
    }

    private String formBody(Registration registration) {
        List<String> pairs = new ArrayList<>();
        add(pairs, "firstName", registration.firstName());
        add(pairs, "lastName", registration.lastName());
        add(pairs, "username", registration.username());
        add(pairs, "email", registration.email());
        add(pairs, "password", registration.password());
        add(pairs, "password-confirm", registration.password());
        if (registration.phoneNumber() != null) {
            add(pairs, "user.attributes.phoneNumber", registration.phoneNumber());
        }
        // Repeated field, exactly as a checkbox group submits it. An empty list omits the
        // field entirely, which is what an all-unchecked group posts.
        for (String accountType : registration.accountTypes()) {
            add(pairs, "user.attributes.accountType", accountType);
        }
        registration.extraFields().forEach((k, v) -> add(pairs, k, v));
        return String.join("&", pairs);
    }

    private static void add(List<String> pairs, String name, String value) {
        if (value == null) {
            return;
        }
        pairs.add(URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String form(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>();
        fields.forEach((k, v) -> add(pairs, k, v));
        return String.join("&", pairs);
    }

    private String absolute(String url) {
        String unescaped = url.replace("&amp;", "&");
        return unescaped.startsWith("http") ? unescaped : authServerUrl + unescaped;
    }

    private static Optional<String> extract(String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static List<String> errorsFrom(String html) {
        List<String> errors = new ArrayList<>();
        for (Pattern pattern : List.of(FIELD_ERROR, FEEDBACK_ERROR)) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String text = matcher.group(1).trim();
                if (!text.isEmpty() && !errors.contains(text)) {
                    errors.add(text);
                }
            }
        }
        return errors;
    }

    private static String codeFrom(String location) {
        Matcher matcher = Pattern.compile("[?&]code=([^&]+)").matcher(location);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Minimal extraction — avoids pulling a JSON parser into the HTTP helper. */
    private static String readJsonString(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No '" + field + "' in token response: " + snippet(json));
        }
        return matcher.group(1);
    }

    private static String snippet(String body) {
        return body == null ? "<null>" : body.substring(0, Math.min(600, body.length()));
    }
}
