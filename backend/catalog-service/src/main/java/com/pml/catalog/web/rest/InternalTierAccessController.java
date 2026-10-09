package com.pml.catalog.web.rest;

import com.pml.catalog.service.TierAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Booking asks, before it reserves a hidden tier, whether the code the buyer holds opens it.
 * Reached only with an internal-service token ({@code /api/internal/**}).
 */
@RestController
@RequestMapping("/api/internal/tiers")
@RequiredArgsConstructor
public class InternalTierAccessController {

    private final TierAccessService access;

    public record Verification(@NotBlank @Size(max = 64) String accessCode) {
    }

    public record Verdict(boolean valid) {
    }

    @PostMapping("/{tierId}/access-code/verify")
    public Mono<Verdict> verify(@PathVariable String tierId, @Valid @RequestBody Verification request) {
        return access.opens(tierId, request.accessCode()).map(Verdict::new);
    }
}
