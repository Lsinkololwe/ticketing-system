package com.pml.identity.web.rest;

import com.pml.identity.service.UserContactLookup;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Resolves a verified contact to the account that owns it, for a ticket transfer addressed by phone
 * number or email. Internal only.
 */
@RestController
@RequestMapping("/api/internal/users")
public class InternalUserLookupController {

    private final UserContactLookup lookup;

    public InternalUserLookupController(UserContactLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Answers {@code 200} with the account id, a short name and the masked contact; {@code 404} with no
     * body for a contact nobody holds and for one held by an account that may not be messaged.
     */
    @PostMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<UserContactLookup.Match>> lookup(@RequestBody ContactQuery query) {
        return lookup.lookup(query.channel(), query.value())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    public record ContactQuery(String channel, String value) {
    }
}
