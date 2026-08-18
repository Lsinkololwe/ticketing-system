package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.CheckInConflict;
import com.pml.booking.service.CheckInService;
import com.pml.booking.web.graphql.dto.checkin.ValidateTicketInput;
import com.pml.booking.web.graphql.dto.checkin.ValidationResult;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gate admission mutations.
 *
 * <h2>Security</h2>
 * The scanning actor and their organization come from the JWT, never from the
 * input. A client that could name its own {@code scannedBy} could record an
 * admission under another steward's name, which is exactly the field an
 * after-the-fact dispute turns on.
 *
 * <p>This is the coarse role gate. ET-TKT-003 additionally requires a per-event
 * {@code ticket:scan} grant from ET-ORG-003, which does not exist yet.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/003-validation-and-checkin/spec.md">ET-TKT-003</a>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class CheckInMutationResolver {

    /**
     * The spec caps an upload batch at 500.
     *
     * <p>Rejecting an over-sized batch is friendlier than it sounds: the
     * alternative is a request that times out on a venue connection halfway
     * through, leaving the device unsure which scans were recorded.
     */
    private static final int UPLOAD_BATCH_MAX = 500;

    private final CheckInService checkInService;

    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SCANNER')")
    public Mono<ValidationResult> validateTicket(@InputArgument ValidateTicketInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> checkInService.scan(toCommand(input, actorId)))
                .map(ValidationResult::from)
                .doOnNext(result -> log.info("Gate scan on event {}: {}",
                        input.eventId(), result.outcome()));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SCANNER')")
    public Mono<List<ValidationResult>> uploadScans(@InputArgument List<ValidateTicketInput> inputs) {
        if (inputs != null && inputs.size() > UPLOAD_BATCH_MAX) {
            return Mono.error(new IllegalArgumentException(
                    "Upload batch exceeds " + UPLOAD_BATCH_MAX + " scans; split it."));
        }

        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> checkInService.uploadScans(
                        inputs == null ? List.of() : inputs.stream()
                                .map(input -> toCommand(input, actorId))
                                .toList()))
                .map(results -> results.stream().map(ValidationResult::from).toList());
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<CheckInConflict> reviewConflict(@InputArgument String id,
                                                @InputArgument String note) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(organizerId -> checkInService.reviewConflict(id, organizerId, note, organizerId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conflict not found")));
    }

    /**
     * The actor is the same value for both fields today because an organizer id
     * <em>is</em> a user id in this system. They are named separately because
     * the moment event-level scan grants exist (ET-ORG-003) a steward will scan
     * on an organizer's behalf and the two will diverge.
     */
    private CheckInService.ScanCommand toCommand(ValidateTicketInput input, String actorId) {
        return new CheckInService.ScanCommand(
                input.eventId(),
                input.code(),
                input.method(),
                input.scanId(),
                input.deviceId(),
                input.scannedAt(),
                input.reason(),
                actorId,
                actorId);
    }
}
