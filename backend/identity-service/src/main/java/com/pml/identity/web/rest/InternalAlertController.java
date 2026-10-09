package com.pml.identity.web.rest;

import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.domain.model.SystemAlert;
import com.pml.identity.platform.PlatformOpsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * How the other services tell operators something is wrong: raise an alert for a condition, and
 * close it when the condition clears. Internal only; the body carries no personal data.
 */
@RestController
@RequestMapping("/api/internal/alerts")
public class InternalAlertController {

    private final PlatformOpsService ops;

    public InternalAlertController(PlatformOpsService ops) {
        this.ops = ops;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Map<String, Object>>> raise(@Valid @RequestBody RaiseRequest request) {
        return ops.raise(request.source(), request.key(), request.severity(), request.title(), request.message())
                .map((SystemAlert alert) -> ResponseEntity.accepted()
                        .body(Map.<String, Object>of("id", alert.getId(), "occurrences", alert.getOccurrences())));
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Void>> resolve(@Valid @RequestBody ResolveRequest request) {
        return ops.resolve(request.source(), request.key()).thenReturn(ResponseEntity.accepted().build());
    }

    public record RaiseRequest(@NotBlank @Size(max = 60) String source,
                               @NotBlank @Size(max = 120) String key,
                               @NotNull AlertSeverity severity,
                               @NotBlank @Size(max = 160) String title,
                               @Size(max = 1000) String message) {
    }

    public record ResolveRequest(@NotBlank @Size(max = 60) String source, @NotBlank @Size(max = 120) String key) {
    }
}
