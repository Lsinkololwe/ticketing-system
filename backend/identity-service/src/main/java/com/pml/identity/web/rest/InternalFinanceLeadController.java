package com.pml.identity.web.rest;

import com.pml.identity.service.FinanceLeadNotifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The finance leads booking escalates to: their email addresses, and a WhatsApp message
 * to each of them. Internal only.
 */
@RestController
@RequestMapping("/api/internal/finance-leads")
public class InternalFinanceLeadController {

    private final FinanceLeadNotifier financeLeads;

    public InternalFinanceLeadController(FinanceLeadNotifier financeLeads) {
        this.financeLeads = financeLeads;
    }

    @GetMapping("/contacts")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Flux<FinanceLeadNotifier.Contact> contacts() {
        return financeLeads.contacts();
    }

    /** Answers {@code 202} with how many leads were addressed, or {@code 400} for a template that is not an escalation. */
    @PostMapping("/notifications")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Map<String, Integer>>> notifyLeads(@Valid @RequestBody EscalationRequest request) {
        return financeLeads.notifyLeads(request.templateKey(), request.discriminator(), request.subjectId())
                .map(addressed -> ResponseEntity.accepted().body(Map.of("addressed", addressed)))
                .onErrorResume(IllegalArgumentException.class,
                        refused -> Mono.just(ResponseEntity.badRequest().body(Map.of("addressed", 0))));
    }

    public record EscalationRequest(@NotBlank @Size(max = 64) String templateKey,
                                    @NotBlank @Size(max = 200) String discriminator,
                                    @NotBlank @Size(max = 100) String subjectId) {
    }
}
