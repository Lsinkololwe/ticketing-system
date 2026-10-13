package com.pml.identity.web.rest;

import com.pml.identity.platform.PlatformWideAuditTrail;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.PlatformWideAuditRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * How booking and catalog tell identity that platform staff reached across organizers or buyers,
 * so the reach is a searchable row beside identity's own.
 *
 * <p>Internal only. The body is the facts of the reach and nothing else; each is checked against a
 * closed shape, because this is a write into the trail the compliance team trusts. The reason must
 * be one the platform declared, and a caller that sends anything outside the shapes is refused
 * rather than trimmed.
 *
 * <p>The sender keeps each reach locally and delivers it until identity confirms, so a delivery can
 * be late and can be repeated. Two things make that safe. The {@code recordId} is the reach's own
 * permanent identifier: a repeat of one identity already holds is answered success and changes
 * nothing. And {@code occurredAt}, the time the reach happened, is accepted only inside a bounded
 * window ({@link PlatformWideAuditTrail#MAX_AGE} back from now, {@link PlatformWideAuditTrail#CLOCK_SKEW}
 * forward); identity always stores its own receipt time beside it, so a caller can neither place a
 * row in the future nor hide a late arrival.
 */
@RestController
@RequestMapping("/api/internal/audit")
public class InternalPlatformWideAuditController {

    private final PlatformWideAuditTrail trail;
    private final Clock clock;

    public InternalPlatformWideAuditController(PlatformWideAuditTrail trail, Clock clock) {
        this.trail = trail;
        this.clock = clock;
    }

    @PostMapping("/platform-wide")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<Void>> record(@RequestBody Delivery request) {
        PlatformWideAccess.Reason reason = validated(request);
        Instant occurredAt = occurredAt(request.occurredAt());
        return trail.receive(request.recordId(), new PlatformWideAuditRecord(request.actorSub(), request.role(),
                        request.service(), reason, request.operation(), occurredAt))
                .thenReturn(ResponseEntity.accepted().<Void>build());
    }

    private static final Pattern RECORD_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern ACTOR = Pattern.compile("[A-Za-z0-9_.:@-]{1,100}");
    private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
    private static final Pattern SERVICE = Pattern.compile("[a-z][a-z0-9-]{0,79}");
    private static final Pattern OPERATION = Pattern.compile("[A-Za-z0-9_.:-]{0,100}");

    /**
     * Refuses anything outside the closed shapes and answers the reason the platform declared.
     * Checked by hand rather than by bean validation so a refusal is a 400 on this surface.
     */
    private static PlatformWideAccess.Reason validated(Delivery request) {
        if (request == null || !matches(RECORD_ID, request.recordId()) || !matches(ACTOR, request.actorSub()) || !matches(ROLE, request.role())
                || !matches(SERVICE, request.service())
                || (request.operation() != null && !OPERATION.matcher(request.operation()).matches())) {
            throw malformed("the delivery does not match the shapes of an actor, role, service and operation");
        }
        return reasonOf(request.reason());
    }

    /** The time the reach happened, inside the window identity is willing to believe. */
    private Instant occurredAt(String value) {
        Instant parsed;
        try {
            parsed = Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException notATime) {
            throw malformed("occurredAt is not an instant");
        }
        Instant now = clock.instant();
        if (parsed.isAfter(now.plus(PlatformWideAuditTrail.CLOCK_SKEW))
                || parsed.isBefore(now.minus(PlatformWideAuditTrail.MAX_AGE))) {
            throw malformed("occurredAt is outside the window in which a reach may be delivered");
        }
        return parsed;
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static TranslatedRefusal malformed(String message) {
        return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, message);
    }

    /** Only a reason the platform declared for a person's reach; system reasons have no caller to name. */
    private static PlatformWideAccess.Reason reasonOf(String name) {
        try {
            PlatformWideAccess.Reason reason = PlatformWideAccess.Reason.valueOf(name);
            if (reason.isSystem()) {
                throw malformed("reason is not a declared platform-wide reason");
            }
            return reason;
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw malformed("reason is not a declared platform-wide reason");
        }
    }

    /** {@code occurredAt} is an ISO-8601 instant, kept as text so a malformed one is a refusal on this surface. */
    public record Delivery(String recordId, String occurredAt, String actorSub, String role, String service,
                           String reason, String operation) {
    }
}
