package com.pml.identity.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.security.ContactCrypto;
import com.pml.shared.constants.UserType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Seals what a staff creation needs to remember (the address, the names, the number and the system-made
 * temporary password) before it is handed to the workflow engine.
 *
 * <p>A workflow's input is written into its history, which anyone who can open the engine's console can
 * read and which lives for the retention period. The sealed form is the same AES-GCM that protects stored
 * contacts, so the history holds only ciphertext and the keyed hash that names the execution.</p>
 */
@Component
public class StaffAccountSeal {

    /** What is sealed: the request once its values are normalised. */
    public record Details(String email, String firstName, String lastName, String phoneNumber, String phoneCountry,
                          UserType role, String actorId, String temporaryPassword) {
        @Override
        public String toString() {
            return "Details[role=" + role + "]";
        }
    }

    private final ObjectMapper json;
    private final ContactCrypto crypto;

    public StaffAccountSeal(ObjectMapper json, ContactCrypto crypto) {
        this.json = json;
        this.crypto = crypto;
    }

    public Mono<String> seal(Details details) {
        return Mono.fromCallable(() -> json.writeValueAsString(details)).flatMap(crypto::encrypt);
    }

    public Mono<Details> open(String sealed) {
        return crypto.decrypt(sealed).map(plain -> {
            try {
                return json.readValue(plain, Details.class);
            } catch (JsonProcessingException unreadable) {
                // The parser's own message can quote the document it failed on, which holds the password.
                throw new IllegalStateException("a sealed staff creation could not be read");
            }
        });
    }
}
