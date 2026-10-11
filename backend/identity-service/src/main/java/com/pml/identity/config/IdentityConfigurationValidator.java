package com.pml.identity.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Stops startup when a secret or a required setting of the account flow is missing (CONTRACT 7).
 *
 * <p>Profiles {@code local} and {@code test} carry development values and may leave things blank;
 * everywhere else a blank or short secret is a refusal to start. Messages name the property, never
 * its value.</p>
 */
@Component
@RequiredArgsConstructor
public class IdentityConfigurationValidator {

    /** Shortest accepted secret outside local and test: 32 characters. */
    static final int MIN_SECRET_LENGTH = 32;

    private final Environment environment;
    private final IdentityChallengeProperties challenge;
    private final IdentityProofProperties proof;
    private final IdentityLoginHandleProperties loginHandle;
    private final IdentityContactProperties contact;
    private final IdentityIdHashProperties idHash;
    private final IdentityLimitsProperties limits;
    private final IdentityDeliveryProperties delivery;

    @PostConstruct
    void validateAtStartup() {
        boolean development = environment.acceptsProfiles(Profiles.of("local", "test"));
        List<String> problems = validate(development, challenge, proof, loginHandle, contact, idHash, limits, delivery);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("identity-service configuration is invalid: " + String.join("; ", problems));
        }
    }

    /** @return one message per problem, empty when the configuration is acceptable */
    public static List<String> validate(boolean development,
                                        IdentityChallengeProperties challenge,
                                        IdentityProofProperties proof,
                                        IdentityLoginHandleProperties loginHandle,
                                        IdentityContactProperties contact,
                                        IdentityIdHashProperties idHash,
                                        IdentityLimitsProperties limits,
                                        IdentityDeliveryProperties delivery) {
        List<String> problems = new ArrayList<>();

        // Structural settings: wrong everywhere, including local.
        if (challenge.getCodeLength() < 4 || challenge.getCodeLength() > 10) {
            problems.add("identity.challenge.code-length must be between 4 and 10");
        }
        if (challenge.getMaxAttempts() < 1) {
            problems.add("identity.challenge.max-attempts must be at least 1");
        }
        positive(problems, "identity.challenge.ttl", challenge.getTtl());
        positive(problems, "identity.challenge.lock", challenge.getLock());
        positive(problems, "identity.challenge.cooldown", challenge.getCooldown());
        positive(problems, "identity.proof.ttl", proof.getTtl());
        positive(problems, "identity.proof.ensure-hold", proof.getEnsureHold());
        positive(problems, "identity.login-handle.ttl", loginHandle.getTtl());
        if (limits.getAllowedCountries() == null || limits.getAllowedCountries().isEmpty()) {
            problems.add("identity.limits.allowed-countries must not be empty");
        }
        if (contact.getEncryptionKeyId() == null || contact.getEncryptionKeyId().isBlank()) {
            problems.add("identity.contact.encryption-key-id must not be blank");
        }
        if (challenge.getTtl() != null && proof.getTtl() != null && proof.getEnsureHold() != null
                && proof.getEnsureHold().compareTo(proof.getTtl()) < 0) {
            problems.add("identity.proof.ensure-hold must not be shorter than identity.proof.ttl");
        }

        if (development) {
            return problems;
        }

        // Outside local and test: secrets are mandatory and long enough to be secrets.
        secret(problems, "identity.challenge.hmac-pepper", challenge.getHmacPepper());
        secret(problems, "identity.contact.hash-key", contact.getHashKey());
        secret(problems, "identity.id-hash.key", idHash.getKey());

        if (delivery.getCapture().isEnabled()) {
            problems.add("identity.delivery.capture.enabled is only allowed in profiles local and test");
        }
        IdentityDeliveryProperties.Whatsapp whatsapp = delivery.getWhatsapp();
        if (whatsapp.isEnabled()) {
            required(problems, "identity.delivery.whatsapp.api-url", whatsapp.getApiUrl());
            required(problems, "identity.delivery.whatsapp.phone-number-id", whatsapp.getPhoneNumberId());
            required(problems, "identity.delivery.whatsapp.access-token", whatsapp.getAccessToken());
            required(problems, "identity.delivery.whatsapp.template-name", whatsapp.getTemplateName());
        }
        if (delivery.getEmail().isEnabled()) {
            required(problems, "identity.delivery.email.from", delivery.getEmail().getFrom());
        }
        if (!whatsapp.isEnabled() && !delivery.getEmail().isEnabled()) {
            problems.add("no delivery channel is enabled: set identity.delivery.whatsapp.enabled or identity.delivery.email.enabled");
        }
        return problems;
    }

    private static void positive(List<String> problems, String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            problems.add(name + " must be a positive duration");
        }
    }

    private static void required(List<String> problems, String name, String value) {
        if (value == null || value.isBlank()) {
            problems.add(name + " is required");
        }
    }

    private static void secret(List<String> problems, String name, String value) {
        if (value == null || value.isBlank()) {
            problems.add(name + " is required outside profiles local and test");
        } else if (value.length() < MIN_SECRET_LENGTH) {
            problems.add(name + " must be at least " + MIN_SECRET_LENGTH + " characters");
        }
    }
}
