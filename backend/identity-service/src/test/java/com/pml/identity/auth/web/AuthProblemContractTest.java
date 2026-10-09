package com.pml.identity.auth.web;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Every error code of CONTRACT 4/5 renders as an RFC 9457 problem with the agreed status and extensions. */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · errors are RFC 9457 application/problem+json with errorCode, retryable and the code's extensions")
class AuthProblemContractTest {

    /** What each code must carry: HTTP status and the extension keys of the contract. */
    private record Expect(ErrorCode code, int status, Map<String, Object> details, List<String> extensions) {
    }

    private static final String SECRET = "secret.person@example.com +260971234567 code 123456";

    private static final List<Expect> EXPECTED = List.of(
            new Expect(ErrorCode.CONTACT_INVALID, 400, Map.of(), List.of()),
            new Expect(ErrorCode.OTP_RATE_LIMITED, 429, Map.of("retryAfterSeconds", 3600L), List.of("retryAfterSeconds")),
            new Expect(ErrorCode.OTP_COOLDOWN_ACTIVE, 429, Map.of("retryAfterSeconds", 42L), List.of("retryAfterSeconds")),
            new Expect(ErrorCode.OTP_LOCKED, 423, Map.of("lockedUntil", "2026-10-04T08:15:00Z"), List.of("lockedUntil")),
            new Expect(ErrorCode.OTP_DELIVERY_FAILED, 503, Map.of(), List.of()),
            new Expect(ErrorCode.OTP_INVALID, 400, Map.of("attemptsRemaining", 3L), List.of("attemptsRemaining")),
            new Expect(ErrorCode.OTP_EXPIRED, 410, Map.of(), List.of()),
            new Expect(ErrorCode.PROOF_INVALID, 400, Map.of(), List.of()),
            new Expect(ErrorCode.ACCOUNT_SUSPENDED, 403, Map.of(), List.of()),
            new Expect(ErrorCode.ACCOUNT_MERGING, 409, Map.of(), List.of()),
            new Expect(ErrorCode.CONTACT_ALREADY_CLAIMED, 409, Map.of(), List.of()),
            new Expect(ErrorCode.LOGIN_HANDLE_INVALID, 400, Map.of(), List.of()),
            new Expect(ErrorCode.ACCOUNT_NOT_ACTIVE, 409, Map.of("currentStatus", "SUSPENDED"), List.of("currentStatus")),
            new Expect(ErrorCode.USER_UNKNOWN, 404, Map.of(), List.of()),
            new Expect(ErrorCode.COMMAND_NOT_WELL_FORMED, 400, Map.of(), List.of()),
            new Expect(ErrorCode.NOTIFICATION_CHANNEL_UNAVAILABLE, 503, Map.of(), List.of()),
            new Expect(ErrorCode.OTP_ATTEMPTS_EXHAUSTED, 423, Map.of(), List.of()),
            new Expect(ErrorCode.PHONE_NUMBER_INVALID, 400, Map.of(), List.of()));

    @RestController
    static class Raiser {
        @GetMapping("/raise/{code}")
        Mono<String> raise(@PathVariable String code) {
            if ("DEFECT".equals(code)) {
                return Mono.error(new IllegalStateException("database says " + SECRET));
            }
            Expect expect = EXPECTED.stream().filter(e -> e.code().name().equals(code)).findFirst().orElseThrow();
            Supplier<Throwable> thrown = () -> new TranslatedRefusal(expect.code(), "developer detail " + SECRET, expect.details());
            return Mono.error(thrown.get());
        }
    }

    private final WebTestClient client = WebTestClient.bindToController(new Raiser())
            .controllerAdvice(new AuthProblemAdvice())
            .build();

    @Test
    @DisplayName("every code of the contract has an explicit HTTP status here")
    void everyCodeIsMapped() {
        assertThat(AuthProblemAdvice.STATUS.keySet()).containsAll(EXPECTED.stream().map(Expect::code).toList());
    }

    @Test
    @DisplayName("each refusal is a problem document with the agreed status, errorCode, retryable and extensions, and no developer text")
    void everyCodeRendersAsAProblem() {
        for (Expect expect : EXPECTED) {
            var result = client.get().uri("/raise/" + expect.code().name()).exchange()
                    .expectStatus().isEqualTo(expect.status())
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(expect.status())
                    .jsonPath("$.title").isEqualTo(expect.code().name())
                    .jsonPath("$.type").isEqualTo("https://errors.myticket.zm/" + expect.code().name())
                    .jsonPath("$.errorCode").isEqualTo(expect.code().name())
                    .jsonPath("$.retryable").isEqualTo(expect.code().retryable())
                    .jsonPath("$.detail").isEqualTo(expect.code().name())
                    .jsonPath("$.correlationId").isNotEmpty()
                    .returnResult();
            String body = new String(result.getResponseBodyContent());
            assertThat(body).as(expect.code().name()).doesNotContain("secret.person").doesNotContain("971234567")
                    .doesNotContain("123456");
            for (String extension : expect.extensions()) {
                assertThat(body).as(expect.code() + " carries " + extension).contains("\"" + extension + "\"");
            }
            for (Map.Entry<String, Object> detail : expect.details().entrySet()) {
                client.get().uri("/raise/" + expect.code().name()).exchange()
                        .expectBody().jsonPath("$." + detail.getKey()).isEqualTo(detail.getValue());
            }
        }
    }

    @Test
    @DisplayName("throttling carries Retry-After; retryable codes are exactly the registry's")
    void retryAfterHeader() {
        client.get().uri("/raise/OTP_RATE_LIMITED").exchange().expectHeader().valueEquals("Retry-After", "3600");
        client.get().uri("/raise/OTP_COOLDOWN_ACTIVE").exchange().expectHeader().valueEquals("Retry-After", "42");
        client.get().uri("/raise/OTP_DELIVERY_FAILED").exchange().expectBody().jsonPath("$.retryable").isEqualTo(true);
        client.get().uri("/raise/OTP_INVALID").exchange().expectBody().jsonPath("$.retryable").isEqualTo(false);
    }

    @Test
    @DisplayName("an unexpected exception is a constant 500 INTERNAL_ERROR that leaks nothing")
    void defectLeaksNothing() {
        var result = client.get().uri("/raise/DEFECT").exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INTERNAL_ERROR")
                .returnResult();
        assertThat(new String(result.getResponseBodyContent())).doesNotContain("secret.person").doesNotContain("database");
    }

    @Test
    @DisplayName("an unreadable body is 400 COMMAND_NOT_WELL_FORMED")
    void malformedBody() {
        WebTestClient posting = WebTestClient.bindToController(new InternalChallengeController(null, null, null))
                .controllerAdvice(new AuthProblemAdvice()).build();
        posting.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not json").exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.errorCode").isEqualTo("COMMAND_NOT_WELL_FORMED");
    }
}
