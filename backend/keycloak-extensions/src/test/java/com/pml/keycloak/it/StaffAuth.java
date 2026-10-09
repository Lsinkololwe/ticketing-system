package com.pml.keycloak.it;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signs a staff member in through the admin realm's browser flow, enrolling an authenticator the
 * first time and answering the code prompt afterwards.
 *
 * <p>Keycloak does not accept the same one-time code twice, so each login uses a time step later
 * than the last one used for that user (the realm accepts one step ahead) and waits for the next
 * step when the window is exhausted.</p>
 */
final class StaffAuth {

    private static final Map<String, String> SECRETS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_STEP = new ConcurrentHashMap<>();

    private StaffAuth() {
    }

    /** Records an authenticator that was provisioned outside the browser (a seeded account). */
    static void register(String username, String rawSecret) {
        SECRETS.put(username, rawSecret);
    }

    static boolean enrolled(String username) {
        return SECRETS.containsKey(username);
    }

    /** Submits the username and password, then completes whichever second-factor page Keycloak shows. */
    static Browser.Step completePasswordLogin(Browser browser, Browser.Step loginPage, String username, String password) throws Exception {
        Browser.Step next = passwordOnly(browser, loginPage, username, password);
        if (next.isPage() && next.html().contains("name=\"totpSecret\"")) {
            return enrol(browser, next, username);
        }
        if (next.isPage() && next.html().contains("name=\"otp\"")) {
            return browser.submit(next, Map.of("otp", nextCode(username)));
        }
        return next;
    }

    /** The password step alone, leaving the second factor to the caller. */
    static Browser.Step passwordOnly(Browser browser, Browser.Step loginPage, String username, String password) throws Exception {
        return browser.submit(loginPage, Map.of("username", username, "password", password, "credentialId", ""));
    }

    static Browser.Step enrol(Browser browser, Browser.Step setupPage, String username) throws Exception {
        String secret = field(setupPage.html(), "totpSecret");
        SECRETS.put(username, secret);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("totpSecret", secret);
        form.put("totp", nextCode(username));
        form.put("userLabel", "it-authenticator");
        String mode = field(setupPage.html(), "mode");
        if (mode != null) {
            form.put("mode", mode);
        }
        return browser.submit(setupPage, form);
    }

    /** A valid code for a step nobody has used yet, waiting for the clock if the window is spent. */
    static String nextCode(String username) throws InterruptedException {
        String secret = SECRETS.get(username);
        if (secret == null) {
            throw new IllegalStateException("no authenticator enrolled for " + username);
        }
        long step = Math.max(Totp.currentStep(), LAST_STEP.getOrDefault(username, -1L) + 1);
        while (step > Totp.currentStep() + 1) {
            Thread.sleep(1000);
            step = Math.max(Totp.currentStep(), LAST_STEP.getOrDefault(username, -1L) + 1);
        }
        LAST_STEP.put(username, step);
        return Totp.code(secret, step);
    }

    /** The code for the step just before the current one: valid in time, but already behind the user's last use. */
    static String staleCode(String username, long stepsBack) {
        return Totp.code(SECRETS.get(username), LAST_STEP.get(username) - stepsBack);
    }

    /** The visible text of a page's form and messages, for failure messages. */
    static String excerpt(String html) {
        if (html == null) {
            return "no body";
        }
        String text = html.replaceAll("(?s)<script.*?</script>|<style.*?</style>", " ");
        text = Pattern.compile("<input[^>]*name=\"([^\"]*)\"[^>]*>").matcher(text).replaceAll("[input $1]");
        text = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.substring(0, Math.min(700, text.length()));
    }

    static String field(String html, String name) {
        Matcher m = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        m = Pattern.compile("<input[^>]*value=\"([^\"]*)\"[^>]*name=\"" + name + "\"").matcher(html);
        return m.find() ? m.group(1) : null;
    }
}
