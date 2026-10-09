package com.pml.identity.web.rest;

import com.pml.identity.service.ApprovalNotifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Catalog's approval workflow asks here for the messages an event review sends: administrators when
 * an event joins the queue, the organizer when it is decided. Internal only.
 */
@RestController
@RequestMapping("/api/internal/notifications")
public class InternalApprovalNotificationController {

    private final ApprovalNotifier approvals;

    public InternalApprovalNotificationController(ApprovalNotifier approvals) {
        this.approvals = approvals;
    }

    /** Answers {@code 202} with how many people were addressed, or {@code 400} for a template that is not an event review. */
    @PostMapping("/approvals")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Map<String, Integer>>> notifyApproval(@Valid @RequestBody ApprovalNotice notice) {
        return approvals.notify(notice.templateKey(), notice.discriminator(), notice.eventId(), notice.organizerId())
                .map(addressed -> ResponseEntity.accepted().body(Map.of("addressed", addressed)))
                .onErrorResume(IllegalArgumentException.class,
                        refused -> Mono.just(ResponseEntity.badRequest().body(Map.of("addressed", 0))));
    }

    public record ApprovalNotice(@NotBlank @Size(max = 64) String templateKey,
                                 @NotBlank @Size(max = 200) String discriminator,
                                 @NotBlank @Size(max = 100) String eventId,
                                 @Size(max = 100) String organizerId) {
    }
}
