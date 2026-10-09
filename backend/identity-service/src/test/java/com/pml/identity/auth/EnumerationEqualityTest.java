package com.pml.identity.auth;

import com.pml.shared.testing.RedisNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.pml.identity.auth.challenge.ChallengeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request for an unknown contact looks exactly like a request for a known one (ET-IDN-001-R4).
 * The challenge engine has no way to ask whether an account exists; this test proves the shape and
 * the latency band, and pins the absence of that dependency.
 */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R4 · a challenge for a registered contact and one for an unknown contact are indistinguishable")
class EnumerationEqualityTest {

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private final AuthEngine engine = new AuthEngine();

    @Test
    @DisplayName("the challenge service takes no account, user or contact repository")
    void noAccountLookup() {
        for (Constructor<?> constructor : ChallengeService.class.getConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertThat(parameter.getName().toLowerCase()).doesNotContain("repository", "account", "userservice");
            }
        }
    }

    @Test
    @DisplayName("same response shape and a latency within the same band for phone and email, known and unknown")
    void sameShapeSameBand() {
        // "Known" contacts have already completed a sign-in (proved and consumed), as a registered buyer has.
        List<String> known = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String contact = i % 2 == 0 ? AuthEngine.randomPhone() : AuthEngine.randomEmail();
            var issued = engine.issue(contact);
            var verified = engine.challenges.verify(issued.challengeId(), engine.codeFor(normalised(contact))).block();
            engine.proofs.create(verified.contactKey(), verified.type(), verified.valueEncrypted(), verified.valueMasked()).block();
            known.add(contact);
            engine.clock.advance(Duration.ofSeconds(61));
            unknown.add(i % 2 == 0 ? AuthEngine.randomPhone() : AuthEngine.randomEmail());
        }

        List<Long> knownNanos = new ArrayList<>();
        List<Long> unknownNanos = new ArrayList<>();
        TreeSet<String> knownShapes = new TreeSet<>();
        TreeSet<String> unknownShapes = new TreeSet<>();
        for (int i = 0; i < known.size(); i++) {
            long t0 = System.nanoTime();
            var a = engine.challenges.issue(engine.command(known.get(i))).block();
            knownNanos.add(System.nanoTime() - t0);
            long t1 = System.nanoTime();
            var b = engine.challenges.issue(engine.command(unknown.get(i))).block();
            unknownNanos.add(System.nanoTime() - t1);
            knownShapes.add(shape(a));
            unknownShapes.add(shape(b));
        }

        assertThat(knownShapes).as("same status/shape per contact type").isEqualTo(unknownShapes);
        Collections.sort(knownNanos);
        Collections.sort(unknownNanos);
        long medianKnown = knownNanos.get(knownNanos.size() / 2) / 1_000_000;
        long medianUnknown = unknownNanos.get(unknownNanos.size() / 2) / 1_000_000;
        assertThat(Math.abs(medianKnown - medianUnknown)).as("median latency difference in ms").isLessThanOrEqualTo(50);
    }

    private String normalised(String contact) {
        return engine.hasher.normalize(contact, null, null, null).orElseThrow().value();
    }

    private String shape(ChallengeService.Issued issued) {
        JsonNode node = engine.json.valueToTree(issued);
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return issued.contactType() + ":" + issued.channel() + ":" + issued.expiresInSeconds() + ":"
                + issued.resendAfterSeconds() + ":" + new TreeSet<>(fields);
    }
}
