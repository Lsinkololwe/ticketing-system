package com.pml.keycloak.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A minimal cookie-keeping browser that drives the OIDC authorization code + PKCE flow. */
final class Browser {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String REDIRECT = "http://localhost:3000/api/auth/callback";

    /** Outcome of one navigation: either a code redirect or a rendered page. */
    record Step(int status, String location, String html) {
        boolean hasCode() {
            return location != null && location.startsWith(REDIRECT) && location.contains("code=");
        }

        String code() {
            Matcher m = Pattern.compile("[?&]code=([^&]+)").matcher(location);
            return m.find() ? m.group(1) : null;
        }

        boolean isPage() {
            return status == 200 && html != null;
        }
    }

    final String base;
    /**
     * Keycloak marks its cookies Secure even for http://localhost (browsers accept that, java.net.CookieManager
     * does not send them back), so cookies are kept by hand: name to value, paths ignored (one realm per jar).
     */
    final Map<String, String> cookies = new java.util.concurrent.ConcurrentHashMap<>();
    final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
    String verifier;

    Browser(String base) {
        this.base = base;
    }

    /** Starts authorization; redirects within Keycloak are followed, redirect to the app is not. */
    Step authorize(String realm, String clientId, String redirect, String extraQuery) throws Exception {
        byte[] rnd = new byte[32];
        new SecureRandom().nextBytes(rnd);
        verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String url = base + "/realms/" + realm + "/protocol/openid-connect/auth?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirect) + "&response_type=code&scope=openid&state=st-" + System.nanoTime()
                + "&code_challenge=" + challenge + "&code_challenge_method=S256"
                + (extraQuery == null || extraQuery.isEmpty() ? "" : "&" + extraQuery);
        return follow(get(url));
    }

    Step get(String url) throws Exception {
        return toStep(send(HttpRequest.newBuilder(URI.create(url)).GET()));
    }

    Step post(String url, Map<String, String> form) throws Exception {
        StringBuilder body = new StringBuilder();
        form.forEach((k, v) -> body.append(body.isEmpty() ? "" : "&").append(enc(k)).append('=').append(enc(v)));
        return follow(toStep(send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())))));
    }

    /** Submits the (first) form of a page with the given fields. */
    Step submit(Step page, Map<String, String> fields) throws Exception {
        Matcher m = Pattern.compile("<form[^>]*action=\"([^\"]+)\"").matcher(page.html());
        if (!m.find()) {
            throw new IllegalStateException("no form on page: " + page.html().substring(0, Math.min(400, page.html().length())));
        }
        return post(m.group(1).replace("&amp;", "&"), new LinkedHashMap<>(fields));
    }

    private Step follow(Step s) throws Exception {
        int hops = 0;
        while ((s.status() == 302 || s.status() == 303) && s.location() != null && !s.location().startsWith(REDIRECT)
                && !s.location().startsWith("http://localhost:30") && hops++ < 10) {
            s = toStep(send(HttpRequest.newBuilder(URI.create(s.location())).GET()));
        }
        return s;
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(java.util.stream.Collectors.joining("; ")));
        }
        HttpResponse<String> r = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        for (String header : r.headers().allValues("Set-Cookie")) {
            String[] parts = header.split(";");
            int eq = parts[0].indexOf('=');
            String name = parts[0].substring(0, eq).trim();
            String value = parts[0].substring(eq + 1).trim();
            boolean expired = value.isEmpty() || header.toLowerCase().contains("max-age=0")
                    || header.toLowerCase().contains("expires=thu, 01-jan-1970");
            if (expired) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
        return r;
    }

    private static Step toStep(HttpResponse<String> r) {
        return new Step(r.statusCode(), r.headers().firstValue("Location").orElse(null), r.body());
    }

    JsonNode exchange(String realm, String clientId, String secret, String redirect, String code) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "authorization_code");
        f.put("code", code);
        f.put("redirect_uri", redirect);
        f.put("client_id", clientId);
        f.put("client_secret", secret);
        f.put("code_verifier", verifier);
        return tokenPost(realm, f);
    }

    JsonNode tokenPost(String realm, Map<String, String> form) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        form.forEach((k, v) -> body.append(body.isEmpty() ? "" : "&").append(enc(k)).append('=').append(enc(v)));
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + "/realms/" + realm + "/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        JsonNode n = JSON.readTree(r.body());
        ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("_status", r.statusCode());
        return n;
    }

    static JsonNode claims(String jwt) throws IOException {
        return JSON.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
