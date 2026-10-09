package com.pml.identity.auth.web;

import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.config.IdentityProofProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** {@code POST /api/internal/auth/challenges} and {@code /challenges/verify} (CONTRACT 4.1, 4.2). */
@RestController
@RequestMapping("/api/internal/auth/challenges")
public class InternalChallengeController {

    private final ChallengeService challenges;
    private final ProofService proofs;
    private final IdentityProofProperties proofProperties;

    public InternalChallengeController(ChallengeService challenges, ProofService proofs,
                                       IdentityProofProperties proofProperties) {
        this.challenges = challenges;
        this.proofs = proofs;
        this.proofProperties = proofProperties;
    }

    @PostMapping
    public Mono<ResponseEntity<AuthDtos.ChallengeResponse>> request(@RequestBody Mono<AuthDtos.ChallengeRequest> body) {
        return body.switchIfEmpty(Mono.error(malformed()))
                .flatMap(request -> {
                    if (request.contact() == null || request.contact().value() == null
                            || request.contact().value().isBlank() || request.contact().value().length() > 320) {
                        return Mono.error(malformed());
                    }
                    return challenges.issue(new ChallengeService.IssueCommand(
                            request.contact().value(),
                            enumOrNull(ContactType.class, request.contact().type()),
                            request.regionHint(), request.clientIp(), request.deviceId(),
                            enumOrNull(DeliveryChannel.class, request.preferredChannel())));
                })
                .map(issued -> ResponseEntity.status(HttpStatus.ACCEPTED).body(new AuthDtos.ChallengeResponse(
                        issued.challengeId(), issued.contactType().name(), issued.maskedContact(),
                        issued.channel().name(), issued.expiresInSeconds(), issued.resendAfterSeconds())));
    }

    @PostMapping("/verify")
    public Mono<ResponseEntity<AuthDtos.VerifyResponse>> verify(@RequestBody Mono<AuthDtos.VerifyRequest> body) {
        return body.switchIfEmpty(Mono.error(malformed()))
                .flatMap(request -> challenges.verify(request.challengeId(), request.code()))
                .flatMap(verified -> proofs.create(verified.contactKey(), verified.type(),
                                verified.valueEncrypted(), verified.valueMasked())
                        .map(proof -> ResponseEntity.ok(new AuthDtos.VerifyResponse(proof, verified.type().name(),
                                verified.valueMasked(), proofProperties.getTtl().toSeconds()))));
    }

    static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw malformed();
        }
    }

    static TranslatedRefusal malformed() {
        return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "request body not well formed");
    }
}
