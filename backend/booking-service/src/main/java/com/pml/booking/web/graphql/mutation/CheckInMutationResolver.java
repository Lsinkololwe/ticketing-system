package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import java.util.Map;
import reactor.core.publisher.Flux;
import com.pml.booking.security.EventGateAccess;
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
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * Gate admission mutations.
 *
 * <h2>Security</h2>
 * The scanning actor and their organization come from the JWT, never from the
 * input. A client that could name its own {@code scannedBy} could record an
 * admission under another steward's name, which is exactly the field an
 * after-the-fact dispute turns on.
 *
 * <p>Every scan and every conflict review needs {@code ticket:scan} on the event concerned,
 * checked by {@link EventGateAccess} before anything is recorded. That is what lets gate staff be
 * given one event without seeing any other, and what stops anyone admitting tickets at another
 * organization's event.
 */
@Slf4j


@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class CheckInMutationResolver {

    /**
     * An upload batch is capped at 500.
     *
     * <p>Rejecting an over-sized batch is friendlier than it sounds: the
     * alternative is a request that times out on a venue connection halfway
     * through, leaving the device unsure which scans were recorded.
     */
    private static final int UPLOAD_BATCH_MAX = 500;

    private final CheckInService checkInService;
    private final EventGateAccess gates;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<ValidationResult> validateTicket(@Valid @InputArgument ValidateTicketInput input) {
        return gates.requireScan(input.eventId())
                .flatMap(event -> SecurityContextUtils.requireCurrentUserId()
                        .flatMap(actorId -> checkInService.scan(toCommand(input, actorId, event.getOrganizerId()))))
                .map(ValidationResult::from)
                .doOnNext(result -> log.info("Gate scan on event {}: {}",
                        input.eventId(), result.outcome()));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<List<ValidationResult>> uploadScans(@Valid @InputArgument List<@Valid ValidateTicketInput> inputs) {
        if (inputs != null && inputs.size() > UPLOAD_BATCH_MAX) {
            return Mono.error(new IllegalArgumentException(
                    "Upload batch exceeds " + UPLOAD_BATCH_MAX + " scans; split it."));
        }
        List<ValidateTicketInput> batch = inputs == null ? List.of() : inputs;

        // A device may upload scans from more than one event. The caller needs ticket:scan on every
        // one of them; a single refusal refuses the batch, so nothing is recorded for an event the
        // caller may not work.
        return Flux.fromIterable(batch.stream().map(ValidateTicketInput::eventId).distinct().toList())
                .concatMap(eventId -> gates.requireScan(eventId).map(event -> Map.entry(eventId, event.getOrganizerId())))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(organizerByEvent -> SecurityContextUtils.requireCurrentUserId()
                        .flatMap(actorId -> checkInService.uploadScans(batch.stream()
                                .map(input -> toCommand(input, actorId, organizerByEvent.get(input.eventId())))
                                .toList())))
                .map(results -> results.stream().map(ValidationResult::from).toList());
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<CheckInConflict> reviewConflict(@InputArgument String id,
                                                @InputArgument String note) {
        return checkInService.findConflict(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conflict not found")))
                .flatMap(conflict -> gates.requireScan(conflict.getEventId())
                        .then(SecurityContextUtils.requireCurrentUserId())
                        .flatMap(reviewerId -> checkInService.reviewConflict(
                                id, conflict.getOrganizerId(), note, reviewerId)));
    }

    /**
     * {@code scannedBy} is whoever held the scanner; {@code organizerId} is the event's organizer,
     * which is who the admission and any conflict belong to. They differ whenever gate staff scan
     * on an organizer's behalf.
     */
    private CheckInService.ScanCommand toCommand(ValidateTicketInput input, String actorId, String organizerId) {
        return new CheckInService.ScanCommand(
                input.eventId(),
                input.code(),
                input.method(),
                input.scanId(),
                input.deviceId(),
                input.scannedAt(),
                input.reason(),
                actorId,
                organizerId);
    }
}
